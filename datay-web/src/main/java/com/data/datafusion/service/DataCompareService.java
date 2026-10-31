package com.data.datafusion.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.data.datafusion.domain.DataCompareTask;
import com.data.datafusion.domain.Job;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.DataCompareTaskRepository;
import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.FileStorageConfigService.FileStorageConfig;
import com.data.datafusion.service.dto.DataCompareResultDTO;
import com.data.datafusion.service.dto.DataCompareTableDTO;
import com.data.datafusion.service.dto.DataCompareTaskDTO;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.mapper.DataCompareTaskMapper;
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
import java.util.HashMap;
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
 * 数据比对任务服务。
 *
 * <p>任务支持多个表对。执行时每个表对生成一条独立的 ETL 分支，分别物化到 DuckDB、
 * 执行比对并导出差异文件和汇总文件；任务实例的状态统一来自 JobInstance。</p>
 */
@Service
@Transactional
public class DataCompareService {

    private static final Logger LOG = LoggerFactory.getLogger(DataCompareService.class);

    private static final String DEFAULT_OUTPUT_DIR = "data-compare";
    private static final Pattern IDENTIFIER = Pattern.compile("^[\\p{L}_][\\p{L}\\p{N}_$]*$");

    private final DataCompareTaskRepository taskRepository;

    private final DataCompareTaskMapper taskMapper;

    private final DataCompareResultService resultService;

    private final DataCompareSqlBuilder sqlBuilder;

    private final DataSourceService dataSourceService;

    private final JobService jobService;

    private final JobInstanceService jobInstanceService;

    private final FileStorageConfigService fileStorageConfigService;

    private final FileStorageStrategy fileStorageStrategy;

    public DataCompareService(
        DataCompareTaskRepository taskRepository,
        DataCompareTaskMapper taskMapper,
        DataCompareResultService resultService,
        DataCompareSqlBuilder sqlBuilder,
        DataSourceService dataSourceService,
        JobService jobService,
        JobInstanceService jobInstanceService,
        FileStorageConfigService fileStorageConfigService,
        FileStorageStrategy fileStorageStrategy
    ) {
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
        this.resultService = resultService;
        this.sqlBuilder = sqlBuilder;
        this.dataSourceService = dataSourceService;
        this.jobService = jobService;
        this.jobInstanceService = jobInstanceService;
        this.fileStorageConfigService = fileStorageConfigService;
        this.fileStorageStrategy = fileStorageStrategy;
    }

