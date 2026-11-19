package com.data.datafusion.service;

import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.domain.Job;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.dto.AnalysisDatasetDTO;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.ETLEdgeDTO;
import com.data.datafusion.service.dto.ETLNodeDTO;
import com.data.datafusion.service.dto.ETLTaskDTO;
import com.data.datafusion.service.dto.MetricQueryDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.quartz.CronExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 数据集结果持久化服务。
 *
 * <p>把数据集定义生成一个真实的 {@code dp_etl_task}（节点 / 连线）以及 {@code type=DATASET}
 * 的调度 Job。任务执行时由 {@code DatasetTaskContextAssembler} 按最新数据集定义重新生成
 * jobContext，因此指标 SQL 不会因为保存时的快照而变旧。
 */
@Service
@Transactional
public class DatasetPersistenceService {

    private static final Logger LOG = LoggerFactory.getLogger(DatasetPersistenceService.class);

    private static final String IDENTIFIER_PATTERN = "^[A-Za-z_][A-Za-z0-9_]*$";

    private final AnalysisDatasetRepository datasetRepository;

    private final ETLTaskRepository etlTaskRepository;

    private final JobRepository jobRepository;

    private final ETLTaskService etlTaskService;

    private final JobService jobService;

    private final DataSourceService dataSourceService;

    private final MetricQueryService metricQueryService;

    private final ObjectMapper objectMapper;

