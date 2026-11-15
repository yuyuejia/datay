package com.data.datafusion.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.data.datafusion.domain.Job;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.domain.MetadataCollectTask;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.JobInstanceRepository;
import com.data.datafusion.repository.MetadataCollectTaskRepository;
import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.FileStorageConfigService.FileStorageConfig;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.MetadataCollectRunDTO;
import com.data.datafusion.service.dto.MetadataCollectTaskDTO;
import com.data.datafusion.service.mapper.MetadataCollectTaskMapper;
import com.data.datasource.DataSourceProviderFactory;
import com.data.file.FileStorageStrategy;
import com.data.metadata.util.DBUtils;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.quartz.CronExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据源元数据采集任务服务。
 *
 * <p>每个数据源维护一条采集配置。保存时生成 {@code MetadataCollect} ETL Job，
 * 支持按 Cron 定时执行和立即执行；执行结果写入系统文件存储。</p>
 */
@Service
@Transactional
public class MetadataCollectTaskService {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataCollectTaskService.class);

    private static final String DEFAULT_OUTPUT_DIR = "metadata";


    private final MetadataCollectTaskRepository taskRepository;

    private final MetadataCollectTaskMapper taskMapper;

    private final DataSourceService dataSourceService;

    private final JobService jobService;

    private final JobInstanceService jobInstanceService;

    private final JobInstanceRepository jobInstanceRepository;

    private final FileStorageConfigService fileStorageConfigService;

    private final FileStorageStrategy fileStorageStrategy;

    public MetadataCollectTaskService(
        MetadataCollectTaskRepository taskRepository,
        MetadataCollectTaskMapper taskMapper,
        DataSourceService dataSourceService,
        JobService jobService,
        JobInstanceService jobInstanceService,
        JobInstanceRepository jobInstanceRepository,
        FileStorageConfigService fileStorageConfigService,
        FileStorageStrategy fileStorageStrategy
    ) {
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
        this.dataSourceService = dataSourceService;
        this.jobService = jobService;
        this.jobInstanceService = jobInstanceService;
        this.jobInstanceRepository = jobInstanceRepository;
        this.fileStorageConfigService = fileStorageConfigService;
        this.fileStorageStrategy = fileStorageStrategy;
    }

    @Transactional(readOnly = true)
    public Optional<MetadataCollectTaskDTO> findByDataSourceId(String dataSourceId) {
        return taskRepository.findByDataSourceId(dataSourceId).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<MetadataCollectTaskDTO> findOne(String id) {
        return taskRepository.findById(id).map(this::toDto);
    }

    public MetadataCollectTaskDTO save(MetadataCollectTaskDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("元数据采集配置不能为空");
        }
        if (dto.getDataSourceId() == null || dto.getDataSourceId().isBlank()) {
            throw new IllegalArgumentException("请选择数据源");
        }
        DataSourceDTO dataSource = requireDataSource(dto.getDataSourceId());
        normalize(dto, dataSource);

        MetadataCollectTask existing = resolveExisting(dto);
        if (existing == null) {
            existing = taskRepository.findByDataSourceId(dto.getDataSourceId()).orElse(null);
        }
        String previousStatus = existing == null ? null : existing.getStatus();
        MetadataCollectTask task = taskMapper.toEntity(dto);
        if (existing != null) {
            task.setId(existing.getId());
            task.setJobId(existing.getJobId());
            task.setCreateTime(existing.getCreateTime());
            task.setCreater(existing.getCreater());
            task.setLastStatus(existing.getLastStatus());
            task.setLastRunTime(existing.getLastRunTime());
            task.setTenantId(existing.getTenantId());
        }
        if (task.getTenantId() == null || task.getTenantId().isBlank()) {
            task.setTenantId(SecurityUtils.getCurrentTenantIdString().orElse(null));
        }
        task.setSchemaNamesJson(JSONUtil.toJsonStr(dto.getSchemaNames()));
        task.setOutputDir(DEFAULT_OUTPUT_DIR);
        task.setCreateTime(task.getCreateTime() == null ? ZonedDateTime.now() : task.getCreateTime());
        task.setUpdateTime(ZonedDateTime.now());
        if (task.getCreater() == null) {
            task.setCreater(SecurityUtils.getCurrentUserLogin().orElse(null));
        }

        FileStorageConfig storage = fileStorageConfigService.getConfig();
        Job job = saveJob(task, dataSource, storage, previousStatus);
        task.setJobId(job.getId());

        if (Boolean.TRUE.equals(task.getEnabled())) {
            if (TaskConstants.TASK_STATUS_ONLINE.equalsIgnoreCase(previousStatus)) {
                jobService.offline(job);
            }
            jobService.online(job, false);
            task.setStatus(TaskConstants.TASK_STATUS_ONLINE);
        } else {
            if (TaskConstants.TASK_STATUS_ONLINE.equalsIgnoreCase(previousStatus)) {
                jobService.offline(job);
            } else {
                job.setStatus(TaskConstants.TASK_STATUS_OFFLINE);
                jobService.save(job);
            }
            task.setStatus(TaskConstants.TASK_STATUS_OFFLINE);
        }

        task = taskRepository.save(task);
        ensureTaskDirectory(task);
        return toDto(task);
    }

    public MetadataCollectRunDTO execute(String id) {
        MetadataCollectTask task = taskRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("元数据采集任务不存在: " + id));
        if (
            task.getJobId() != null &&
            (!jobInstanceService.findByJobCodeAndStatus(task.getJobId(), TaskConstants.TASK_STATUS_RUNNING).isEmpty() ||
                !jobInstanceService.findByJobCodeAndStatus(task.getJobId(), TaskConstants.TASK_STATUS_APPENDING).isEmpty())
        ) {
            throw new IllegalArgumentException("元数据采集任务正在执行中，请稍后再试");
        }

        DataSourceDTO dataSource = requireDataSource(task.getDataSourceId());
        FileStorageConfig storage = fileStorageConfigService.getConfig();
        Job job = saveJob(task, dataSource, storage, task.getStatus());

        JobInstance instance = jobInstanceService.buildJobInstance(job.getId());
        instance.setCreateTime(ZonedDateTime.now());
        instance = jobInstanceService.save(instance);

        task.setLastRunTime(ZonedDateTime.now());
        task.setUpdateTime(ZonedDateTime.now());
        taskRepository.save(task);

        jobInstanceService.startInstance(instance);
        LOG.info("元数据采集任务已提交: taskId={}, jobInstanceId={}", task.getId(), instance.getId());
        return toRun(instance, task);
    }

    @Transactional(readOnly = true)
    public Page<MetadataCollectRunDTO> findRuns(String id, Pageable pageable) {
        MetadataCollectTask task = taskRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("元数据采集任务不存在: " + id));
        if (task.getJobId() == null || task.getJobId().isBlank()) {
            return Page.empty(pageable);
        }
        return jobInstanceRepository.findByJobCodeOrderByCreateTimeDesc(task.getJobId(), pageable).map(instance -> toRun(instance, task));
    }

    public void delete(String id) {
        MetadataCollectTask task = taskRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("元数据采集任务不存在: " + id));
        deleteTaskAndJob(task);
    }

    public void deleteByDataSourceId(String dataSourceId) {
        if (dataSourceId == null || dataSourceId.isBlank()) {
            return;
        }
        taskRepository.findByDataSourceId(dataSourceId).ifPresent(this::deleteTaskAndJob);
    }

    private void deleteTaskAndJob(MetadataCollectTask task) {
        if (task.getJobId() != null && !task.getJobId().isBlank()) {
            jobService.findOneJob(task.getJobId()).ifPresent(job -> jobService.delete(job.getId()));
        }
        taskRepository.delete(task);
    }

    private MetadataCollectTask resolveExisting(MetadataCollectTaskDTO dto) {
        if (dto.getId() != null && !dto.getId().isBlank()) {
            return taskRepository
                .findById(dto.getId())
                .orElseThrow(() -> new IllegalArgumentException("元数据采集任务不存在: " + dto.getId()));
        }
        return null;
    }

    private Job saveJob(MetadataCollectTask task, DataSourceDTO dataSource, FileStorageConfig storage, String previousStatus) {
        Job job = task.getJobId() == null ? new Job() : jobService.findOneJob(task.getJobId()).orElseGet(Job::new);
        job.setJobName(task.getTaskName());
        job.setType(TaskConstants.TASK_TYPE_METADATA_COLLECT);
        job.setCron(normalizeCron(task.getCron()));
        job.setStatus(
            TaskConstants.TASK_STATUS_ONLINE.equalsIgnoreCase(previousStatus)
                ? TaskConstants.TASK_STATUS_ONLINE
                : TaskConstants.TASK_STATUS_OFFLINE
        );
        job.setJobContext(buildJobJson(task, dataSource, storage, tenantPrefix()).toString());
        job.setUpdateTime(ZonedDateTime.now());
        if (job.getCreateTime() == null) {
            job.setCreateTime(ZonedDateTime.now());
        }
        if (job.getTenantId() == null || job.getTenantId().isBlank()) {
            job.setTenantId(
                task.getTenantId() != null ? task.getTenantId() : SecurityUtils.getCurrentTenantIdString().orElse(null)
            );
        }
        return jobService.save(job);
    }

    /**
     * 生成元数据采集 ETL JobContext。保留 package 可见性以方便单元测试。
     */
    JSONObject buildJobJson(
        MetadataCollectTask task,
        DataSourceDTO dataSource,
        FileStorageConfig storage,
        String tenantPrefix
    ) {
        Map<String, Object> unit = new LinkedHashMap<>();
        unit.put(".id", "metadata_collect");
        unit.put(".name", "MetadataCollect");
        unit.put("sourceId", toSourceMap(dataSource));
        unit.put("schemaNames", new ArrayList<>(task.getSchemaNamesJson() == null ? List.of() : parseSchemaNames(task.getSchemaNamesJson())));
        unit.put("dataSourceId", task.getDataSourceId());
        unit.put("dataSourceName", dataSource.getName());
        unit.put("outputDir", normalizeOutputDir(task.getOutputDir()));
        unit.put("storageType", storage.type == null ? "local" : storage.type);
        unit.put("tenantPrefix", tenantPrefix);
        if ("minio".equalsIgnoreCase(storage.type)) {
            unit.put("minioEndpoint", storage.minioEndpoint);
            unit.put("minioAccessKey", storage.minioAccessKey);
            unit.put("minioSecretKey", storage.minioSecretKey);
            unit.put("minioBucketName", storage.minioBucketName);
            unit.put("minioUrlStyle", "path");
        } else {
            unit.put("localBasePath", storage.localBasePath);
        }

        List<Map<String, Object>> units = new ArrayList<>();
        units.add(unit);

        Map<String, Object> jobJson = new LinkedHashMap<>();
        jobJson.put("units", units);
        jobJson.put("connections", new ArrayList<>());
        jobJson.put("version", "1.0.0");
        return JSONUtil.parseObj(JSONUtil.toJsonStr(jobJson));
    }

    private Map<String, Object> toSourceMap(DataSourceDTO source) {
        Map<String, Object> sourceMap = new LinkedHashMap<>();
        sourceMap.put("type", source.getType());
        sourceMap.put("url", source.getUrl());
        sourceMap.put("driver", DBUtils.getDriverClassName(source.getUrl()));
        sourceMap.put("username", source.getUsername());
        sourceMap.put("password", source.getPassword());
        sourceMap.put("hostname", source.getHostname());
        sourceMap.put("port", source.getPort());
        sourceMap.put("extraParams", source.getExtraParams());
        sourceMap.put("dbschema", source.getSchemaName());
        return sourceMap;
    }

    private void normalize(MetadataCollectTaskDTO dto, DataSourceDTO dataSource) {
        if (dto.getTaskName() == null || dto.getTaskName().isBlank()) {
            dto.setTaskName(dataSource.getName() + " 元数据采集");
        }
        if (dto.getSchemaNames() == null) {
            dto.setSchemaNames(new ArrayList<>());
        } else {
            List<String> normalized = new ArrayList<>();
            for (String schema : dto.getSchemaNames()) {
                if (schema != null && !schema.isBlank() && !normalized.contains(schema.trim())) {
                    normalized.add(schema.trim());
                }
            }
            dto.setSchemaNames(normalized);
        }
        dto.setCron(normalizeCron(dto.getCron()));
        dto.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));
        if (dto.getEnabled()) {
            if (dto.getCron() == null) {
                throw new IllegalArgumentException("启用定期采集时请设置 Cron 表达式");
            }
            if (!CronExpression.isValidExpression(dto.getCron())) {
                throw new IllegalArgumentException("调度周期（Cron 表达式）不合法: " + dto.getCron());
            }
        }
    }

    private MetadataCollectTaskDTO toDto(MetadataCollectTask task) {
        MetadataCollectTaskDTO dto = taskMapper.toDto(task);
        dto.setSchemaNames(parseSchemaNames(task.getSchemaNamesJson()));
        if (task.getDataSourceId() != null) {
            dataSourceService.findOne(task.getDataSourceId()).ifPresent(ds -> dto.setDataSourceName(ds.getName()));
        }
        attachLastRun(task, dto);
        return dto;
    }

    private void attachLastRun(MetadataCollectTask task, MetadataCollectTaskDTO dto) {
        if (task.getJobId() == null || task.getJobId().isBlank()) {
            return;
        }
        jobInstanceRepository
            .findFirstByJobCodeOrderByCreateTimeDesc(task.getJobId())
            .ifPresent(instance -> {
                dto.setLastRun(toRun(instance, task));
                dto.setLastStatus(instance.getStatus());
                ZonedDateTime startTime = parseTime(instance.getStartTime());
                if (startTime == null) {
                    startTime = instance.getCreateTime();
                }
                dto.setLastRunTime(startTime);
            });
    }

    private MetadataCollectRunDTO toRun(JobInstance instance, MetadataCollectTask task) {
        MetadataCollectRunDTO dto = new MetadataCollectRunDTO();
        dto.setId(instance.getId());
        dto.setInstanceCode(instance.getInstanceCode());
        dto.setStatus(instance.getStatus());
        dto.setStartTime(parseTime(instance.getStartTime()));
        dto.setEndTime(parseTime(instance.getEndTime()));
        dto.setCreateTime(instance.getCreateTime());
        dto.setJobMessage(instance.getJobMessage());
        String outputDir = runOutputDir(task, instance.getInstanceCode());
        dto.setOutputDir(outputDir);
        dto.setTablesPath(joinPath(outputDir, "tables.csv"));
        dto.setColumnsPath(joinPath(outputDir, "columns.csv"));
        dto.setTablesDownloadUrl(downloadUrl(dto.getTablesPath()));
        dto.setColumnsDownloadUrl(downloadUrl(dto.getColumnsPath()));
        return dto;
    }

    private String runOutputDir(MetadataCollectTask task, String instanceCode) {
        String code = instanceCode == null || instanceCode.isBlank() ? "manual" : instanceCode;
        return joinPath(
            tenantPrefix(),
            normalizeOutputDir(task.getOutputDir()),
            task.getDataSourceId() == null ? "unknown-source" : task.getDataSourceId(),
            code
        );
    }

    private static String downloadUrl(String path) {
        return "api/files/download?path=" + URLEncoder.encode(path == null ? "" : path, StandardCharsets.UTF_8);
    }

    private DataSourceDTO requireDataSource(String dataSourceId) {
        DataSourceDTO dataSource = dataSourceService
            .findOne(dataSourceId)
            .orElseThrow(() -> new IllegalArgumentException("数据源不存在: " + dataSourceId));
        String type = dataSource.getType();
        if (type != null && !type.isBlank() && DataSourceProviderFactory.provider(type) == null) {
            throw new IllegalArgumentException("数据源类型不支持元数据采集: " + dataSource.getType() + "（" + dataSource.getName() + "）");
        }
        return dataSource;
    }

    private void ensureTaskDirectory(MetadataCollectTask task) {
        if (task == null || task.getDataSourceId() == null) {
            return;
        }
        try {
            fileStorageStrategy.mkdir(joinPath(tenantPrefix(), normalizeOutputDir(task.getOutputDir()), task.getDataSourceId()));
        } catch (Exception e) {
            LOG.warn("创建元数据采集目录失败: {}", e.getMessage());
        }
    }

    private static List<String> parseSchemaNames(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        try {
            JSONArray array = JSONUtil.parseArray(value);
            for (int i = 0; i < array.size(); i++) {
                String schema = array.getStr(i);
                if (schema != null && !schema.isBlank() && !result.contains(schema.trim())) {
                    result.add(schema.trim());
                }
            }
        } catch (Exception e) {
            // 兼容历史配置：按逗号分隔解析
            for (String schema : value.split(",")) {
                if (!schema.isBlank() && !result.contains(schema.trim())) {
                    result.add(schema.trim());
                }
            }
        }
        return result;
    }

    private String tenantPrefix() {
        String code = TenantContext.getTenantCode();
        if (code == null || code.isBlank()) {
            code = SecurityUtils.getCurrentTenantCode().orElse(null);
        }
        if (code == null || code.isBlank()) {
            Long id = TenantContext.getTenantId();
            if (id == null) {
                id = SecurityUtils.getCurrentTenantId().orElse(null);
            }
            if (id != null) {
                code = String.valueOf(id);
            }
        }
        if (code == null || code.isBlank()) {
            code = "default";
        }
        return "tenants/" + code;
    }

    private static String normalizeCron(String cron) {
        if (cron == null || cron.isBlank()) {
            return null;
        }
        return cron.trim();
    }

    private static String normalizeOutputDir(String outputDir) {
        String value = outputDir == null || outputDir.isBlank() ? DEFAULT_OUTPUT_DIR : outputDir.trim();
        value = value.replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.contains("..")) {
            throw new IllegalArgumentException("输出目录不能包含 ..");
        }
        return value.isEmpty() ? DEFAULT_OUTPUT_DIR : value;
    }

    private static ZonedDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(value.trim())).atZone(ZoneId.systemDefault());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String joinPath(String... parts) {
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String normalized = part.replace('\\', '/');
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            if (normalized.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(normalized);
        }
        return result.toString();
    }
}