    public DataCompareTaskDTO save(DataCompareTaskDTO dto) {
        normalize(dto);
        validateTaskConfig(dto);
        DataCompareTask task = taskMapper.toEntity(dto);
        task.setTableConfigs(JSONUtil.toJsonStr(dto.getTables()));
        applyLegacyFields(task, dto.getTables());
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

    public DataCompareTaskDTO update(DataCompareTaskDTO dto) {
        normalize(dto);
        validateTaskConfig(dto);
        DataCompareTask existing = taskRepository
            .findById(dto.getId())
            .orElseThrow(() -> new IllegalArgumentException("数据比对任务不存在: " + dto.getId()));
        DataCompareTask task = taskMapper.toEntity(dto);
        task.setTableConfigs(JSONUtil.toJsonStr(dto.getTables()));
        applyLegacyFields(task, dto.getTables());
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
    public Page<DataCompareTaskDTO> findAll(Pageable pageable, String search) {
        return taskRepository.findAll(buildSearchSpecification(search), pageable).map(task -> {
            DataCompareTaskDTO dto = toDto(task);
            attachLastResult(task, dto);
            return dto;
        });
    }

    @Transactional(readOnly = true)
    public Optional<DataCompareTaskDTO> findOne(String id) {
        return taskRepository
            .findById(id)
            .map(task -> {
                DataCompareTaskDTO dto = toDto(task);
                attachLastResult(task, dto);
                return dto;
            });
    }

    /**
     * 任务最近状态、最近执行时间统一取最近一次 JobInstance 的状态。
     */
    private void attachLastResult(DataCompareTask task, DataCompareTaskDTO dto) {
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

    public void delete(String id) {
        DataCompareTask task = taskRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("数据比对任务不存在: " + id));
        if (task.getJobId() != null) {
            jobService.findOneJob(task.getJobId()).ifPresent(job -> jobService.delete(job.getId()));
        }
        taskRepository.delete(task);
    }

    /**
     * 立即执行数据比对任务，返回本次执行结果视图。
     */
    public DataCompareResultDTO execute(String taskId) {
        DataCompareTask task = taskRepository
            .findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("数据比对任务不存在: " + taskId));

        if (task.getJobId() != null
            && (!jobInstanceService.findByJobCodeAndStatus(task.getJobId(), TaskConstants.TASK_STATUS_RUNNING).isEmpty()
                || !jobInstanceService.findByJobCodeAndStatus(task.getJobId(), TaskConstants.TASK_STATUS_APPENDING).isEmpty())) {
            throw new IllegalArgumentException("数据比对任务正在执行中，请稍后再试");
        }

        DataSourceDTO leftDataSource = requireJdbcDataSource(task.getLeftDataSourceId());
        DataSourceDTO rightDataSource = requireJdbcDataSource(task.getRightDataSourceId());
        String leftSchema = effectiveSchema(task.getLeftSchema(), leftDataSource);
        String rightSchema = effectiveSchema(task.getRightSchema(), rightDataSource);
        List<DataCompareTableDTO> tables = resolveTables(task);
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("请至少配置一张比对表");
        }

        Job job = task.getJobId() == null ? null : jobService.findOneJob(task.getJobId()).orElse(null);
        if (job == null) {
            job = saveJob(task);
            task.setJobId(job.getId());
        }

        // 先创建 JobInstance 拿到 UUID，再生成包含该 UUID 的文件路径和 jobContext。
        JobInstance instance = jobInstanceService.buildJobInstance(job.getId());
        instance.setCreateTime(ZonedDateTime.now());
        instance = jobInstanceService.save(instance);
        String jobInstanceId = instance.getId();

        String outputDir = DataCompareSqlBuilder.joinPath(normalizeOutputDir(task.getOutputDir()), task.getId());
        String resultFormat = normalizeFormat(task.getOutputFormat());
        FileStorageConfig storage = fileStorageConfigService.getConfig();
        String tenantPrefix = tenantPrefix();

        boolean compareDetail = isCompareDetail(task);
        List<TablePlan> plans = new ArrayList<>();
        Map<String, List<String>> leftColumnCache = new HashMap<>();
        Map<String, List<String>> rightColumnCache = new HashMap<>();
        for (int i = 0; i < tables.size(); i++) {
            DataCompareTableDTO table = tables.get(i);
            String leftTable = table.getLeftTable();
            String rightTable = table.getRightTable();
            validateIdentifier(leftTable, "左侧表名");
            validateIdentifier(rightTable, "右侧表名");

            String suffix = String.valueOf(i);
            String summaryPath = DataCompareSqlBuilder.joinPath(
                outputDir,
                "summary_" + jobInstanceId + "_" + suffix + "_" + safeTableName(leftTable) + ".csv"
            );
            String resultPath = null;
            String compareSql;
            String exportSql;
            if (compareDetail) {
                List<String> keyColumns = sqlBuilder.parseColumns(table.getKeyColumns());
                if (keyColumns.isEmpty()) {
                    throw new IllegalArgumentException("表 " + leftTable + " 的主键字段不能为空");
                }
                List<String> leftColumns = leftColumnCache.computeIfAbsent(leftTable, key -> readColumns(leftDataSource, leftSchema, key));
                List<String> rightColumns = rightColumnCache.computeIfAbsent(rightTable, key -> readColumns(rightDataSource, rightSchema, key));
                validateColumnsExist(keyColumns, leftColumns, rightColumns, "表 " + leftTable + " 的主键字段");

                List<String> compareColumns = sqlBuilder.parseColumns(table.getCompareColumns());
                if (compareColumns.isEmpty()) {
                    compareColumns = deriveCompareColumns(leftColumns, rightColumns, keyColumns);
                } else {
                    validateColumnsExist(compareColumns, leftColumns, rightColumns, "表 " + leftTable + " 的比对字段");
                    compareColumns.removeAll(keyColumns);
                }
                String fileStem = "diff_result_" + jobInstanceId + "_" + suffix + "_" + safeTableName(leftTable);
                resultPath = DataCompareSqlBuilder.joinPath(outputDir, fileStem + ("PARQUET".equals(resultFormat) ? ".parquet" : ".csv"));
                compareSql = sqlBuilder.buildCompareSql(
                    suffix,
                    keyColumns,
                    compareColumns,
                    Boolean.TRUE.equals(task.getIgnoreCase()),
                    Boolean.TRUE.equals(task.getTrimWhitespace())
                );
                exportSql = sqlBuilder.buildExportSql(
                    storage,
                    tenantPrefix,
                    resultPath,
                    summaryPath,
                    resultFormat,
                    DataCompareSqlBuilder.tableName(DataCompareSqlBuilder.RESULT_TABLE, suffix),
                    DataCompareSqlBuilder.tableName(DataCompareSqlBuilder.SUMMARY_TABLE, suffix)
                );
            } else {
                // 仅记录数对比：不读取数据，但执行前校验两侧表存在，避免 ETL 启动后才失败。
                leftColumnCache.computeIfAbsent(leftTable, key -> readColumns(leftDataSource, leftSchema, key));
                rightColumnCache.computeIfAbsent(rightTable, key -> readColumns(rightDataSource, rightSchema, key));
                compareSql = sqlBuilder.buildCountSummarySql(suffix);
                exportSql = sqlBuilder.buildSummaryExportSql(
                    storage,
                    tenantPrefix,
                    summaryPath,
                    resultFormat,
                    DataCompareSqlBuilder.tableName(DataCompareSqlBuilder.SUMMARY_TABLE, suffix)
                );
            }
            plans.add(
                new TablePlan(
                    i,
                    leftTable,
                    rightTable,
                    normalizeWhere(table.getLeftWhere()),
                    normalizeWhere(table.getRightWhere()),
                    compareSql,
                    exportSql,
                    resultPath,
                    summaryPath,
                    resultFormat
                )
            );
        }

        String jobJson = buildJobJson(leftDataSource, rightDataSource, leftSchema, rightSchema, plans, compareDetail);
        jobJson = attachResultMetadata(jobJson, task, outputDir, resultFormat, plans);

        job.setType(TaskConstants.TASK_TYPE_DATA_COMPARE);
        job.setJobContext(jobJson);
        job.setUpdateTime(ZonedDateTime.now());
        job = jobService.save(job);

        instance.setJobContext(jobJson);
        instance = jobInstanceService.save(instance);

        task.setJobId(job.getId());
        task.setLastRunTime(ZonedDateTime.now());
        task.setUpdateTime(ZonedDateTime.now());
        taskRepository.save(task);

        jobInstanceService.startInstance(instance);

        LOG.info("数据比对任务已提交: taskId={}, jobInstanceId={}, tables={}", task.getId(), jobInstanceId, plans.size());
        return resultService
            .findByJobInstanceId(jobInstanceId)
            .orElseThrow(() -> new IllegalStateException("数据比对任务实例未创建: " + jobInstanceId));
    }

