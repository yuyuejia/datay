package com.data.datasource.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.DatasourceInfo;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Apache Polaris 端到端测试：需要本地真实的 Polaris + S3（如 MinIO/moto）环境。
 *
 * <p>默认跳过。运行方式：</p>
 * <pre>
 * POLARIS_E2E_URL=polaris://127.0.0.1:8181/quickstart_catalog \
 * POLARIS_E2E_USER=root POLARIS_E2E_PASSWORD=secret \
 * POLARIS_E2E_SCHEMA=e2e \
 * POLARIS_E2E_S3_KEY=admin POLARIS_E2E_S3_SECRET=password \
 * POLARIS_E2E_S3_ENDPOINT=http://127.0.0.1:9100 \
 * mvn -pl datay-core -Dtest=PolarisEndToEndTest test
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "POLARIS_E2E_URL", matches = "polaris://.+")
class PolarisEndToEndTest {

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static DatasourceInfo datasourceInfo() {
        DatasourceInfo info = new DatasourceInfo();
        info.setType("POLARIS");
        info.setUrl(env("POLARIS_E2E_URL", null));
        info.setUsername(env("POLARIS_E2E_USER", "root"));
        info.setPassword(env("POLARIS_E2E_PASSWORD", "secret"));
        info.setDbschema(env("POLARIS_E2E_SCHEMA", "e2e"));
        Map<String, String> extraParams = new HashMap<>();
        extraParams.put("polaris.access_delegation_mode", env("POLARIS_E2E_DELEGATION", "none"));
        extraParams.put("s3.key_id", env("POLARIS_E2E_S3_KEY", "admin"));
        extraParams.put("s3.secret", env("POLARIS_E2E_S3_SECRET", "password"));
        extraParams.put("s3.endpoint", env("POLARIS_E2E_S3_ENDPOINT", "http://127.0.0.1:9100"));
        extraParams.put("s3.url_style", "path");
        extraParams.put("s3.use_ssl", "false");
        info.setExtraParams(extraParams);
        return info;
    }

    private static TableMeta ordersTable(String schema, String table) {
        TableMeta meta = new TableMeta(table);
        meta.setCatalog(DBUtils.POLARIS_CATALOG);
        meta.setSchema(schema);
        meta.setDbType("POLARIS");
        ColumnMeta id = new ColumnMeta("id", "INTEGER");
        id.setNullable(false);
        meta.addColumn(id);
        ColumnMeta name = new ColumnMeta("name", "VARCHAR");
        meta.addColumn(name);
        ColumnMeta amount = new ColumnMeta("amount", "DECIMAL");
        amount.setPrecision(18);
        amount.setScale(2);
        meta.addColumn(amount);
        return meta;
    }

    private static JSONObject row(int id, String name, String amount) {
        JSONObject row = new JSONObject();
        row.put("id", id);
        row.put("name", name);
        row.put("amount", new BigDecimal(amount));
        return row;
    }

    private static int queryCount(Connection conn, String schema, String table) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT COUNT(*) FROM \"" + DBUtils.POLARIS_CATALOG + "\".\"" + schema + "\".\"" + table + "\""
             )) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    @Test
    void readWriteThroughPolarisProvider() throws Exception {
        DatasourceInfo info = datasourceInfo();
        String schema = info.getDbschema();
        String table = "e2e_orders";

        JdbcDataSourceProvider provider = DBUtils.resolveJdbcProvider(info);
        assertNotNull(provider);
        assertTrue(provider instanceof PolarisDataSourceProvider, "应解析为 PolarisDataSourceProvider");

        try (Connection conn = provider.openConnection(info)) {
            assertEquals(List.of(DBUtils.POLARIS_CATALOG), provider.getCatalogs(conn));

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(
                    "DROP TABLE IF EXISTS \"" + DBUtils.POLARIS_CATALOG + "\".\"" + schema + "\".\"" + table + "\""
                );
            } catch (SQLException ignored) {
                // 首次运行时表不存在
            }

            // 1. 自动创建 namespace
            provider.prepareTableCreate(info, conn, DBUtils.POLARIS_CATALOG, schema);

            // 2. 按 Polaris 转换器生成 DDL 并建表
            String ddl = DatabaseConverter.generateTableDDL("POLARIS", ordersTable(schema, table));
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(ddl);
            }
            assertTrue(provider.tableExists(conn, schema, table), "建表后 tableExists 应为 true");

            // 3. 元数据读取（SHOW ALL TABLES）
            List<TableMeta> tables = provider.getTables(conn, DBUtils.POLARIS_CATALOG, schema, null, null);
            assertTrue(tables.stream().anyMatch(t -> table.equalsIgnoreCase(t.getTable())), "表清单应包含新建表");
            TableMeta meta = provider.getTableMeta(conn, DBUtils.POLARIS_CATALOG, schema, table);
            assertEquals(3, meta.columns().size(), "列数应为 3");
            assertEquals("INTEGER", meta.columns().get(0).getType());
            assertEquals("VARCHAR", meta.columns().get(1).getType());
            assertTrue(meta.columns().get(2).getType().startsWith("DECIMAL"), meta.columns().get(2).getType());

            // 4. INSERT
            JSONArray inserts = new JSONArray();
            inserts.add(row(1, "alpha", "10.50"));
            inserts.add(row(2, "beta", "20.00"));
            inserts.add(row(3, "gamma", "30.25"));
            assertTrue(provider.supportsTableWrite(info, meta, "INSERT"));
            provider.writeTable(info, conn, meta, inserts, "INSERT", null);
            assertEquals(3, queryCount(conn, schema, table));

            // 5. UPDATE（带 __before 前镜像）
            JSONObject update = row(2, "beta-updated", "20.00");
            JSONObject before = row(2, "beta", "20.00");
            update.put("__before", before);
            JSONArray updates = new JSONArray();
            updates.add(update);
            assertTrue(provider.supportsTableWrite(info, meta, "UPDATE"));
            provider.writeTable(info, conn, meta, updates, "UPDATE", null);
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM \"" + DBUtils.POLARIS_CATALOG + "\".\"" + schema + "\".\"" + table +
                     "\" WHERE id = 2"
                 )) {
                assertTrue(rs.next());
                assertEquals("beta-updated", rs.getString(1));
            }

            // 6. DELETE（带 __before 前镜像）
            JSONObject delete = new JSONObject();
            delete.put("__before", row(3, "gamma", "30.25"));
            JSONArray deletes = new JSONArray();
            deletes.add(delete);
            assertTrue(provider.supportsTableWrite(info, meta, "DELETE"));
            provider.writeTable(info, conn, meta, deletes, "DELETE", null);
            assertEquals(2, queryCount(conn, schema, table));

            // 7. OVERWRITE：清空后写入
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(
                    "DELETE FROM \"" + DBUtils.POLARIS_CATALOG + "\".\"" + schema + "\".\"" + table + "\""
                );
            }
            JSONArray overwrite = new JSONArray();
            overwrite.add(row(9, "omega", "99.99"));
            provider.writeTable(info, conn, meta, overwrite, "OVERWRITE", null);
            assertEquals(1, queryCount(conn, schema, table));

            // 8. 清理
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(
                    "DROP TABLE IF EXISTS \"" + DBUtils.POLARIS_CATALOG + "\".\"" + schema + "\".\"" + table + "\""
                );
            }
            assertFalse(provider.tableExists(conn, schema, table));
        }
    }
}
