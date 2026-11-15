package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.job.DatasourceInfo;
import com.data.job.DuckDBEngine;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetadataCollectTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCollectTableAndColumnMetadataToLocalFiles() throws Exception {
        Path sourceFile = tempDir.resolve("metadata-source.duckdb");
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:" + sourceFile.toAbsolutePath())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA analytics");
                statement.execute("CREATE TABLE analytics.orders (order_id INTEGER PRIMARY KEY, amount DECIMAL(18,2), customer_name VARCHAR)");
                statement.execute("CREATE TABLE analytics.customers (customer_id INTEGER, customer_name VARCHAR)");
            }
        }

        String instanceCode = "metadata-test-" + System.currentTimeMillis();
        DatasourceInfo datasource = new DatasourceInfo();
        datasource.setType("DUCKDB");
        datasource.setUrl("jdbc:duckdb:" + sourceFile.toAbsolutePath());
        datasource.setDbschema("analytics");

        MetadataCollect component = new MetadataCollect();
        component.setId("metadata_collect");
        component.setName("MetadataCollect");
        component.setDatasource(datasource);
        component.setSchemaNames(List.of("analytics"));
        component.setDataSourceId("ds-1");
        component.setDataSourceName("测试数据源");
        component.setOutputDir("metadata");
        component.setStorageType("local");
        component.setLocalBasePath(tempDir.resolve("files").toString());
        component.setTenantPrefix("tenants/default");

        ExecutionContext context = new ExecutionContext();
        context.setJobCode("job-1");
        context.setJobInstanceCode(instanceCode);
        component.setContext(context);
        component.setTaskLogger(new TaskLogger("job-1", instanceCode));

        try {
            component.execute(new FlowFile());

            Path outputDir = tempDir.resolve("files/tenants/default/metadata/ds-1/" + instanceCode);
            String tables = Files.readString(outputDir.resolve("tables.csv"), StandardCharsets.UTF_8);
            String columns = Files.readString(outputDir.resolve("columns.csv"), StandardCharsets.UTF_8);

            assertTrue(tables.contains("table_name") && tables.contains("orders") && tables.contains("customers") && tables.contains("analytics"));
            assertTrue(columns.contains("column_name") && columns.contains("order_id") && columns.contains("amount") && columns.contains("customer_name"));
            Path latestDir = tempDir.resolve("files/tenants/default/metadata/ds-1/latest");
            assertTrue(Files.exists(latestDir.resolve("tables.csv")));
            assertTrue(Files.exists(latestDir.resolve("columns.csv")));
        } finally {
            DuckDBEngine.deleteDBFile(instanceCode);
        }
    }
}