    public DatasetPersistenceService(
        AnalysisDatasetRepository datasetRepository,
        ETLTaskRepository etlTaskRepository,
        JobRepository jobRepository,
        ETLTaskService etlTaskService,
        JobService jobService,
        DataSourceService dataSourceService,
        MetricQueryService metricQueryService,
        ObjectMapper objectMapper
    ) {
        this.datasetRepository = datasetRepository;
        this.etlTaskRepository = etlTaskRepository;
        this.jobRepository = jobRepository;
        this.etlTaskService = etlTaskService;
        this.jobService = jobService;
        this.dataSourceService = dataSourceService;
        this.metricQueryService = metricQueryService;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验持久化配置。关闭持久化时不做任何要求。
     */
    public void validateConfig(AnalysisDatasetDTO dto) {
        if (dto == null || !Boolean.TRUE.equals(dto.getPersistEnabled())) {
            return;
        }
        if (AnalysisDataset.TYPE_SQL.equalsIgnoreCase(dto.getType())) {
            String definition = dto.getDefinition();
            if (definition != null && definition.contains("${")) {
                throw new IllegalArgumentException("SQL 数据集包含 ${param} 占位符，暂不支持结果持久化");
            }
        }
        if (!StringUtils.hasText(dto.getPersistTargetDataSourceId())) {
            throw new IllegalArgumentException("启用持久化时必须选择目标数据源");
        }
        DataSourceDTO target = dataSourceService
            .findOne(dto.getPersistTargetDataSourceId())
            .orElseThrow(() -> new IllegalArgumentException("持久化目标数据源不存在：" + dto.getPersistTargetDataSourceId()));
        if (target.getType() != null && "ELASTICSEARCH".equalsIgnoreCase(target.getType())) {
            throw new IllegalArgumentException("Elasticsearch 数据源暂不支持作为持久化目标");
        }
        validateIdentifier(dto.getPersistTargetTable(), "目标表");
        if (StringUtils.hasText(dto.getPersistTargetSchema())) {
            validateIdentifier(dto.getPersistTargetSchema().trim(), "目标 Schema");
        }
        String mode = normalizeWriteMode(dto.getPersistWriteMode());
        dto.setPersistWriteMode(mode);
        if (StringUtils.hasText(dto.getPersistCron()) && !CronExpression.isValidExpression(dto.getPersistCron().trim())) {
            throw new IllegalArgumentException("持久化 Cron 表达式不合法：" + dto.getPersistCron());
        }
    }

    private static void validateIdentifier(String value, String label) {
        if (!StringUtils.hasText(value) || !value.trim().matches(IDENTIFIER_PATTERN)) {
            throw new IllegalArgumentException(label + "只能包含字母、数字和下划线，且不能以数字开头");
        }
    }

    private static String normalizeWriteMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return AnalysisDataset.PERSIST_MODE_REPLACE;
        }
        String normalized = mode.trim().toUpperCase(Locale.ROOT);
        if (!AnalysisDataset.PERSIST_MODE_REPLACE.equals(normalized) && !AnalysisDataset.PERSIST_MODE_APPEND.equals(normalized)) {
            throw new IllegalArgumentException("写入模式只支持 REPLACE 或 APPEND");
        }
        return normalized;
    }

    /**
     * 数据集保存 / 更新后同步持久化任务定义。
     *
     * <p>关闭持久化时清理已生成的任务和调度；开启时生成或更新 ETL 任务与 Job。
     * 定义或目标配置变化后，已产出的结果会被标记为 STALE，查询自动回退实时结果。
     */
    public AnalysisDataset sync(AnalysisDataset entity) {
        if (entity == null) {
            return null;
        }
        if (!Boolean.TRUE.equals(entity.getPersistEnabled())) {
            cleanup(entity);
            entity.setPersistStatus(AnalysisDataset.PERSIST_STATUS_DISABLED);
            entity.setPersistLastError(null);
            entity.setPersistRowCount(null);
            return datasetRepository.save(entity);
        }
        normalizeEntityConfig(entity);
        String currentHash = definitionHash(entity);
        boolean definitionChanged = entity.getPersistLastSuccessTime() != null && !currentHash.equals(entity.getPersistDefinitionHash());

        Job job = saveGeneratedJob(entity);
        ETLTaskDTO task = etlTaskService.saveWithoutJob(buildTask(entity, job.getId()));
        entity.setPersistJobId(job.getId());
        entity.setPersistEtlTaskId(task.getId());

        if (entity.getPersistLastSuccessTime() == null) {
            entity.setPersistStatus(AnalysisDataset.PERSIST_STATUS_PENDING);
        } else if (definitionChanged) {
            entity.setPersistStatus(AnalysisDataset.PERSIST_STATUS_STALE);
        } else if (entity.getPersistStatus() == null || AnalysisDataset.PERSIST_STATUS_DISABLED.equals(entity.getPersistStatus())) {
            entity.setPersistStatus(AnalysisDataset.PERSIST_STATUS_PENDING);
        }

        if (StringUtils.hasText(entity.getPersistCron())) {
            job.setStatus(TaskConstants.TASK_STATUS_ONLINE);
            jobService.online(job, false);
        } else {
            job.setStatus(TaskConstants.TASK_STATUS_OFFLINE);
            jobService.offline(job);
        }
        return datasetRepository.save(entity);
    }

    private void normalizeEntityConfig(AnalysisDataset entity) {
        validateIdentifier(entity.getPersistTargetTable(), "目标表");
        if (StringUtils.hasText(entity.getPersistTargetSchema())) {
            entity.setPersistTargetSchema(entity.getPersistTargetSchema().trim());
            validateIdentifier(entity.getPersistTargetSchema(), "目标 Schema");
        } else {
            entity.setPersistTargetSchema(null);
        }
        entity.setPersistWriteMode(normalizeWriteMode(entity.getPersistWriteMode()));
        entity.setPersistCron(StringUtils.hasText(entity.getPersistCron()) ? entity.getPersistCron().trim() : null);
        if (entity.getPersistStatus() == null || entity.getPersistStatus().isBlank()) {
            entity.setPersistStatus(AnalysisDataset.PERSIST_STATUS_PENDING);
        }
    }

    /**
     * 清理数据集生成的 ETL 任务和 Job。
     */
    public void cleanup(AnalysisDataset entity) {
        if (entity == null) {
            return;
        }
        String taskId = entity.getPersistEtlTaskId();
        String jobId = entity.getPersistJobId();
        if (StringUtils.hasText(taskId) && etlTaskRepository.existsById(taskId)) {
            try {
                etlTaskService.delete(taskId);
            } catch (Exception e) {
                LOG.warn("清理数据集持久化任务失败 {}: {}", taskId, e.getMessage());
            }
        } else if (StringUtils.hasText(jobId) && jobRepository.existsById(jobId)) {
            try {
                jobService.delete(jobId);
            } catch (Exception e) {
                LOG.warn("清理数据集持久化 Job 失败 {}: {}", jobId, e.getMessage());
            }
        }
        entity.setPersistEtlTaskId(null);
        entity.setPersistJobId(null);
        entity.setPersistDefinitionHash(null);
        entity.setPersistLastSuccessTime(null);
        entity.setPersistStatus(AnalysisDataset.PERSIST_STATUS_DISABLED);
    }

    /**
     * 手动触发一次持久化任务。
     */
    public Map<String, Object> run(String datasetId) {
        AnalysisDataset entity = loadDataset(datasetId);
        if (!Boolean.TRUE.equals(entity.getPersistEnabled())) {
            throw new IllegalArgumentException("数据集未启用结果持久化");
        }
        if (!StringUtils.hasText(entity.getPersistJobId()) || !jobRepository.existsById(entity.getPersistJobId())) {
            entity = sync(entity);
        }
        Job job = jobRepository
            .findById(entity.getPersistJobId())
            .orElseThrow(() -> new IllegalArgumentException("持久化任务不存在，请先同步任务定义"));
        entity.setPersistStatus(AnalysisDataset.PERSIST_STATUS_RUNNING);
        entity.setPersistLastRunTime(ZonedDateTime.now());
        entity.setPersistLastError(null);
        datasetRepository.save(entity);
        jobService.executeOnce(job);
        return status(datasetId);
    }

    /**
     * 重新生成并同步 ETL 任务定义，但不执行。
     */
    public AnalysisDataset syncTask(String datasetId) {
        return sync(loadDataset(datasetId));
    }

    /**
     * 判断当前数据集是否可以使用持久化结果。
     */
    @Transactional(readOnly = true)
    public boolean isPersistedUsable(String datasetId) {
        if (!StringUtils.hasText(datasetId)) {
            return false;
        }
        Optional<AnalysisDataset> optional = datasetRepository.findById(datasetId);
        if (optional.isEmpty()) {
            return false;
        }
        AnalysisDataset entity = optional.get();
        return (
            Boolean.TRUE.equals(entity.getPersistEnabled()) &&
            AnalysisDataset.PERSIST_STATUS_READY.equalsIgnoreCase(entity.getPersistStatus()) &&
            entity.getPersistLastSuccessTime() != null &&
            StringUtils.hasText(entity.getPersistTargetDataSourceId()) &&
            StringUtils.hasText(entity.getPersistTargetTable()) &&
            definitionHash(entity).equals(entity.getPersistDefinitionHash())
        );
    }

    /**
     * 任务状态更新入口，由 Job 状态事件处理器调用。
     */
    public void updateFromJobInstance(JobInstance instance) {
        if (instance == null || !TaskConstants.TASK_TYPE_DATASET.equalsIgnoreCase(instance.getType())) {
            return;
        }
        String jobId = instance.getJobCode();
        if (!StringUtils.hasText(jobId)) {
            return;
        }
        boolean tenantSet = false;
        try {
            if (StringUtils.hasText(instance.getTenantId())) {
                TenantContext.setTenantId(Long.valueOf(instance.getTenantId()));
                tenantSet = true;
            }
            Optional<AnalysisDataset> optional = datasetRepository.findFirstByPersistJobId(jobId);
            if (optional.isEmpty()) {
                return;
            }
            AnalysisDataset entity = optional.get();
            String status = instance.getStatus();
            if (
                TaskConstants.TASK_STATUS_RUNNING.equalsIgnoreCase(status) ||
                TaskConstants.TASK_STATUS_WAITING.equalsIgnoreCase(status) ||
                TaskConstants.TASK_STATUS_APPENDING.equalsIgnoreCase(status)
            ) {
                entity.setPersistStatus(AnalysisDataset.PERSIST_STATUS_RUNNING);
                if (entity.getPersistLastRunTime() == null) {
                    entity.setPersistLastRunTime(ZonedDateTime.now());
                }
            } else if (TaskConstants.TASK_STATUS_SUCCESSFUL.equalsIgnoreCase(status)) {
                String producedHash = readContextHash(instance.getJobContext());
                String currentHash = definitionHash(entity);
                if (producedHash != null && !producedHash.equals(currentHash)) {
                    entity.setPersistStatus(AnalysisDataset.PERSIST_STATUS_STALE);
                } else {
                    entity.setPersistStatus(AnalysisDataset.PERSIST_STATUS_READY);
                    entity.setPersistDefinitionHash(currentHash);
                    entity.setPersistLastSuccessTime(ZonedDateTime.now());
                    entity.setPersistLastError(null);
                }
            } else if (
                TaskConstants.TASK_STATUS_FAILED.equalsIgnoreCase(status) ||
                TaskConstants.TASK_STATUS_TIMEOUT.equalsIgnoreCase(status) ||
                TaskConstants.TASK_STATUS_INTERRUPTED.equalsIgnoreCase(status)
            ) {
                entity.setPersistStatus(AnalysisDataset.PERSIST_STATUS_FAILED);
                entity.setPersistLastError(truncate(instance.getJobMessage(), 2000));
            }
            datasetRepository.save(entity);
        } catch (Exception e) {
            LOG.warn("更新数据集持久化状态失败: {}", e.getMessage(), e);
        } finally {
            if (tenantSet) {
                TenantContext.clear();
            }
        }
    }

    /**
     * 返回持久化状态，供数据集编辑器和列表展示。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> status(String datasetId) {
        AnalysisDataset entity = loadDataset(datasetId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasetId", entity.getId());
        result.put("enabled", Boolean.TRUE.equals(entity.getPersistEnabled()));
        result.put("status", entity.getPersistStatus());
        result.put("targetDataSourceId", entity.getPersistTargetDataSourceId());
        result.put("targetSchema", entity.getPersistTargetSchema());
        result.put("targetTable", entity.getPersistTargetTable());
        result.put("writeMode", entity.getPersistWriteMode());
        result.put("cron", entity.getPersistCron());
        result.put("lastRunTime", entity.getPersistLastRunTime());
        result.put("lastSuccessTime", entity.getPersistLastSuccessTime());
        result.put("lastError", entity.getPersistLastError());
        result.put("rowCount", entity.getPersistRowCount());
        result.put("definitionHash", entity.getPersistDefinitionHash());
        result.put("currentDefinitionHash", definitionHash(entity));
        result.put("stale", !definitionHash(entity).equals(entity.getPersistDefinitionHash()));
        result.put("etlTaskId", entity.getPersistEtlTaskId());
        result.put("jobId", entity.getPersistJobId());
        if (StringUtils.hasText(entity.getPersistJobId())) {
            jobRepository.findById(entity.getPersistJobId()).ifPresent(job -> result.put("jobStatus", job.getStatus()));
        }
        return result;
    }

    /**
     * 生成最新 jobContext。由任务下发器调用；不写库。
     */
    @Transactional(readOnly = true)
    public String buildJobContext(AnalysisDataset entity) {
        ETLTaskDTO task = buildTask(entity, entity.getPersistJobId());
        String context = etlTaskService.generateETLJobJson(task);
        try {
            Map<String, Object> map = objectMapper.readValue(context, new TypeReference<Map<String, Object>>() {});
            map.put("datasetId", entity.getId());
            map.put("datasetDefinitionHash", definitionHash(entity));
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("数据集任务上下文生成失败：" + e.getOriginalMessage());
        }
    }

    public String definitionHash(AnalysisDataset entity) {
        try {
            StringBuilder material = new StringBuilder();
            material.append(nullToEmpty(entity.getType())).append('\n');
            material.append(nullToEmpty(entity.getDefinition())).append('\n');
            material.append(nullToEmpty(entity.getPersistTargetDataSourceId())).append('\n');
            material.append(nullToEmpty(entity.getPersistTargetSchema())).append('\n');
            material.append(nullToEmpty(entity.getPersistTargetTable())).append('\n');
            material.append(nullToEmpty(entity.getPersistWriteMode()));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(material.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private AnalysisDataset loadDataset(String datasetId) {
        return datasetRepository
            .findById(datasetId)
            .orElseThrow(() -> new IllegalArgumentException("数据集不存在：" + datasetId));
    }

    /**
     * 保存调度 Job。Job 上下文只保存数据集引用，真正的 ETL 上下文在任务下发时生成。
     */
    private Job saveGeneratedJob(AnalysisDataset entity) {
        Job job = null;
        if (StringUtils.hasText(entity.getPersistJobId())) {
            job = jobRepository.findById(entity.getPersistJobId()).orElse(null);
        }
        boolean created = false;
        if (job == null) {
            job = new Job();
            created = true;
        }
        job.setJobName(entity.getName() + "（数据集持久化）");
        job.setJobGroup("DATASET");
        job.setType(TaskConstants.TASK_TYPE_DATASET);
        job.setCron(entity.getPersistCron());
        job.setRetryCount(0);
        job.setTimeoutSeconds(0);
        job.setPriority(TaskConstants.DEFAULT_PRIORITY);
        job.setStatus(StringUtils.hasText(entity.getPersistCron()) ? TaskConstants.TASK_STATUS_ONLINE : TaskConstants.TASK_STATUS_OFFLINE);
        job.setTenantId(entity.getTenantId());
        job.setJobContext(descriptorJson(entity));
        if (created) {
            job.setCreateTime(ZonedDateTime.now());
        }
        job.setUpdateTime(ZonedDateTime.now());
        return jobService.save(job);
    }

    private String descriptorJson(AnalysisDataset entity) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("datasetId", entity.getId());
        descriptor.put("targetDataSourceId", entity.getPersistTargetDataSourceId());
        descriptor.put("targetSchema", entity.getPersistTargetSchema());
        descriptor.put("targetTable", entity.getPersistTargetTable());
        descriptor.put("writeMode", entity.getPersistWriteMode());
        descriptor.put("datasetDefinitionHash", definitionHash(entity));
        try {
            return objectMapper.writeValueAsString(descriptor);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("持久化任务描述生成失败：" + e.getOriginalMessage());
        }
    }

    private ETLTaskDTO buildTask(AnalysisDataset entity, String jobId) {
        String type = entity.getType() == null ? AnalysisDataset.TYPE_METRIC : entity.getType().trim().toUpperCase(Locale.ROOT);
        Graph graph = AnalysisDataset.TYPE_SQL.equals(type) ? buildSqlGraph(entity) : buildMetricGraph(entity);

        ETLTaskDTO task = new ETLTaskDTO();
        task.setId(entity.getPersistEtlTaskId());
        task.setTaskName(entity.getName() + "（数据集任务）");
        task.setTaskCode("ds_persist_" + entity.getId());
        task.setTaskDesc("由数据集 " + entity.getCode() + " 自动生成的结果持久化任务");
        task.setType(TaskConstants.ETL_TASK_KIND_BATCH);
        task.setCron(entity.getPersistCron());
        task.setRetryCount(0);
        task.setTimeoutSeconds(0);
        task.setPriority(TaskConstants.DEFAULT_PRIORITY);
        task.setStatus(StringUtils.hasText(entity.getPersistCron()) ? TaskConstants.TASK_STATUS_ONLINE : TaskConstants.TASK_STATUS_OFFLINE);
        task.setTenantId(entity.getTenantId());
        task.setJobId(jobId);
        task.setCreateTime(ZonedDateTime.now());
        task.setUpdateTime(ZonedDateTime.now());
        task.setNodes(graph.nodes);
        task.setEdges(graph.edges);
        return task;
    }

    /**
     * SQL 数据集：SqlInput -> StreamJdbcOutput。
     */
    private Graph buildSqlGraph(AnalysisDataset entity) {
        Map<String, Object> definition = parseDefinition(entity.getDefinition());
        String sql = asString(definition.get("sql"));
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 数据集定义缺少 sql");
        }
        if (sql.contains("${")) {
            throw new IllegalArgumentException("SQL 数据集包含 ${param} 占位符，暂不支持结果持久化");
        }
        String dataSourceId = entity.getDataSourceId();
        if (!StringUtils.hasText(dataSourceId)) {
            dataSourceId = asString(definition.get("dataSourceId"));
        }
        if (!StringUtils.hasText(dataSourceId)) {
            throw new IllegalArgumentException("SQL 数据集未绑定数据源");
        }
        final String resolvedDataSourceId = dataSourceId;
        DataSourceDTO source = dataSourceService
            .findOne(resolvedDataSourceId)
            .orElseThrow(() -> new IllegalArgumentException("SQL 数据集数据源不存在：" + resolvedDataSourceId));

        Graph graph = new Graph();
        String inputId = "source";
        graph.nodes.add(
            sqlInputNode(inputId, "数据源输入", source, source.getSchemaName(), sql, "dataset_source", 80, 160)
        );
        String outputId = "output";
        graph.nodes.add(outputNode(outputId, "持久化输出", entity, 520, 160));
        graph.edges.add(edge(inputId, outputId));
        return graph;
    }

    /**
     * 指标数据集：单来源直接 SqlInput -> StreamJdbcOutput；跨来源按来源写入 DuckDB 后合并再输出。
     */
    private Graph buildMetricGraph(AnalysisDataset entity) {
        MetricQueryDTO dto = buildMetricQueryDTO(entity);
        MetricQueryService.FederatedPlan federated = metricQueryService.federatedPlan(dto);
        Graph graph = new Graph();
        if (federated == null) {
            MetricQueryService.QueryPlan plan = metricQueryService.plan(dto);
            String inputId = "source";
            graph.nodes.add(
                sqlInputNode(inputId, "指标查询", plan.dataSource(), plan.dataSource().getSchemaName(), plan.sql(), "dataset_source", 80, 160)
            );
            String outputId = "output";
            graph.nodes.add(outputNode(outputId, "持久化输出", entity, 520, 160));
            graph.edges.add(edge(inputId, outputId));
            return graph;
        }

        List<String> writeIds = new ArrayList<>();
        int y = 80;
        for (int i = 0; i < federated.sources().size(); i++) {
            MetricQueryService.FederatedSource source = federated.sources().get(i);
            String inputId = "source_" + i;
            String writeId = "duckdb_" + i;
            graph.nodes.add(
                sqlInputNode(inputId, "来源 " + (i + 1), source.dataSource(), source.dataSource().getSchemaName(), source.sql(), source.table(), 80, y)
            );
            graph.nodes.add(
                node(writeId, "DuckDBWrite", "写入 DuckDB " + (i + 1), Map.of("table", source.table(), "model", "overwrite"), 340, y)
            );
            graph.edges.add(edge(inputId, writeId));
            writeIds.add(writeId);
            y += 140;
        }
        String mergeId = "merge";
        graph.nodes.add(node(mergeId, "DuckDBSql", "合并跨源结果", Map.of("sql", federated.mergeSql(), "outputTable", "dataset_result"), 620, 160));
        for (String writeId : writeIds) {
            graph.edges.add(edge(writeId, mergeId));
        }
        String outputId = "output";
        graph.nodes.add(outputNode(outputId, "持久化输出", entity, 900, 160));
        graph.edges.add(edge(mergeId, outputId));
        return graph;
    }

    private ETLNodeDTO sqlInputNode(
        String code,
        String label,
        DataSourceDTO dataSource,
        String schema,
        String sql,
        String outputTable,
        int x,
        int y
    ) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("sourceId", dataSource.getId());
        config.put("schema", schema == null ? "" : schema);
        config.put("sql", sql);
        config.put("outputTable", outputTable);
        return node(code, "SqlInput", label, config, x, y);
    }

    private ETLNodeDTO outputNode(String code, String label, AnalysisDataset entity, int x, int y) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("sourceId", entity.getPersistTargetDataSourceId());
        String targetSchema = entity.getPersistTargetSchema();
        if (!StringUtils.hasText(targetSchema)) {
            // StreamJdbcOutput 的 tableExists 依赖准确 schema；空串在 DuckDB 下会被当成
            // 不存在的 schema，导致 APPEND / 第二次 REPLACE 误判为“表不存在”后重复建表。
            targetSchema = dataSourceService
                .findOne(entity.getPersistTargetDataSourceId())
                .map(DataSourceDTO::getSchemaName)
                .orElse("");
        }
        config.put("schema", targetSchema == null ? "" : targetSchema);
        config.put("table", entity.getPersistTargetTable());
        boolean replace = !AnalysisDataset.PERSIST_MODE_APPEND.equalsIgnoreCase(entity.getPersistWriteMode());
        config.put("model", replace ? "overwrite" : "append");
        if (replace) {
            config.put("dropIfTableExists", true);
        }
        return node(code, "StreamJdbcOutput", label, config, x, y);
    }

    private ETLNodeDTO node(String code, String type, String label, Map<String, Object> config, int x, int y) {
        ETLNodeDTO node = new ETLNodeDTO();
        node.setCode(code);
        node.setType(type);
        node.setLabel(label);
        node.setStatus("SUCCESS");
        node.setxAxis(String.valueOf(x));
        node.setyAxis(String.valueOf(y));
        try {
            node.setConfig(objectMapper.writeValueAsString(config));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("任务节点配置序列化失败：" + e.getOriginalMessage());
        }
        return node;
    }

    private ETLEdgeDTO edge(String source, String target) {
        ETLEdgeDTO edge = new ETLEdgeDTO();
        edge.setSource(source);
        edge.setTarget(target);
        edge.setName("");
        edge.setConfig("{}");
        return edge;
    }

    private MetricQueryDTO buildMetricQueryDTO(AnalysisDataset entity) {
        Map<String, Object> definition = parseDefinition(entity.getDefinition());
        Object raw = definition.get("metricQuery");
        Map<String, Object> metricQuery = raw instanceof Map<?, ?> map ? castMap(map) : definition;
        MetricQueryDTO dto = objectMapper.convertValue(metricQuery, MetricQueryDTO.class);
        if (dto.getMetricCodes() == null || dto.getMetricCodes().isEmpty()) {
            throw new IllegalArgumentException("指标数据集必须至少选择一个指标");
        }
        Object filterConfig = metricQuery.get("filterConfig");
        if (filterConfig instanceof Map<?, ?> || filterConfig instanceof List<?>) {
            try {
                dto.setFilterConfig(objectMapper.writeValueAsString(filterConfig));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("指标筛选条件序列化失败：" + e.getOriginalMessage());
            }
        } else if (filterConfig != null) {
            dto.setFilterConfig(String.valueOf(filterConfig));
        }
        return dto;
    }

    private Map<String, Object> parseDefinition(String definitionJson) {
        if (!StringUtils.hasText(definitionJson)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> definition = objectMapper.readValue(definitionJson, new TypeReference<Map<String, Object>>() {});
            return definition == null ? new LinkedHashMap<>() : definition;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("数据集定义不是合法 JSON：" + e.getOriginalMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private String readContextHash(String jobContext) {
        if (!StringUtils.hasText(jobContext)) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(jobContext, new TypeReference<Map<String, Object>>() {});
            Object value = map.get("datasetDefinitionHash");
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public static String qualifiedTable(DataSourceDTO dataSource, String schema, String table) {
        String dbType = dataSource == null ? null : dataSource.getType();
        boolean mysql = dbType != null && ("MYSQL".equalsIgnoreCase(dbType) || "MARIADB".equalsIgnoreCase(dbType));
        String quote = mysql ? "`" : "\"";
        String result = quote + table + quote;
        if (StringUtils.hasText(schema)) {
            result = quote + schema + quote + "." + result;
        }
        return result;
    }

    private static final class Graph {

        private final List<ETLNodeDTO> nodes = new ArrayList<>();
        private final List<ETLEdgeDTO> edges = new ArrayList<>();
    }
}
