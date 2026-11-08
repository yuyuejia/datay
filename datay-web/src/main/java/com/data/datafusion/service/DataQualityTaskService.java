package com.data.datafusion.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.data.datafusion.domain.DataQualityRule;
import com.data.datafusion.domain.DataQualityTask;
import com.data.datafusion.domain.Job;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.job.quality.DataQualityRuleCompiler;
import com.data.job.quality.QualityRuleType;
import com.data.datafusion.repository.DataQualityTaskRepository;
import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.FileStorageConfigService.FileStorageConfig;
import com.data.datafusion.service.dto.DataQualityResultDTO;
import com.data.datafusion.service.dto.DataQualityRuleBindingDTO;
import com.data.datafusion.service.dto.DataQualityTaskDTO;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.mapper.DataQualityTaskMapper;
import com.data.datasource.DataSourceProviderFactory;
import com.data.datasource.provider.JdbcDataSourceProvider;
import com.data.file.FileStorageStrategy;
import com.data.job.DatasourceInfo;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import jakarta.persistence.criteria.Predicate;
import java.sql.Connection;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据质量检核任务服务。
 *
 * <p>任务保存时把规则绑定快照为 JSON；执行时创建 JobInstance，并生成
 * {@code StreamJdbcInput -> DataQualityCheck} 的 ETL 任务。</p>
 */
@Service
@Transactional
public class DataQualityTaskService {

    private static final Logger LOG = LoggerFactory.getLogger(DataQualityTaskService.class);

    private static final String DEFAULT_OUTPUT_DIR = "data-quality";

    private static final Pattern IDENTIFIER = Pattern.compile("^[\\p{L}_][\\p{L}\\p{N}_$]*$");

    private final DataQualityTaskRepository taskRepository;

    private final DataQualityTaskMapper taskMapper;

    private final DataQualityRuleService ruleService;

    private final DataQualityResultService resultService;

    private final DataSourceService dataSourceService;

    private final JobService jobService;

    private final JobInstanceService jobInstanceService;

    private final FileStorageConfigService fileStorageConfigService;

    private final FileStorageStrategy fileStorageStrategy;

    private final DataQualityRuleCompiler ruleCompiler = new DataQualityRuleCompiler();

    public DataQualityTaskService(
        DataQualityTaskRepository taskRepository,
        DataQualityTaskMapper taskMapper,
        DataQualityRuleService ruleService,
        DataQualityResultService resultService,
        DataSourceService dataSourceService,
        JobService jobService,
        JobInstanceService jobInstanceService,
        FileStorageConfigService fileStorageConfigService,
        FileStorageStrategy fileStorageStrategy
    ) {
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
        this.ruleService = ruleService;
        this.resultService = resultService;
        this.dataSourceService = dataSourceService;
        this.jobService = jobService;
        this.jobInstanceService = jobInstanceService;
        this.fileStorageConfigService = fileStorageConfigService;
        this.fileStorageStrategy = fileStorageStrategy;
    }

    public DataQualityTaskDTO save(DataQualityTaskDTO dto) {
        normalize(dto);
        validateTaskConfig(dto);
        DataQualityTask task = taskMapper.toEntity(dto);
        task.setRuleConfigs(JSONUtil.toJsonStr(dto.getRules()));
        Job job = saveJob(task);
        task.setJobId(job.getId());
        task.setStatus(TaskConstants.TASK_STATUS_OFFLINE);
        task.setCreateTime(ZonedDateTime.now());
        task.setUpdateTime(ZonedDateTime.now());
        if (task.getCreater() == null) {
            task.setCreater(SecurityUtils.getCurrentUserLogin().orElse(null));
        }
        if (task.getTenantId() == null || task.getTenantId().isBlank()) {
            task.setTenantId(SecurityUtils.getCurrentTenantIdString().orElse(null));
        }
        task = taskRepository.save(task);
        ensureTaskDirectory(task);
        return toDto(task);
    }