    /**
     * 任务创建/更新时创建任务级输出目录：{outputDir}/{taskId}。
     */
    private void ensureTaskDirectory(DataCompareTask task) {
        if (task == null || task.getId() == null) {
            return;
        }
        fileStorageStrategy.mkdir(DataCompareSqlBuilder.joinPath(normalizeOutputDir(task.getOutputDir()), task.getId()));
    }

    private Job saveJob(DataCompareTask task) {
        Job job = task.getJobId() == null ? new Job() : jobService.findOneJob(task.getJobId()).orElseGet(Job::new);
        job.setJobName(task.getTaskName());
        job.setType(TaskConstants.TASK_TYPE_DATA_COMPARE);
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

    /**
     * 把本次执行的多表结果文件元数据写入 JobContext，JobInstance 会保存该快照。
     */
    private String attachResultMetadata(
        String jobJson,
        DataCompareTask task,
        String outputDir,
        String resultFormat,
        List<TablePlan> plans
    ) {
        JSONObject job = JSONUtil.parseObj(jobJson);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("taskId", task.getId());
        metadata.put("taskName", task.getTaskName());
        metadata.put("outputDir", outputDir);
        metadata.put("resultFormat", resultFormat);
        metadata.put("compareRecordCount", isCompareRecordCount(task));
        metadata.put("compareDetail", isCompareDetail(task));
        List<Map<String, Object>> tableMetadata = new ArrayList<>();
        for (TablePlan plan : plans) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", plan.index());
            item.put("leftTable", plan.leftTable());
            item.put("rightTable", plan.rightTable());
            item.put("resultPath", plan.resultPath());
            item.put("summaryPath", plan.summaryPath());
            item.put("resultFormat", plan.resultFormat());
            tableMetadata.add(item);
        }
        metadata.put("tables", tableMetadata);
        if (!plans.isEmpty()) {
            metadata.put("resultPath", plans.get(0).resultPath());
            metadata.put("summaryPath", plans.get(0).summaryPath());
        }
        job.put("dataCompare", metadata);
        return job.toString();
    }

