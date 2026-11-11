package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.JobInstanceRepository;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.FileStorageConfigService.FileStorageConfig;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.ETLEdgeDTO;
import com.data.datafusion.service.dto.ETLNodeDTO;
import com.data.datafusion.service.dto.ETLTaskDTO;
import com.data.file.FileStorageStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * ETL 数据质量检核组件端到端验证。
 */
@IntegrationTest
@WithMockUser
class ETLDataQualityCheckIT {

    @Autowired
    private ETLTaskService etlTaskService;

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private JobService jobService;

    @Autowired
    private JobInstanceRepository jobInstanceRepository;

    @Autowired
    private FileStorageConfigService fileStorageConfigService;

    @Autowired
    private FileStorageStrategy fileStorageStrategy;

    @Autowired
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    private final List<String> taskIds = new ArrayList<>();

    private String dataSourceId;

    private FileStorageConfig originalStorageConfig;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(0L);
        TenantContext.setTenantCode("default");
        originalStorageConfig = fileStorageConfigService.getConfig();
        FileStorageConfig storageConfig = new FileStorageConfig();
        storageConfig.type = "local";
        storageConfig.localBasePath = tempDir.resolve("files").toString();
        fileStorageConfigService.updateAndReload(storageConfig);
    }

    @AfterEach
    void tearDown() {
        TenantContext.setTenantId(0L);
        TenantContext.setTenantCode("default");
        taskIds.forEach(id -> {
            try {
                etlTaskService.delete(id);
            } catch (Exception ignored) {
                // ignore cleanup failure
            }
        });
        if (dataSourceId != null) {
            try {
                dataSourceService.delete(dataSourceId);
            } catch (Exception ignored) {
                // ignore cleanup failure
            }
        }
        if (originalStorageConfig != null) {
            fileStorageConfigService.updateAndReload(originalStorageConfig);
        }
        TenantContext.clear();
    }

    @Test
    void shouldExecuteEtlTaskWithQualityCheckAndWriteCsvResult() throws Exception {
        dataSourceId = createDuckDbSource();

        ETLTaskDTO failedTask = createTask("ETL质检失败", "customer_name", "STREAM");
        taskIds.add(failedTask.getId());
        DataQualityCheckUnit failedUnit = extractQualityCheckUnit(jobContext(failedTask));
        assertThat(failedUnit.checkMode()).isEqualTo("STREAM");
        assertThat(failedUnit.outputFormat()).isEqualTo("CSV");
        assertThat(failedUnit.failOnWarn()).isFalse();
        assertThat(failedUnit.sampleLimit()).isEqualTo(100);
        assertThat(failedUnit.storageType()).isEqualTo("local");
        assertThat(failedUnit.tenantPrefix()).isEqualTo("tenants/default");

        JobInstance failedInstance = executeAndWait(failedTask);
        assertThat(failedInstance.getStatus()).isEqualTo(TaskConstants.TASK_STATUS_FAILED);

        String failedSummaryPath = join(
            failedUnit.outputDir(),
            failedInstance.getInstanceCode(),
            failedUnit.resultPrefix() + "_batch_1_summary.csv"
        );
        assertThat(fileStorageStrategy.exists(failedSummaryPath)).isTrue();
        assertThat(readFile(failedSummaryPath)).contains("FAIL").contains("customer_name");
        assertThat(fileStorageStrategy.list(join(failedUnit.outputDir(), failedInstance.getInstanceCode())))
            .anyMatch(file -> file.name().contains("_detail_"));

        ETLTaskDTO passedTask = createTask("ETL质检通过", "order_id", "FULL");
        taskIds.add(passedTask.getId());
        DataQualityCheckUnit passedUnit = extractQualityCheckUnit(jobContext(passedTask));
        assertThat(passedUnit.checkMode()).isEqualTo("FULL");
        JobInstance passedInstance = executeAndWait(passedTask);
        assertThat(passedInstance.getStatus()).isEqualTo(TaskConstants.TASK_STATUS_SUCCESSFUL);
        String passedSummaryPath = join(
            passedUnit.outputDir(),
            passedInstance.getInstanceCode(),
            passedUnit.resultPrefix() + "_summary.csv"
        );
        assertThat(readFile(passedSummaryPath)).contains("PASS").contains("order_id");
    }

    @Test
    void shouldExecuteEtlTaskWithCustomSqlRuleReferencingSourceTable() throws Exception {
        dataSourceId = createDuckDbSource();

        ETLTaskDTO failedTask = createCustomSqlTask(
            "ETL自定义SQL质检失败",
            "SELECT COUNT(*) AS invalid_rows FROM dq_orders WHERE customer_name IS NULL",
            "STREAM"
        );
        taskIds.add(failedTask.getId());
        DataQualityCheckUnit failedUnit = extractQualityCheckUnit(jobContext(failedTask));
        JobInstance failedInstance = executeAndWait(failedTask);
        assertThat(failedInstance.getStatus()).isEqualTo(TaskConstants.TASK_STATUS_FAILED);
        String failedSummaryPath = join(
            failedUnit.outputDir(),
            failedInstance.getInstanceCode(),
            failedUnit.resultPrefix() + "_batch_1_summary.csv"
        );
        assertThat(fileStorageStrategy.exists(failedSummaryPath)).isTrue();
        assertThat(readFile(failedSummaryPath)).contains("FAIL").contains("CUSTOM_SQL");

        ETLTaskDTO passedTask = createCustomSqlTask(
            "ETL自定义SQL质检通过",
            "SELECT COUNT(*) AS invalid_rows FROM dq_orders WHERE order_id < 0",
            "FULL"
        );
        taskIds.add(passedTask.getId());
        DataQualityCheckUnit passedUnit = extractQualityCheckUnit(jobContext(passedTask));
        JobInstance passedInstance = executeAndWait(passedTask);
        assertThat(passedInstance.getStatus()).isEqualTo(TaskConstants.TASK_STATUS_SUCCESSFUL);
        String passedSummaryPath = join(
            passedUnit.outputDir(),
            passedInstance.getInstanceCode(),
            passedUnit.resultPrefix() + "_summary.csv"
        );
        assertThat(readFile(passedSummaryPath)).contains("PASS").contains("CUSTOM_SQL");
    }

    private String createDuckDbSource() throws Exception {
        Path sourceFile = tempDir.resolve("etl-dq-source-" + System.currentTimeMillis() + ".duckdb");
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:" + sourceFile.toAbsolutePath())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                    "CREATE TABLE dq_orders (order_id INTEGER, customer_name VARCHAR, amount DOUBLE);" +
                    "INSERT INTO dq_orders VALUES (1, 'Alice', 10), (2, NULL, 20), (3, 'Bob', 30);"
                );
            }
        }
        DataSourceDTO dto = new DataSourceDTO();
        dto.setName("etl-dq-source-" + System.currentTimeMillis());
        dto.setType("DUCKDB");
        dto.setUrl("jdbc:duckdb:" + sourceFile.toAbsolutePath());
        dto.setSchemaName("main");
        dto.setConnectionMode("database");
        return dataSourceService.save(dto).getId();
    }

    private ETLTaskDTO createTask(String taskName, String checkColumn, String checkMode) throws Exception {
        Map<String, Object> inputConfig = new LinkedHashMap<>();
        inputConfig.put("sourceId", dataSourceId);
        inputConfig.put("schema", "main");
        inputConfig.put("table", "dq_orders");
        inputConfig.put("where", "");
        inputConfig.put("incrColumn", "");

        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("ruleCode", "ETL_DQ_NOT_NULL_" + System.currentTimeMillis());
        rule.put("ruleName", "客户名非空");
        rule.put("ruleType", "NOT_NULL");
        rule.put("column", checkColumn);
        rule.put("severity", "ERROR");
        rule.put("maxInvalidRows", 0);
        rule.put("maxInvalidRatio", 0);
        rule.put("config", Map.of("trimWhitespace", true));

        Map<String, Object> checkConfig = new LinkedHashMap<>();
        checkConfig.put("checkMode", checkMode);
        checkConfig.put("failOnError", true);
        checkConfig.put("rules", List.of(rule));

        ETLNodeDTO input = node("输入", "input_1", "StreamJdbcInput", inputConfig);
        ETLNodeDTO check = node("数据质量检核", "dq_check_1", "DataQualityCheck", checkConfig);
        ETLEdgeDTO edge = new ETLEdgeDTO();
        edge.setCode("edge_1");
        edge.setName("输入 -> 质量检核");
        edge.setSource("input_1");
        edge.setTarget("dq_check_1");
        edge.setConfig("{\"queueCapacity\":10}");

        ETLTaskDTO task = new ETLTaskDTO();
        task.setTaskName(taskName + "-" + System.currentTimeMillis());
        task.setTaskDesc("ETL 数据质量组件验证");
        task.setType(TaskConstants.ETL_TASK_KIND_BATCH);
        task.setStatus(TaskConstants.TASK_STATUS_OFFLINE);
        task.setNodes(List.of(input, check));
        task.setEdges(List.of(edge));
        return etlTaskService.save(task);
    }

    private ETLTaskDTO createCustomSqlTask(String taskName, String sql, String checkMode) throws Exception {
        Map<String, Object> inputConfig = new LinkedHashMap<>();
        inputConfig.put("sourceId", dataSourceId);
        inputConfig.put("schema", "main");
        inputConfig.put("table", "dq_orders");
        inputConfig.put("where", "");
        inputConfig.put("incrColumn", "");

        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("ruleCode", "ETL_DQ_CUSTOM_SQL_" + System.nanoTime());
        rule.put("ruleName", "自定义 SQL");
        rule.put("ruleType", "CUSTOM_SQL");
        rule.put("severity", "ERROR");
        rule.put("maxInvalidRows", 0);
        rule.put("maxInvalidRatio", 0);
        rule.put("config", Map.of("sql", sql));

        Map<String, Object> checkConfig = new LinkedHashMap<>();
        checkConfig.put("checkMode", checkMode);
        checkConfig.put("failOnError", true);
        checkConfig.put("rules", List.of(rule));

        ETLNodeDTO input = node("输入", "input_1", "StreamJdbcInput", inputConfig);
        ETLNodeDTO check = node("数据质量检核", "dq_check_1", "DataQualityCheck", checkConfig);
        ETLEdgeDTO edge = new ETLEdgeDTO();
        edge.setCode("edge_1");
        edge.setName("输入 -> 质量检核");
        edge.setSource("input_1");
        edge.setTarget("dq_check_1");
        edge.setConfig("{\"queueCapacity\":10}");

        ETLTaskDTO task = new ETLTaskDTO();
        task.setTaskName(taskName + "-" + System.currentTimeMillis());
        task.setTaskDesc("ETL 自定义 SQL 数据质量验证");
        task.setType(TaskConstants.ETL_TASK_KIND_BATCH);
        task.setStatus(TaskConstants.TASK_STATUS_OFFLINE);
        task.setNodes(List.of(input, check));
        task.setEdges(List.of(edge));
        return etlTaskService.save(task);
    }

    private ETLNodeDTO node(String label, String code, String type, Map<String, Object> config) throws Exception {
        ETLNodeDTO node = new ETLNodeDTO();
        node.setLabel(label);
        node.setCode(code);
        node.setType(type);
        node.setConfig(objectMapper.writeValueAsString(config));
        node.setxAxis("0");
        node.setyAxis("0");
        return node;
    }

    private JobInstance executeAndWait(ETLTaskDTO task) throws Exception {
        etlTaskService.executeOnce(task.getId());
        for (int i = 0; i < 120; i++) {
            TenantContext.setTenantId(0L);
            TenantContext.setTenantCode("default");
            JobInstance instance = jobInstanceRepository.findFirstByJobCodeOrderByCreateTimeDesc(task.getJobId()).orElse(null);
            if (instance != null && !TaskConstants.TASK_STATUS_APPENDING.equals(instance.getStatus()) && !TaskConstants.TASK_STATUS_RUNNING.equals(instance.getStatus())) {
                return instance;
            }
            Thread.sleep(500);
        }
        fail("ETL 数据质量任务执行超时: " + task.getTaskName());
        return null;
    }

    private String jobContext(ETLTaskDTO task) {
        return jobService
            .findOneJob(task.getJobId())
            .orElseThrow(() -> new IllegalStateException("ETL 任务 Job 不存在: " + task.getJobId()))
            .getJobContext();
    }

    private DataQualityCheckUnit extractQualityCheckUnit(String jobContext) throws Exception {
        JsonNode context = objectMapper.readTree(jobContext);
        for (JsonNode unit : context.get("units")) {
            if ("DataQualityCheck".equals(unit.path(".name").asText())) {
                return new DataQualityCheckUnit(
                    unit.path("checkMode").asText(),
                    unit.path("outputDir").asText(),
                    unit.path("resultPrefix").asText(),
                    unit.path("outputFormat").asText(),
                    unit.path("failOnWarn").asBoolean(),
                    unit.path("sampleLimit").asInt(),
                    unit.path("storageType").asText(),
                    unit.path("tenantPrefix").asText()
                );
            }
        }
        throw new AssertionError("JobContext 中未找到 DataQualityCheck 组件");
    }

    private String readFile(String path) throws Exception {
        try (InputStream inputStream = fileStorageStrategy.download(path)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String join(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('/');
            }
            builder.append(part.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", ""));
        }
        return builder.toString();
    }

    private record DataQualityCheckUnit(
        String checkMode,
        String outputDir,
        String resultPrefix,
        String outputFormat,
        boolean failOnWarn,
        int sampleLimit,
        String storageType,
        String tenantPrefix
    ) {}
}