    public DataQualityTaskDTO update(DataQualityTaskDTO dto) {
        normalize(dto);
        validateTaskConfig(dto);
        DataQualityTask existing = taskRepository
            .findById(dto.getId())
            .orElseThrow(() -> new IllegalArgumentException("数据质量任务不存在: " + dto.getId()));
        DataQualityTask task = taskMapper.toEntity(dto);
        task.setRuleConfigs(JSONUtil.toJsonStr(dto.getRules()));
        task.setJobId(existing.getJobId());
        task.setStatus(existing.getStatus() == null ? TaskConstants.TASK_STATUS_OFFLINE : existing.getStatus());
        task.setLastStatus(existing.getLastStatus());
        task.setLastRunTime(existing.getLastRunTime());
        task.setCreateTime(existing.getCreateTime());
        task.setUpdateTime(ZonedDateTime.now());
        task.setCreater(existing.getCreater());
        if (task.getTenantId() == null || task.getTenantId().isBlank()) {
            task.setTenantId(existing.getTenantId());
        }
        Job job = saveJob(task);
        task.setJobId(job.getId());
        task = taskRepository.save(task);
        ensureTaskDirectory(task);
        return toDto(task);
    }

    @Transactional(readOnly = true)
    public Page<DataQualityTaskDTO> findAll(Pageable pageable, String search) {
        return taskRepository.findAll(buildSearchSpecification(search), pageable).map(task -> {
            DataQualityTaskDTO dto = toDto(task);
            attachLastResult(task, dto);
            return dto;
        });
    }

    @Transactional(readOnly = true)
    public Optional<DataQualityTaskDTO> findOne(String id) {
        return taskRepository
            .findById(id)
            .map(task -> {
                DataQualityTaskDTO dto = toDto(task);
                attachLastResult(task, dto);
                return dto;
            });
    }

