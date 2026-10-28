package com.data.datafusion.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.service.dto.DataSourceDTO;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link DatabaseSchemaQueryTool} 单元测试，重点验证 catalog 优先的元数据下钻链路。
 */
class DatabaseSchemaQueryToolTest {

    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("unchecked")
    void shouldExposeCatalogInParametersSchema() {
        DatabaseSchemaQueryTool tool = new DatabaseSchemaQueryTool(null, null);

        Map<String, Object> properties = (Map<String, Object>) tool.parametersSchema().get("properties");
        Map<String, Object> objectType = (Map<String, Object>) properties.get("objectType");

        assertThat((List<String>) objectType.get("enum")).containsExactly("catalogs", "schemas", "tables", "columns");
        assertThat(properties).containsKey("catalog");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldListCatalogSchemasTablesAndColumns() throws Exception {
        Path database = tempDir.resolve("catalog_demo.duckdb");
        try (
            Connection connection = DriverManager.getConnection("jdbc:duckdb:" + database);
            Statement statement = connection.createStatement()
        ) {
            statement.execute("CREATE TABLE orders (id INTEGER, amount DECIMAL(10, 2))");
        }

        DataSourceDTO dataSource = new DataSourceDTO();
        dataSource.setType("DUCKDB");
        dataSource.setUrl("jdbc:duckdb:" + database);

        DatabaseSchemaQueryTool tool = new DatabaseSchemaQueryTool(null, null);
        AiToolContext context = new AiToolContext(null, dataSource, "查询订单", null);

        Map<String, Object> catalogs = (Map<String, Object>) tool.execute(Map.of("objectType", "catalogs"), context);
        List<String> catalogNames = (List<String>) catalogs.get("catalogs");
        assertThat(catalogNames).contains("catalog_demo");
        assertThat(catalogNames).doesNotContain("system", "temp");

        String catalog = "catalog_demo";
        Map<String, Object> schemas = (Map<String, Object>) tool.execute(Map.of("objectType", "schemas", "catalog", catalog), context);
        assertThat((List<String>) schemas.get("schemas")).contains("main");
        assertThat(schemas.get("catalog")).isEqualTo(catalog);

        Map<String, Object> tables = (Map<String, Object>) tool.execute(
            Map.of("objectType", "tables", "catalog", catalog, "schema", "main"),
            context
        );
        assertThat(tables.get("catalog")).isEqualTo(catalog);
        List<Map<String, Object>> tableList = (List<Map<String, Object>>) tables.get("tables");
        assertThat(tableList).anySatisfy(item -> assertThat(item).containsEntry("table", "orders").containsEntry("catalog", catalog));

        Map<String, Object> columns = (Map<String, Object>) tool.execute(
            Map.of("objectType", "columns", "catalog", catalog, "schema", "main", "table", "orders"),
            context
        );
        assertThat(columns.get("catalog")).isEqualTo(catalog);
        assertThat((List<?>) columns.get("columns")).hasSize(2);
    }
}
