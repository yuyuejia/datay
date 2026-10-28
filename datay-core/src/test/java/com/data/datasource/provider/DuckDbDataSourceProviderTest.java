package com.data.datasource.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.TableMeta;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * DuckDB catalog 元数据测试：ATTACH 后的库应出现在 catalog / schema / table 链路中。
 */
class DuckDbDataSourceProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void listsAttachedCatalogSchemasAndTables() throws Exception {
        Path attachedDb = tempDir.resolve("attached.db");
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + attachedDb); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE t1 (id INTEGER, name VARCHAR)");
            stmt.execute("CREATE SCHEMA s1");
            stmt.execute("CREATE TABLE s1.t2 (id INTEGER)");
        }

        DuckDbDataSourceProvider provider = new DuckDbDataSourceProvider();
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:"); Statement stmt = conn.createStatement()) {
            stmt.execute("ATTACH '" + attachedDb.toString().replace("'", "''") + "' AS other_db");

            List<String> catalogs = provider.getCatalogs(conn);
            assertTrue(catalogs.contains("other_db"), "应包含 ATTACH 后的 other_db");
            assertFalse(catalogs.contains("system"), "不应包含 system catalog");
            assertFalse(catalogs.contains("temp"), "不应包含 temp catalog");

            assertTrue(provider.getSchemas(conn, "other_db").contains("main"));
            assertTrue(provider.getSchemas(conn, "other_db").contains("s1"));

            List<TableMeta> tables = provider.getTables(conn, "other_db", "main", null, null);
            assertTrue(
                tables.stream().anyMatch(t -> "t1".equals(t.getTable()) && "other_db".equals(t.getCatalog())),
                "应读取 other_db.main.t1 并回填 catalog"
            );

            TableMeta meta = provider.getTableMeta(conn, "other_db", "main", "t1");
            assertEquals("other_db", meta.getCatalog());
            assertEquals(2, meta.columns().size());

            TableMeta detail = provider.getTableDetail(conn, "other_db", "main", "t1");
            assertEquals("other_db", detail.getCatalog());
            assertEquals("main", detail.getSchema());
        }
    }
}