    /**
     * 记录数对比输入：只执行 COUNT(*)，不读取源表全量数据。
     */
    private Map<String, Object> countInput(
        String id,
        DataSourceDTO dataSource,
        String schema,
        String table,
        String where
    ) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put(".id", id);
        input.put(".name", "SqlInput");
        input.put("sourceId", toSourceMap(dataSource, schema));
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS row_count FROM ").append(schema).append(".").append(table);
        if (where != null && !where.trim().isEmpty()) {
            sql.append(" WHERE ").append(where.trim());
        }
        input.put("sql", sql.toString());
        return input;
    }

    String buildJobJson(
        DataSourceDTO leftDataSource,
        DataSourceDTO rightDataSource,
        String leftSchema,
        String rightSchema,
        List<TablePlan> plans,
        boolean compareDetail
    ) {
        Map<String, Object> jobJson = new LinkedHashMap<>();
        List<Map<String, Object>> units = new ArrayList<>();
        List<Map<String, Object>> connections = new ArrayList<>();

        for (TablePlan plan : plans) {
            String suffix = String.valueOf(plan.index());
            String leftInputId = "left_input_" + suffix;
            String leftWriteId = "left_write_" + suffix;
            String rightInputId = "right_input_" + suffix;
            String rightWriteId = "right_write_" + suffix;
            String compareId = "compare_sql_" + suffix;
            String exportId = "export_sql_" + suffix;

            if (compareDetail) {
                Map<String, Object> leftInput = new LinkedHashMap<>();
                leftInput.put(".id", leftInputId);
                leftInput.put(".name", "StreamJdbcInput");
                leftInput.put("sourceId", toSourceMap(leftDataSource, leftSchema));
                leftInput.put("schema", leftSchema);
                leftInput.put("table", plan.leftTable());
                leftInput.put("where", plan.leftWhere());
                leftInput.put("incrColumn", "");
                units.add(leftInput);

                Map<String, Object> rightInput = new LinkedHashMap<>();
                rightInput.put(".id", rightInputId);
                rightInput.put(".name", "StreamJdbcInput");
                rightInput.put("sourceId", toSourceMap(rightDataSource, rightSchema));
                rightInput.put("schema", rightSchema);
                rightInput.put("table", plan.rightTable());
                rightInput.put("where", plan.rightWhere());
                rightInput.put("incrColumn", "");
                units.add(rightInput);
            } else {
                units.add(countInput(leftInputId, leftDataSource, leftSchema, plan.leftTable(), plan.leftWhere()));
                units.add(countInput(rightInputId, rightDataSource, rightSchema, plan.rightTable(), plan.rightWhere()));
            }

            Map<String, Object> leftWrite = new LinkedHashMap<>();
            leftWrite.put(".id", leftWriteId);
            leftWrite.put(".name", "DuckDBWrite");
            leftWrite.put("table", DataCompareSqlBuilder.tableName(DataCompareSqlBuilder.LOCAL_TABLE, suffix));
            leftWrite.put("model", "overwrite");
            units.add(leftWrite);

            Map<String, Object> rightWrite = new LinkedHashMap<>();
            rightWrite.put(".id", rightWriteId);
            rightWrite.put(".name", "DuckDBWrite");
            rightWrite.put("table", DataCompareSqlBuilder.tableName(DataCompareSqlBuilder.RIGHT_TABLE, suffix));
            rightWrite.put("model", "overwrite");
            units.add(rightWrite);

            Map<String, Object> compareSqlUnit = new LinkedHashMap<>();
            compareSqlUnit.put(".id", compareId);
            compareSqlUnit.put(".name", "DuckDBSql");
            compareSqlUnit.put("sql", plan.compareSql());
            units.add(compareSqlUnit);

            Map<String, Object> exportSqlUnit = new LinkedHashMap<>();
            exportSqlUnit.put(".id", exportId);
            exportSqlUnit.put(".name", "DuckDBSql");
            exportSqlUnit.put("sql", plan.exportSql());
            units.add(exportSqlUnit);

            connections.add(connection(leftInputId, leftWriteId));
            connections.add(connection(rightInputId, rightWriteId));
            connections.add(connection(leftWriteId, compareId));
            connections.add(connection(rightWriteId, compareId));
            connections.add(connection(compareId, exportId));
        }

        jobJson.put("units", units);
        jobJson.put("connections", connections);
        jobJson.put("version", "1.0.0");
        return JSONUtil.toJsonStr(jobJson);
    }

    /**
     * 兼容单表测试/旧调用的 JobContext 构建。
     */
    String buildJobJson(
        DataCompareTask task,
        DataSourceDTO leftDataSource,
        DataSourceDTO rightDataSource,
        String leftSchema,
        String rightSchema,
        String compareSql,
        String exportSql
    ) {
        TablePlan plan = new TablePlan(
            0,
            task.getLeftTable(),
            task.getRightTable(),
            normalizeWhere(task.getLeftWhere()),
            normalizeWhere(task.getRightWhere()),
            compareSql,
            exportSql,
            "",
            "",
            normalizeFormat(task.getOutputFormat())
        );
        return buildJobJson(leftDataSource, rightDataSource, leftSchema, rightSchema, List.of(plan), true);
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

    private Map<String, Object> connection(String sourceId, String targetId) {
        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("sourceId", sourceId);
        connection.put("targetId", targetId);
        connection.put("sourcePort", 0);
        return connection;
    }

    private DataSourceDTO requireJdbcDataSource(String id) {
        DataSourceDTO dataSource = dataSourceService
            .findOne(id)
            .orElseThrow(() -> new IllegalArgumentException("数据源不存在: " + id));
        if (dataSource.getType() != null && !dataSource.getType().isBlank()) {
            try {
                if (!(DataSourceProviderFactory.provider(dataSource.getType()) instanceof JdbcDataSourceProvider)) {
                    throw new IllegalArgumentException("数据比对仅支持 JDBC 关系型数据源: " + dataSource.getName());
                }
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                LOG.warn("无法解析数据源类型 {}，按 JDBC 处理", dataSource.getType());
            }
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

    private List<String> deriveCompareColumns(List<String> leftColumns, List<String> rightColumns, List<String> keyColumns) {
        Set<String> rightSet = new HashSet<>(rightColumns);
        Set<String> keys = new HashSet<>(keyColumns);
        List<String> result = new ArrayList<>();
        for (String column : leftColumns) {
            if (!keys.contains(column) && rightSet.contains(column)) {
                result.add(column);
            }
        }
        return result;
    }

    private void validateColumnsExist(List<String> columns, List<String> leftColumns, List<String> rightColumns, String label) {
        Set<String> leftSet = new HashSet<>(leftColumns);
        Set<String> rightSet = new HashSet<>(rightColumns);
        for (String column : columns) {
            if (!leftSet.contains(column) || !rightSet.contains(column)) {
                throw new IllegalArgumentException(label + "在两侧表中不存在: " + column);
            }
        }
    }

    private void validateTaskConfig(DataCompareTaskDTO dto) {
        if (dto.getTaskName() == null || dto.getTaskName().isBlank()) {
            throw new IllegalArgumentException("任务名称不能为空");
        }
        if (dto.getLeftDataSourceId() == null || dto.getLeftDataSourceId().isBlank()) {
            throw new IllegalArgumentException("请选择左侧数据源");
        }
        if (dto.getRightDataSourceId() == null || dto.getRightDataSourceId().isBlank()) {
            throw new IllegalArgumentException("请选择右侧数据源");
        }
        if (dto.getTables() == null || dto.getTables().isEmpty()) {
            throw new IllegalArgumentException("请至少配置一张比对表");
        }
        if (!Boolean.TRUE.equals(dto.getCompareRecordCount()) && !Boolean.TRUE.equals(dto.getCompareDetail())) {
            throw new IllegalArgumentException("请至少选择一个对比项");
        }
        for (DataCompareTableDTO table : dto.getTables()) {
            validateIdentifier(table.getLeftTable(), "左侧表名");
            validateIdentifier(table.getRightTable(), "右侧表名");
            if (Boolean.TRUE.equals(dto.getCompareDetail()) && sqlBuilder.parseColumns(table.getKeyColumns()).isEmpty()) {
                throw new IllegalArgumentException("表 " + table.getLeftTable() + " 的主键字段不能为空");
            }
            validateWhere(table.getLeftWhere());
            validateWhere(table.getRightWhere());
        }
        normalizeOutputDir(dto.getOutputDir());
        normalizeFormat(dto.getOutputFormat());
    }

    private void validateIdentifier(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        if (!IDENTIFIER.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException(label + "只能包含字母、数字、下划线和 $，且不能以数字开头: " + value);
        }
    }

    private void validateWhere(String where) {
        if (where != null && where.contains(";")) {
            throw new IllegalArgumentException("过滤条件不能包含分号");
        }
    }

    private String normalizeWhere(String where) {
        if (where == null) {
            return "";
        }
        String result = where.trim();
        if (result.regionMatches(true, 0, "where", 0, 5) && (result.length() == 5 || Character.isWhitespace(result.charAt(5)))) {
            result = result.substring(5).trim();
        }
        return result;
    }

    private String normalizeOutputDir(String outputDir) {
        String result = outputDir == null ? "" : outputDir.trim().replace('\\', '/');
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.isEmpty()) {
            result = DEFAULT_OUTPUT_DIR;
        }
        if (result.contains("..") || result.contains(";") || result.contains("--") || result.contains("/*")) {
            throw new IllegalArgumentException("输出目录包含非法字符");
        }
        return result;
    }

    private String normalizeFormat(String format) {
        String result = format == null ? "CSV" : format.trim().toUpperCase(Locale.ROOT);
        if (!"CSV".equals(result) && !"PARQUET".equals(result)) {
            throw new IllegalArgumentException("输出格式仅支持 CSV 或 PARQUET");
        }
        return result;
    }

    private void normalize(DataCompareTaskDTO dto) {
        if (dto.getCompareRecordCount() == null) {
            dto.setCompareRecordCount(Boolean.TRUE);
        }
        if (dto.getCompareDetail() == null) {
            dto.setCompareDetail(Boolean.FALSE);
        }
        if (dto.getIgnoreCase() == null) {
            dto.setIgnoreCase(Boolean.FALSE);
        }
        if (dto.getTrimWhitespace() == null) {
            dto.setTrimWhitespace(Boolean.FALSE);
        }
        dto.setOutputFormat(normalizeFormat(dto.getOutputFormat()));
        dto.setOutputDir(normalizeOutputDir(dto.getOutputDir()));
        List<DataCompareTableDTO> tables = dto.getTables() == null ? new ArrayList<>() : new ArrayList<>(dto.getTables());
        tables.removeIf(table -> table == null || table.getLeftTable() == null || table.getLeftTable().isBlank());
        for (DataCompareTableDTO table : tables) {
            table.setLeftTable(table.getLeftTable().trim());
            table.setRightTable(table.getRightTable() == null || table.getRightTable().isBlank() ? table.getLeftTable() : table.getRightTable().trim());
            table.setLeftWhere(normalizeWhere(table.getLeftWhere()));
            table.setRightWhere(normalizeWhere(table.getRightWhere()));
            table.setKeyColumns(String.join(",", sqlBuilder.parseColumns(table.getKeyColumns())));
            table.setCompareColumns(String.join(",", sqlBuilder.parseColumns(table.getCompareColumns())));
        }
        if (tables.isEmpty() && dto.getLeftTable() != null && !dto.getLeftTable().isBlank()) {
            DataCompareTableDTO legacy = new DataCompareTableDTO();
            legacy.setLeftTable(dto.getLeftTable().trim());
            legacy.setRightTable(dto.getRightTable() == null || dto.getRightTable().isBlank() ? dto.getLeftTable().trim() : dto.getRightTable().trim());
            legacy.setLeftWhere(normalizeWhere(dto.getLeftWhere()));
            legacy.setRightWhere(normalizeWhere(dto.getRightWhere()));
            legacy.setKeyColumns(String.join(",", sqlBuilder.parseColumns(dto.getKeyColumns())));
            legacy.setCompareColumns(String.join(",", sqlBuilder.parseColumns(dto.getCompareColumns())));
            tables.add(legacy);
        }
        dto.setTables(tables);
    }

    private void applyLegacyFields(DataCompareTask task, List<DataCompareTableDTO> tables) {
        if (tables == null || tables.isEmpty()) {
            return;
        }
        DataCompareTableDTO first = tables.get(0);
        task.setLeftTable(first.getLeftTable());
        task.setRightTable(first.getRightTable());
        task.setLeftWhere(first.getLeftWhere());
        task.setRightWhere(first.getRightWhere());
        task.setKeyColumns(first.getKeyColumns());
        task.setCompareColumns(first.getCompareColumns());
    }

    private List<DataCompareTableDTO> resolveTables(DataCompareTask task) {
        if (task.getTableConfigs() != null && !task.getTableConfigs().isBlank()) {
            try {
                List<DataCompareTableDTO> tables = JSONUtil.parseArray(task.getTableConfigs()).toList(DataCompareTableDTO.class);
                if (tables != null && !tables.isEmpty()) {
                    return tables;
                }
            } catch (Exception e) {
                LOG.warn("解析数据比对多表配置失败，回退到旧版单表配置: taskId={}, error={}", task.getId(), e.getMessage());
            }
        }
        if (task.getLeftTable() == null || task.getLeftTable().isBlank()) {
            return new ArrayList<>();
        }
        DataCompareTableDTO legacy = new DataCompareTableDTO();
        legacy.setLeftTable(task.getLeftTable());
        legacy.setRightTable(task.getRightTable() == null || task.getRightTable().isBlank() ? task.getLeftTable() : task.getRightTable());
        legacy.setLeftWhere(task.getLeftWhere());
        legacy.setRightWhere(task.getRightWhere());
        legacy.setKeyColumns(task.getKeyColumns());
        legacy.setCompareColumns(task.getCompareColumns());
        List<DataCompareTableDTO> tables = new ArrayList<>();
        tables.add(legacy);
        return tables;
    }

    private DataCompareTaskDTO toDto(DataCompareTask task) {
        DataCompareTaskDTO dto = taskMapper.toDto(task);
        dto.setTables(resolveTables(task));
        return dto;
    }

    private boolean isCompareRecordCount(DataCompareTask task) {
        return task.getCompareRecordCount() == null || task.getCompareRecordCount();
    }

    private boolean isCompareDetail(DataCompareTask task) {
        return task.getCompareDetail() == null || task.getCompareDetail();
    }

    private String safeTableName(String table) {
        if (table == null || table.isBlank()) {
            return "table";
        }
        String safe = table.replaceAll("[^\\p{L}\\p{N}_]", "_");
        return safe.length() > 40 ? safe.substring(0, 40) : safe;
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

    private Specification<DataCompareTask> buildSearchSpecification(String search) {
        if (search == null || search.trim().isEmpty()) {
            return Specification.where(null);
        }
        String keyword = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(cb.lower(root.get("taskName")), keyword));
            predicates.add(cb.like(cb.lower(root.get("taskDesc")), keyword));
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    record TablePlan(
        int index,
        String leftTable,
        String rightTable,
        String leftWhere,
        String rightWhere,
        String compareSql,
        String exportSql,
        String resultPath,
        String summaryPath,
        String resultFormat
    ) {}
}
