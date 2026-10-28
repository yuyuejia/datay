package com.data.metadata.util;

import com.data.datasource.provider.DuckLakeDataSourceProvider;
import com.data.job.DatasourceInfo;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归验证：DuckLake 数据源必须挂载为 DuckLake catalog，表清单只包含用户表，
 * 不能退化为普通 DuckDB 库而暴露 ducklake_* 元数据表。
 */
public class DuckLakeAttachTest {

    private static String[] createDuckLake() throws Exception {
        Path base = Files.createTempDirectory("ducklake-attach");
        String meta = base.resolve("m.ducklake").toString();
        String data = base.resolve("data").toString();

        File creator = File.createTempFile("ducklake_attach_creator", ".duckdb");
        creator.delete();
        creator.deleteOnExit();

        try (Connection c = DriverManager.getConnection("jdbc:duckdb:" + creator.getAbsolutePath())) {
            Statement st = c.createStatement();
            try {
                st.execute("INSTALL ducklake");
                st.execute("LOAD ducklake");
                st.execute("ATTACH 'ducklake:" + meta + "' AS ducklake (data_path '" + data + "')");
            } catch (Exception e) {
                Assumptions.assumeTrue(false, "DuckLake 扩展不可用，跳过: " + e.getMessage());
            }
            st.execute("CREATE TABLE ducklake.main.orders (id int)");
            st.close();
        }
        return new String[] { meta, data };
    }

    private static DatasourceInfo duckLakeInfo(String meta, String data, String username, String password) {
        DatasourceInfo info = new DatasourceInfo();
        info.setType("DUCKLAKE");
        info.setUrl("ducklake:" + meta);
        info.setUsername(username);
        info.setPassword(password);
        Map<String, String> params = new HashMap<>();
        params.put("s3.data_path", data);
        info.setExtraParams(params);
        return info;
    }

    @Test
    public void listsUserTablesNotMetadataTables() throws Exception {
        String[] meta = createDuckLake();

        try (Connection conn = DBUtils.getConnection(duckLakeInfo(meta[0], meta[1], null, null))) {
            List<TableMeta> tables = DBUtils.getTableList(conn, "main");
            assertTrue(tables.stream().anyMatch(t -> "orders".equals(t.getTable())), "应包含用户表 orders");
            assertFalse(
                tables.stream().anyMatch(t -> t.getTable() != null && t.getTable().startsWith("ducklake_")),
                "不应暴露 ducklake_* 元数据表"
            );
        }
    }

    @Test
    public void exposesDuckLakeAndAttachedCatalogs() throws Exception {
        String[] meta = createDuckLake();

        Path attachedDb = Files.createTempDirectory("ducklake-attached").resolve("attached.duckdb");
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:" + attachedDb); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE attached_orders (id int)");
        }

        try (Connection conn = DBUtils.getConnection(duckLakeInfo(meta[0], meta[1], null, null))) {
            String scratchCatalog = conn.getCatalog();
            try (Statement st = conn.createStatement()) {
                st.execute("ATTACH '" + attachedDb.toString().replace("'", "''") + "' AS attached_db");
            }

            DuckLakeDataSourceProvider provider = new DuckLakeDataSourceProvider();
            List<String> catalogs = provider.getCatalogs(conn);

            assertTrue(catalogs.contains("ducklake"), "应包含 DuckLake catalog");
            assertTrue(catalogs.contains("attached_db"), "应包含额外挂载的 catalog");
            assertFalse(
                catalogs.stream().anyMatch(c -> c.startsWith("__ducklake_metadata_")),
                "不应展示 DuckLake 内部元数据 catalog"
            );
            if (scratchCatalog != null) {
                assertFalse(catalogs.contains(scratchCatalog), "不应展示 DuckLake 本地 scratch catalog");
            }

            assertTrue(provider.getSchemas(conn, "attached_db").contains("main"));
            List<TableMeta> tables = provider.getTables(conn, "attached_db", "main", null, null);
            assertTrue(tables.stream().anyMatch(t -> "attached_orders".equals(t.getTable())));
        }
    }

    @Test
    public void differentDatasourceCredentialsDoNotConflict() throws Exception {
        String[] meta = createDuckLake();

        // 连接测试与查询可能携带不同的用户名（DuckLake 本地 scratch 库不应使用账号密码），
        // 修复前会以不同 user 配置打开同一 DuckDB 文件而报 "different configuration"
        try (Connection c = DBUtils.getConnection(duckLakeInfo(meta[0], meta[1], "admin", "123456"))) {
            assertNotNull(c);
        }
        try (Connection c = DBUtils.getConnection(duckLakeInfo(meta[0], meta[1], null, null))) {
            assertNotNull(c);
            assertTrue(DBUtils.getTableList(c, "main").stream().anyMatch(t -> "orders".equals(t.getTable())));
        }
    }
}
