package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.JobInstanceRepository;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.FileStorageConfigService.FileStorageConfig;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.MetadataCollectRunDTO;
import com.data.datafusion.service.dto.MetadataCollectTaskDTO;
import com.data.file.FileStorageStrategy;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * 元数据采集任务端到端验证：配置 -> 生成 ETL Job -> 执行 -> 写入文件存储。
 */
@IntegrationTest
@WithMockUser
class MetadataCollectExecutionIT {

    @Autowired
    private MetadataCollectTaskService metadataCollectTaskService;

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

    private String dataSourceId;

    private String taskId;

    private FileStorageConfig originalStorageConfig;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(0L);
        TenantContext.setTenantCode("default");
        originalStorageConfig = fileStorageConfigService.getConfig();
        FileStorageConfig config = new FileStorageConfig();
        config.type = "local";
        config.localBasePath = tempDir.resolve("files").toString();
        fileStorageConfigService.updateAndReload(config);
    }

    @AfterEach
    void tearDown() {
        TenantContext.setTenantId(0L);
        TenantContext.setTenantCode("default");
        if (taskId != null) {
            try {
                metadataCollectTaskService.delete(taskId);
            } catch (Exception ignored) {
                // ignore cleanup failure
            }
        }
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
    void shouldExecuteMetadataCollectTaskAndWriteLocalFiles() throws Exception {
        dataSourceId = createDuckDbSource();

        MetadataCollectTaskDTO dto = new MetadataCollectTaskDTO();
        dto.setDataSourceId(dataSourceId);
        dto.setTaskName("元数据采集端到端验证");
        dto.setSchemaNames(java.util.List.of("analytics"));
        dto.setEnabled(false);
        MetadataCollectTaskDTO saved = metadataCollectTaskService.save(dto);
        taskId = saved.getId();

        MetadataCollectRunDTO run = metadataCollectTaskService.execute(taskId);
        assertThat(run.getId()).isNotBlank();

        JobInstance instance = waitForInstance(saved.getJobId());
        assertThat(instance.getStatus()).isEqualTo(TaskConstants.TASK_STATUS_SUCCESSFUL);

        String outputDir = String.join(
            "/",
            "tenants/default/metadata",
            dataSourceId,
            instance.getInstanceCode()
        );
        String tablesPath = outputDir + "/tables.csv";
        String columnsPath = outputDir + "/columns.csv";
        assertThat(fileStorageStrategy.exists(tablesPath)).isTrue();
        assertThat(fileStorageStrategy.exists(columnsPath)).isTrue();
        assertThat(readFile(tablesPath)).contains("orders", "customers", "analytics");
        assertThat(readFile(columnsPath)).contains("order_id", "amount", "customer_name");

        assertThat(fileStorageStrategy.exists("tenants/default/metadata/" + dataSourceId + "/latest/tables.csv")).isTrue();
        assertThat(fileStorageStrategy.exists("tenants/default/metadata/" + dataSourceId + "/latest/columns.csv")).isTrue();
    }

    private String createDuckDbSource() throws Exception {
        Path sourceFile = tempDir.resolve("metadata-source-" + System.currentTimeMillis() + ".duckdb");
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:" + sourceFile.toAbsolutePath())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA analytics");
                statement.execute("CREATE TABLE analytics.orders (order_id INTEGER PRIMARY KEY, amount DECIMAL(18,2), customer_name VARCHAR)");
                statement.execute("CREATE TABLE analytics.customers (customer_id INTEGER, customer_name VARCHAR)");
            }
        }
        DataSourceDTO dto = new DataSourceDTO();
        dto.setName("metadata-source-" + System.currentTimeMillis());
        dto.setType("DUCKDB");
        dto.setUrl("jdbc:duckdb:" + sourceFile.toAbsolutePath());
        dto.setSchemaName("analytics");
        return dataSourceService.save(dto).getId();
    }

    private JobInstance waitForInstance(String jobId) throws Exception {
        for (int i = 0; i < 120; i++) {
            TenantContext.setTenantId(0L);
            TenantContext.setTenantCode("default");
            JobInstance instance = jobInstanceRepository.findFirstByJobCodeOrderByCreateTimeDesc(jobId).orElse(null);
            if (
                instance != null &&
                !TaskConstants.TASK_STATUS_APPENDING.equals(instance.getStatus()) &&
                !TaskConstants.TASK_STATUS_RUNNING.equals(instance.getStatus())
            ) {
                return instance;
            }
            Thread.sleep(500);
        }
        fail("元数据采集任务执行超时");
        return null;
    }

    private String readFile(String path) throws Exception {
        try (InputStream inputStream = fileStorageStrategy.download(path)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
