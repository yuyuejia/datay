package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.JobInstanceRepository;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.FileStorageConfigService.FileStorageConfig;
import com.data.datafusion.service.dto.DataQualityResultDTO;
import com.data.datafusion.service.dto.DataQualityRuleBindingDTO;
import com.data.datafusion.service.dto.DataQualityRuleDTO;
import com.data.datafusion.service.dto.DataQualityTaskDTO;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.file.FileStorageStrategy;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * 端到端验证：新建质量规则 -> 创建检核任务 -> 立即执行 -> 写入结果文件。
 */
@IntegrationTest
@WithMockUser
class DataQualityExecutionIT {

    @Autowired
    private DataQualityRuleService ruleService;

    @Autowired
    private DataQualityTaskService taskService;

    @Autowired
    private DataQualityResultService resultService;

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private JobInstanceRepository jobInstanceRepository;

    @Autowired
    private FileStorageConfigService fileStorageConfigService;

    @Autowired
    private FileStorageStrategy fileStorageStrategy;

    @TempDir
    Path tempDir;

    private final List<String> taskIds = new ArrayList<>();

    private final List<String> ruleIds = new ArrayList<>();

    private final List<String> dataSourceIds = new ArrayList<>();

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
        taskIds.forEach(id -> {
            try {
                taskService.delete(id);
            } catch (Exception ignored) {
                // ignore cleanup failure
            }
        });
        ruleIds.forEach(id -> {
            try {
                ruleService.delete(id);
            } catch (Exception ignored) {
                // ignore cleanup failure
            }
        });
        dataSourceIds.forEach(id -> {
            try {
                dataSourceService.delete(id);
            } catch (Exception ignored) {
                // ignore cleanup failure
            }
        });
        if (originalStorageConfig != null) {
            fileStorageConfigService.updateAndReload(originalStorageConfig);
        }
        TenantContext.clear();
    }

    @Test
    void shouldCreateRuleAndExecuteTaskWithFailAndPassResults() throws Exception {
        DataSourceDTO dataSource = createDuckDbSource();
        DataQualityRuleDTO rule = createRule();
        DataQualityTaskDTO failTask = createTask(dataSource, rule, "质检失败任务", "customer_name", "ERROR");
        DataQualityTaskDTO passTask = createTask(dataSource, rule, "质检通过任务", "order_id", "ERROR");

        DataQualityResultDTO failResult = executeAndWait(failTask.getId());
        assertThat(failResult.getOverallStatus()).isEqualTo("FAIL");
        assertThat(failResult.getFailRules()).isGreaterThanOrEqualTo(1);
        assertThat(failResult.getSummaryPath()).isNotBlank();
        assertThat(fileStorageStrategy.exists(failResult.getSummaryPath())).isTrue();
        assertThat(readFile(failResult.getSummaryPath())).contains("FAIL").contains("customer_name");
        assertThat(fileStorageStrategy.list(join(failResult.getOutputDir(), failResult.getInstanceCode())))
            .anyMatch(file -> file.name().contains("_detail_"));

        DataQualityResultDTO passResult = executeAndWait(passTask.getId());
        assertThat(passResult.getOverallStatus()).isEqualTo("PASS");
        assertThat(passResult.getFailRules()).isZero();
        assertThat(readFile(passResult.getSummaryPath())).contains("PASS").contains("order_id");
    }

    private DataSourceDTO createDuckDbSource() throws Exception {
        Path sourceFile = tempDir.resolve("source-" + System.currentTimeMillis() + ".duckdb");
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:" + sourceFile.toAbsolutePath())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                    "CREATE TABLE dq_orders (order_id INTEGER, customer_name VARCHAR, amount DOUBLE);" +
                    "INSERT INTO dq_orders VALUES (1, 'Alice', 10), (2, NULL, 20), (3, 'Bob', 30);"
                );
            }
        }

        DataSourceDTO dto = new DataSourceDTO();
        dto.setName("dq-it-source-" + System.currentTimeMillis());
        dto.setType("DUCKDB");
        dto.setUrl("jdbc:duckdb:" + sourceFile.toAbsolutePath());
        dto.setSchemaName("main");
        dto.setConnectionMode("database");
        DataSourceDTO saved = dataSourceService.save(dto);
        dataSourceIds.add(saved.getId());
        return saved;
    }

    private DataQualityRuleDTO createRule() {
        DataQualityRuleDTO dto = new DataQualityRuleDTO();
        dto.setRuleName("订单客户名非空");
        dto.setRuleCode("IT_DQ_NOT_NULL_" + System.currentTimeMillis());
        dto.setRuleType("NOT_NULL");
        dto.setDescription("验证规则新建");
        dto.setDefaultConfig("{\"trimWhitespace\":true}");
        dto.setSeverity("ERROR");
        dto.setEnabled(Boolean.TRUE);
        DataQualityRuleDTO created = ruleService.save(dto);
        ruleIds.add(created.getId());

        assertThat(created.getId()).isNotBlank();
        assertThat(created.getBuiltin()).isFalse();
        assertThat(created.getRuleLevel()).isEqualTo("FIELD");
        assertThat(created.getRuleType()).isEqualTo("NOT_NULL");

        created.setDescription("验证规则更新");
        DataQualityRuleDTO updated = ruleService.update(created);
        assertThat(updated.getDescription()).isEqualTo("验证规则更新");
        return updated;
    }

    private DataQualityTaskDTO createTask(DataSourceDTO dataSource, DataQualityRuleDTO rule, String name, String column, String severity) {
        DataQualityRuleBindingDTO binding = new DataQualityRuleBindingDTO();
        binding.setRuleId(rule.getId());
        binding.setColumn(column);
        binding.setSeverity(severity);
        binding.setMaxInvalidRows(0L);
        binding.setMaxInvalidRatio(0.0D);

        DataQualityTaskDTO dto = new DataQualityTaskDTO();
        dto.setTaskName(name + "-" + System.currentTimeMillis());
        dto.setTaskDesc("端到端验证任务");
        dto.setDataSourceId(dataSource.getId());
        dto.setSchemaName("main");
        dto.setTableName("dq_orders");
        dto.setFailOnWarn(Boolean.FALSE);
        dto.setSampleLimit(10);
        dto.setOutputFormat("CSV");
        dto.setOutputDir("data-quality-it");
        dto.setRules(List.of(binding));
        DataQualityTaskDTO saved = taskService.save(dto);
        taskIds.add(saved.getId());
        assertThat(saved.getJobId()).isNotBlank();
        assertThat(saved.getRules()).hasSize(1);
        return saved;
    }

    private DataQualityResultDTO executeAndWait(String taskId) throws Exception {
        DataQualityResultDTO submitted = taskService.execute(taskId);
        assertThat(submitted.getId()).isNotBlank();
        JobInstance instance = waitForTerminal(submitted.getId());
        assertThat(instance.getStatus()).isIn(TaskConstants.TASK_STATUS_SUCCESSFUL, TaskConstants.TASK_STATUS_FAILED);
        return resultService.findByJobInstanceId(instance.getId()).orElseThrow();
    }

    private JobInstance waitForTerminal(String instanceId) throws InterruptedException {
        for (int i = 0; i < 120; i++) {
            JobInstance instance = jobInstanceRepository.findById(instanceId).orElse(null);
            if (instance != null && !TaskConstants.TASK_STATUS_APPENDING.equals(instance.getStatus()) && !TaskConstants.TASK_STATUS_RUNNING.equals(instance.getStatus())) {
                return instance;
            }
            Thread.sleep(500);
        }
        fail("数据质量任务执行超时: " + instanceId);
        return null;
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
}