    public void delete(String id) {
        DataQualityTask task = taskRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("数据质量任务不存在: " + id));
        if (task.getJobId() != null) {
            jobService.findOneJob(task.getJobId()).ifPresent(job -> jobService.delete(job.getId()));
        }
        taskRepository.delete(task);
    }

    /**
     * 立即执行质量任务，返回本次执行实例结果。
     */
    public DataQualityResultDTO execute(String taskId) {
        DataQualityTask task = taskRepository
            .findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("数据质量任务不存在: " + taskId));

        if (task.getJobId() != null
            && (!jobInstanceService.findByJobCodeAndStatus(task.getJobId(), TaskConstants.TASK_STATUS_RUNNING).isEmpty()
                || !jobInstanceService.findByJobCodeAndStatus(task.getJobId(), TaskConstants.TASK_STATUS_APPENDING).isEmpty())) {
            throw new IllegalArgumentException("数据质量任务正在执行中，请稍后再试");
        }

        DataSourceDTO dataSource = requireJdbcDataSource(task.getDataSourceId());
        String schema = effectiveSchema(task.getSchemaName(), dataSource);
        validateIdentifier(task.getTableName(), "表名");
        List<DataQualityRuleBindingDTO> rules = resolveRules(task);
        validateRules(rules, dataSource, schema, task.getTableName());

        Job job = task.getJobId() == null ? null : jobService.findOneJob(task.getJobId()).orElse(null);
        if (job == null) {
            job = saveJob(task);
            task.setJobId(job.getId());
        }

        JobInstance instance = jobInstanceService.buildJobInstance(job.getId());
        instance.setCreateTime(ZonedDateTime.now());
        instance = jobInstanceService.save(instance);
        String instanceCode = instance.getInstanceCode();

        String outputDir = joinPath(normalizeOutputDir(task.getOutputDir()), task.getId());
        String resultFormat = normalizeFormat(task.getOutputFormat());
        String summaryPath = joinPath(outputDir, instanceCode, "quality_summary.csv");
        FileStorageConfig storage = fileStorageConfigService.getConfig();
        String tenantPrefix = tenantPrefix();
        int sampleLimit = task.getSampleLimit() == null ? 100 : Math.max(0, task.getSampleLimit());

        JSONObject jobJson = buildJobJson(
            dataSource,
            schema,
            task.getTableName(),
            task.getWhereClause(),
            rules,
            outputDir,
            resultFormat,
            sampleLimit,
            Boolean.TRUE.equals(task.getFailOnWarn()),
            storage,
            tenantPrefix
        );
        attachResultMetadata(jobJson, task, schema, outputDir, resultFormat, summaryPath, instanceCode, rules, sampleLimit);

        job.setType(TaskConstants.TASK_TYPE_DATA_QUALITY);
        job.setJobContext(jobJson.toString());
        job.setUpdateTime(ZonedDateTime.now());
        job = jobService.save(job);

        instance.setJobContext(jobJson.toString());
        instance = jobInstanceService.save(instance);

        task.setJobId(job.getId());
        task.setLastRunTime(ZonedDateTime.now());
        task.setUpdateTime(ZonedDateTime.now());
        taskRepository.save(task);

        jobInstanceService.startInstance(instance);

        String instanceId = instance.getId();
        LOG.info("数据质量任务已提交: taskId={}, jobInstanceId={}, rules={}", task.getId(), instanceId, rules.size());
        return resultService
            .findByJobInstanceId(instanceId)
            .orElseThrow(() -> new IllegalStateException("数据质量任务实例未创建: " + instanceId));
    }

    /**
     * 生成质量任务 ETL JobContext。保留 package 可见性以方便单元测试。
     */
    JSONObject buildJobJson(
        DataSourceDTO dataSource,
        String schema,
        String table,
        String where,
        List<DataQualityRuleBindingDTO> rules,
        String outputDir,
        String resultFormat,
        int sampleLimit,
        boolean failOnWarn,
        FileStorageConfig storage,
        String tenantPrefix
    ) {
        Map<String, Object> jobJson = new LinkedHashMap<>();
        List<Map<String, Object>> units = new ArrayList<>();
        List<Map<String, Object>> connections = new ArrayList<>();

        Map<String, Object> input = new LinkedHashMap<>();
        input.put(".id", "dq_input");
        input.put(".name", "StreamJdbcInput");
        input.put("sourceId", toSourceMap(dataSource, schema));
        input.put("schema", schema);
        input.put("table", table);
        input.put("where", normalizeWhere(where));
        input.put("incrColumn", "");
        units.add(input);

        Map<String, Object> check = new LinkedHashMap<>();
        check.put(".id", "dq_check");
        check.put(".name", "DataQualityCheck");
        // 质量检核任务统一使用全量模式：所有数据先落 DuckDB，再执行全量检核。
        check.put("checkMode", "FULL");
        check.put("rules", JSONUtil.parseArray(JSONUtil.toJsonStr(rules)));
        check.put("failOnError", true);
        check.put("failOnWarn", failOnWarn);
        check.put("writeResult", true);
        check.put("sampleLimit", sampleLimit);
        check.put("outputFormat", resultFormat);
        check.put("outputDir", outputDir);
        check.put("resultPrefix", "quality");
        check.put("tenantPrefix", tenantPrefix);
        check.put("storageType", storage.type);
        if ("minio".equalsIgnoreCase(storage.type)) {
            check.put("minioEndpoint", storage.minioEndpoint);
            check.put("minioAccessKey", storage.minioAccessKey);
            check.put("minioSecretKey", storage.minioSecretKey);
            check.put("minioBucketName", storage.minioBucketName);
            check.put("minioUrlStyle", "path");
        } else {
            check.put("localBasePath", storage.localBasePath);
        }
        units.add(check);

        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("sourceId", "dq_input");
        connection.put("targetId", "dq_check");
        connection.put("sourcePort", 0);
        connections.add(connection);

        jobJson.put("units", units);
        jobJson.put("connections", connections);
        jobJson.put("version", "1.0.0");
        return JSONUtil.parseObj(JSONUtil.toJsonStr(jobJson));
    }

    private void attachResultMetadata(
        JSONObject jobJson,
        DataQualityTask task,
        String schema,
        String outputDir,
        String resultFormat,
        String summaryPath,
        String instanceCode,
        List<DataQualityRuleBindingDTO> rules,
        int sampleLimit
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("taskId", task.getId());
        metadata.put("taskName", task.getTaskName());
        metadata.put("dataSourceId", task.getDataSourceId());
        metadata.put("schemaName", schema);
        metadata.put("tableName", task.getTableName());
        metadata.put("outputDir", outputDir);
        metadata.put("resultFormat", resultFormat);
        metadata.put("summaryPath", summaryPath);
        metadata.put("resultPath", summaryPath);
        metadata.put("detailDir", joinPath(outputDir, instanceCode));
        metadata.put("sampleLimit", sampleLimit);
        metadata.put("failOnWarn", Boolean.TRUE.equals(task.getFailOnWarn()));
        metadata.put("rules", JSONUtil.parseArray(JSONUtil.toJsonStr(rules)));
        jobJson.put("dataQuality", metadata);
    }

    private List<DataQualityRuleBindingDTO> resolveRules(DataQualityTask task) {
        JSONArray array = JSONUtil.parseArray(task.getRuleConfigs() == null ? "[]" : task.getRuleConfigs());
        List<DataQualityRuleBindingDTO> bindings = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JSONObject node = array.getJSONObject(i);
            if (node == null) {
                continue;
            }
            DataQualityRuleBindingDTO binding = node.toBean(DataQualityRuleBindingDTO.class);
            applyRuleDefaults(binding);
            normalizeBinding(binding);
            bindings.add(binding);
        }
        return bindings;
    }

    private void validateTaskConfig(DataQualityTaskDTO dto) {
        if (dto.getTaskName() == null || dto.getTaskName().isBlank()) {
            throw new IllegalArgumentException("任务名称不能为空");
        }
        if (dto.getDataSourceId() == null || dto.getDataSourceId().isBlank()) {
            throw new IllegalArgumentException("请选择数据源");
        }
        if (dto.getTableName() == null || dto.getTableName().isBlank()) {
            throw new IllegalArgumentException("请选择数据表");
        }
        validateIdentifier(dto.getTableName(), "表名");
        if (dto.getRules() == null || dto.getRules().isEmpty()) {
            throw new IllegalArgumentException("请至少配置一条质量规则");
        }
        List<DataQualityRuleBindingDTO> bindings = new ArrayList<>();
        for (DataQualityRuleBindingDTO binding : dto.getRules()) {
            DataQualityRuleBindingDTO normalized = cloneBinding(binding);
            applyRuleDefaults(normalized);
            normalizeBinding(normalized);
            bindings.add(normalized);
        }
        dto.setRules(bindings);
        try {
            ruleCompiler.compile(JSONUtil.toJsonStr(bindings), "main.dq_source");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("质量规则配置错误: " + e.getMessage(), e);
        }
    }

    private void validateRules(List<DataQualityRuleBindingDTO> rules, DataSourceDTO dataSource, String schema, String table) {
        Set<String> columns = new HashSet<>(readColumns(dataSource, schema, table));
        for (DataQualityRuleBindingDTO rule : rules) {
            if (!"FIELD".equalsIgnoreCase(rule.getRuleLevel())) {
                continue;
            }
            List<String> ruleColumns = new ArrayList<>();
            if (rule.getColumns() != null && !rule.getColumns().isEmpty()) {
                ruleColumns.addAll(rule.getColumns());
            } else if (rule.getColumn() != null && !rule.getColumn().isBlank()) {
                ruleColumns.add(rule.getColumn());
            }
            for (String column : ruleColumns) {
                if (!columns.contains(column)) {
                    throw new IllegalArgumentException("规则[" + rule.getRuleName() + "]字段不存在: " + column);
                }
            }
        }
    }

    private void applyRuleDefaults(DataQualityRuleBindingDTO binding) {
        if (binding.getRuleId() == null || binding.getRuleId().isBlank()) {
            return;
        }
        Optional<DataQualityRule> ruleOptional = ruleService.findEntity(binding.getRuleId());
        if (ruleOptional.isEmpty()) {
            // 规则库中的规则被删除后，继续使用任务中保存的快照配置。
            return;
        }
        DataQualityRule rule = ruleOptional.get();
        binding.setRuleCode(rule.getRuleCode());
        if (binding.getRuleName() == null || binding.getRuleName().isBlank()) {
            binding.setRuleName(rule.getRuleName());
        }
        binding.setRuleLevel(rule.getRuleLevel());
        binding.setRuleType(rule.getRuleType());
        if (binding.getSeverity() == null || binding.getSeverity().isBlank()) {
            binding.setSeverity(rule.getSeverity());
        }
        if (rule.getDefaultConfig() != null && !rule.getDefaultConfig().isBlank()) {
            JSONObject defaults = JSONUtil.parseObj(rule.getDefaultConfig());
            Map<String, Object> merged = new LinkedHashMap<>(defaults);
            merged.putAll(binding.getConfig());
            binding.setConfig(merged);
        }
    }

    private void normalizeBinding(DataQualityRuleBindingDTO binding) {
        QualityRuleType type = QualityRuleType.of(binding.getRuleType());
        binding.setRuleType(type.getCode());
        binding.setRuleLevel(type.getLevel().name());
        if (binding.getRuleName() == null || binding.getRuleName().isBlank()) {
            binding.setRuleName(type.getLabel());
        }
        String severity = binding.getSeverity() == null || binding.getSeverity().isBlank()
            ? "ERROR"
            : binding.getSeverity().trim().toUpperCase(Locale.ROOT);
        if (!"ERROR".equals(severity) && !"WARN".equals(severity)) {
            throw new IllegalArgumentException("规则严重级别仅支持 ERROR 或 WARN");
        }
        binding.setSeverity(severity);
        if (binding.getMaxInvalidRows() == null) {
            binding.setMaxInvalidRows(0L);
        }
        if (binding.getMaxInvalidRatio() == null) {
            binding.setMaxInvalidRatio(0.0D);
        }
        if (type.getLevel() == QualityRuleType.QualityRuleLevel.FIELD) {
            boolean hasColumn = (binding.getColumn() != null && !binding.getColumn().isBlank()) ||
                (binding.getColumns() != null && !binding.getColumns().isEmpty());
            if (!hasColumn) {
                throw new IllegalArgumentException("字段级规则[" + binding.getRuleName() + "]必须选择字段");
            }
        }
    }

    private DataQualityRuleBindingDTO cloneBinding(DataQualityRuleBindingDTO source) {
        DataQualityRuleBindingDTO target = new DataQualityRuleBindingDTO();
        target.setRuleId(source.getRuleId());
        target.setRuleCode(source.getRuleCode());
        target.setRuleName(source.getRuleName());
        target.setRuleLevel(source.getRuleLevel());
        target.setRuleType(source.getRuleType());
        target.setColumn(source.getColumn());
        target.setColumns(source.getColumns());
        target.setConfig(source.getConfig());
        target.setSeverity(source.getSeverity());
        target.setMaxInvalidRows(source.getMaxInvalidRows());
        target.setMaxInvalidRatio(source.getMaxInvalidRatio());
        return target;
    }

    private void normalize(DataQualityTaskDTO dto) {
        if (dto.getOutputDir() == null || dto.getOutputDir().isBlank()) {
            dto.setOutputDir(DEFAULT_OUTPUT_DIR);
        }
        if (dto.getOutputFormat() == null || dto.getOutputFormat().isBlank()) {
            dto.setOutputFormat("CSV");
        }
        if (dto.getFailOnWarn() == null) {
            dto.setFailOnWarn(Boolean.FALSE);
        }
        if (dto.getSampleLimit() == null || dto.getSampleLimit() < 0) {
            dto.setSampleLimit(100);
        }
    }

    private DataQualityTaskDTO toDto(DataQualityTask task) {
        DataQualityTaskDTO dto = taskMapper.toDto(task);
        if (task.getRuleConfigs() != null && !task.getRuleConfigs().isBlank()) {
            JSONArray array = JSONUtil.parseArray(task.getRuleConfigs());
            List<DataQualityRuleBindingDTO> rules = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                JSONObject node = array.getJSONObject(i);
                if (node != null) {
                    rules.add(node.toBean(DataQualityRuleBindingDTO.class));
                }
            }
            dto.setRules(rules);
        }
        return dto;
    }

    private void attachLastResult(DataQualityTask task, DataQualityTaskDTO dto) {
        resultService
            .findLatestByTaskId(task.getId())
            .ifPresent(lastResult -> {
                dto.setLastResult(lastResult);
                dto.setLastStatus(lastResult.getStatus());
                if (lastResult.getStartTime() != null) {
                    dto.setLastRunTime(lastResult.getStartTime());
                }
            });
    }

    private Job saveJob(DataQualityTask task) {
        Job job = task.getJobId() == null ? new Job() : jobService.findOneJob(task.getJobId()).orElseGet(Job::new);
        job.setJobName(task.getTaskName());
        job.setType(TaskConstants.TASK_TYPE_DATA_QUALITY);
        job.setStatus(task.getStatus() == null ? TaskConstants.TASK_STATUS_OFFLINE : task.getStatus());
        job.setUpdateTime(ZonedDateTime.now());
        if (job.getCreateTime() == null) {
            job.setCreateTime(ZonedDateTime.now());
        }
        if (job.getJobContext() == null) {
            job.setJobContext("");
        }
        if (job.getTenantId() == null || job.getTenantId().isBlank()) {
            job.setTenantId(task.getTenantId() != null ? task.getTenantId() : SecurityUtils.getCurrentTenantIdString().orElse(null));
        }
        return jobService.save(job);
    }

    private void ensureTaskDirectory(DataQualityTask task) {
        if (task == null || task.getId() == null) {
            return;
        }
        fileStorageStrategy.mkdir(joinPath(normalizeOutputDir(task.getOutputDir()), task.getId()));
    }

    private List<String> readColumns(DataSourceDTO dataSource, String schema, String table) {
        validateIdentifier(table, "表名");
        DatasourceInfo info = DataSourceQueryService.toDatasourceInfo(dataSource);
        info.setDbschema(schema);
        try (Connection connection = DBUtils.getConnection(info)) {
            TableMeta tableMeta = DBUtils.getTableMetaData(connection, schema, table);
            List<String> columns = new ArrayList<>();
            for (ColumnMeta column : tableMeta.columns()) {
                columns.add(column.getName());
            }
            return columns;
        } catch (Exception e) {
            throw new IllegalArgumentException("读取表字段失败: " + schema + "." + table + " - " + e.getMessage(), e);
        }
    }

    private DataSourceDTO requireJdbcDataSource(String dataSourceId) {
        DataSourceDTO dataSource = dataSourceService
            .findOne(dataSourceId)
            .orElseThrow(() -> new IllegalArgumentException("数据源不存在: " + dataSourceId));
        try {
            if (!(DataSourceProviderFactory.provider(dataSource.getType()) instanceof JdbcDataSourceProvider)) {
                throw new IllegalArgumentException("数据质量任务仅支持 JDBC 关系型数据源: " + dataSource.getName());
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("无法解析数据源类型 {}，按 JDBC 处理", dataSource.getType());
        }
        return dataSource;
    }

    private String effectiveSchema(String configuredSchema, DataSourceDTO dataSource) {
        String schema = configuredSchema;
        if (schema == null || schema.isBlank()) {
            schema = dataSource.getSchemaName();
        }
        if (schema == null || schema.isBlank()) {
            throw new IllegalArgumentException("请选择数据源 schema: " + dataSource.getName());
        }
        return schema.trim();
    }

    private Map<String, Object> toSourceMap(DataSourceDTO source, String schema) {
        Map<String, Object> sourceMap = new LinkedHashMap<>();
        sourceMap.put("type", source.getType());
        sourceMap.put("url", source.getUrl());
        sourceMap.put("driver", DBUtils.getDriverClassName(source.getUrl()));
        sourceMap.put("username", source.getUsername());
        sourceMap.put("password", source.getPassword());
        sourceMap.put("hostname", source.getHostname());
        sourceMap.put("port", source.getPort());
        sourceMap.put("extraParams", source.getExtraParams());
        sourceMap.put("dbschema", schema);
        return sourceMap;
    }

    private void validateIdentifier(String value, String label) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(label + "包含非法字符: " + value);
        }
    }

    private String normalizeWhere(String where) {
        return where == null || where.trim().isEmpty() ? "" : where.trim();
    }

    private String normalizeOutputDir(String outputDir) {
        String value = outputDir == null || outputDir.isBlank() ? DEFAULT_OUTPUT_DIR : outputDir.trim();
        value = value.replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalizeFormat(String format) {
        String value = format == null || format.isBlank() ? "CSV" : format.trim().toUpperCase(Locale.ROOT);
        return "PARQUET".equals(value) ? "PARQUET" : "CSV";
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

    private Specification<DataQualityTask> buildSearchSpecification(String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(
                    cb.or(
                        cb.like(cb.lower(root.get("taskName")), pattern),
                        cb.like(cb.lower(root.get("taskDesc")), pattern),
                        cb.like(cb.lower(root.get("tableName")), pattern)
                    )
                );
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
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
