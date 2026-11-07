package com.data.datafusion.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.JobInstanceRepository;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.DataQualityRuleService;
import com.data.datafusion.service.DataQualityTaskService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.FileStorageConfigService;
import com.data.datafusion.service.FileStorageConfigService.FileStorageConfig;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * REST 层端到端验证：规则新建/更新、任务创建、立即执行、结果查询与下载。
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class DataQualityResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private DataQualityRuleService ruleService;

    @Autowired
    private DataQualityTaskService taskService;

    @Autowired
    private JobInstanceRepository jobInstanceRepository;

    @Autowired
    private FileStorageConfigService fileStorageConfigService;

    @TempDir
    Path tempDir;

    private FileStorageConfig originalStorageConfig;

    private String taskId;

    private String ruleId;

    private String dataSourceId;

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
        try {
            if (taskId != null) {
                taskService.delete(taskId);
            }
        } catch (Exception ignored) {
            // ignore cleanup failure
        }
        try {
            if (ruleId != null) {
                ruleService.delete(ruleId);
            }
        } catch (Exception ignored) {
            // ignore cleanup failure
        }
        try {
            if (dataSourceId != null) {
                dataSourceService.delete(dataSourceId);
            }
        } catch (Exception ignored) {
            // ignore cleanup failure
        }
        if (originalStorageConfig != null) {
            fileStorageConfigService.updateAndReload(originalStorageConfig);
        }
        TenantContext.clear();
    }

    @Test
    void shouldCreateRuleAndExecuteTaskThroughRestApi() throws Exception {
        dataSourceId = createDuckDbSource();

        Map<String, Object> rulePayload = new LinkedHashMap<>();
        rulePayload.put("ruleName", "REST订单客户名非空");
        rulePayload.put("ruleCode", "REST_DQ_NOT_NULL_" + System.currentTimeMillis());
        rulePayload.put("ruleType", "NOT_NULL");
        rulePayload.put("description", "REST 新建规则");
        rulePayload.put("defaultConfig", "{\"trimWhitespace\":true}");
        rulePayload.put("severity", "ERROR");
        rulePayload.put("enabled", true);

        MvcResult createRuleResult = mockMvc
            .perform(
                post("/api/data-quality-rules")
                    .with(tenant())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(rulePayload))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.builtin").value(false))
            .andExpect(jsonPath("$.ruleLevel").value("FIELD"))
            .andReturn();

        JsonNode createdRule = objectMapper.readTree(createRuleResult.getResponse().getContentAsString());
        ruleId = createdRule.get("id").asText();

        rulePayload.put("id", ruleId);
        rulePayload.put("description", "REST 更新规则");
        mockMvc
            .perform(
                put("/api/data-quality-rules/{id}", ruleId)
                    .with(tenant())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(rulePayload))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("REST 更新规则"));

        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("ruleId", ruleId);
        binding.put("column", "customer_name");
        binding.put("severity", "ERROR");
        binding.put("maxInvalidRows", 0);
        binding.put("maxInvalidRatio", 0);

        Map<String, Object> taskPayload = new LinkedHashMap<>();
        taskPayload.put("taskName", "REST质检失败任务-" + System.currentTimeMillis());
        taskPayload.put("taskDesc", "REST 端到端验证");
        taskPayload.put("dataSourceId", dataSourceId);
        taskPayload.put("schemaName", "main");
        taskPayload.put("tableName", "dq_orders");
        taskPayload.put("failOnWarn", false);
        taskPayload.put("sampleLimit", 10);
        taskPayload.put("outputFormat", "CSV");
        taskPayload.put("outputDir", "data-quality-rest");
        taskPayload.put("rules", java.util.List.of(binding));

        MvcResult createTaskResult = mockMvc
            .perform(
                post("/api/data-quality-tasks")
                    .with(tenant())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(taskPayload))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.jobId").isNotEmpty())
            .andExpect(jsonPath("$.rules[0].ruleName").value("REST订单客户名非空"))
            .andReturn();

        JsonNode createdTask = objectMapper.readTree(createTaskResult.getResponse().getContentAsString());
        taskId = createdTask.get("id").asText();

        MvcResult executeResult = mockMvc
            .perform(post("/api/data-quality-tasks/{id}/execute", taskId).with(tenant()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andReturn();
        String instanceId = objectMapper.readTree(executeResult.getResponse().getContentAsString()).get("id").asText();

        JobInstance instance = waitForTerminal(instanceId);
        assertThat(instance.getStatus()).isEqualTo(TaskConstants.TASK_STATUS_FAILED);

        mockMvc
            .perform(get("/api/data-quality-results/by-instance-id/{id}", instanceId).with(tenant()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overallStatus").value("FAIL"))
            .andExpect(jsonPath("$.failRules").value(1))
            .andExpect(jsonPath("$.summaryPath").isNotEmpty());

        mockMvc
            .perform(get("/api/data-quality-results/by-instance-id/{id}/download", instanceId).with(tenant()))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("FAIL")));
    }

    private String createDuckDbSource() throws Exception {
        Path sourceFile = tempDir.resolve("rest-source-" + System.currentTimeMillis() + ".duckdb");
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:" + sourceFile.toAbsolutePath())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                    "CREATE TABLE dq_orders (order_id INTEGER, customer_name VARCHAR, amount DOUBLE);" +
                    "INSERT INTO dq_orders VALUES (1, 'Alice', 10), (2, NULL, 20), (3, 'Bob', 30);"
                );
            }
        }
        DataSourceDTO dto = new DataSourceDTO();
        dto.setName("dq-rest-source-" + System.currentTimeMillis());
        dto.setType("DUCKDB");
        dto.setUrl("jdbc:duckdb:" + sourceFile.toAbsolutePath());
        dto.setSchemaName("main");
        dto.setConnectionMode("database");
        return dataSourceService.save(dto).getId();
    }

    private JobInstance waitForTerminal(String instanceId) throws Exception {
        for (int i = 0; i < 120; i++) {
            TenantContext.setTenantId(0L);
            TenantContext.setTenantCode("default");
            JobInstance instance = jobInstanceRepository.findById(instanceId).orElse(null);
            if (instance != null && !TaskConstants.TASK_STATUS_APPENDING.equals(instance.getStatus()) && !TaskConstants.TASK_STATUS_RUNNING.equals(instance.getStatus())) {
                return instance;
            }
            Thread.sleep(500);
        }
        fail("数据质量任务执行超时: " + instanceId);
        return null;
    }

    private RequestPostProcessor tenant() {
        return request -> {
            TenantContext.setTenantId(0L);
            TenantContext.setTenantCode("default");
            return request;
        };
    }
}
