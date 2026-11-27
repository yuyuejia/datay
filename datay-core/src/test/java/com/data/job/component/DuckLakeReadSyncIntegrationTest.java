package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.job.DatasourceInfo;
import com.data.job.ETLFlowTask;
import com.data.metadata.util.DBUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * DuckLake 读取链路端到端验证（真实走 DAGParser + ETLFlowTask + 数据源 Provider）。
 *
 * <p>每个用例都会创建全新的 DuckLake 元数据，并删除 Provider 对应的本地 scratch 文件，
 * 模拟「新进程首次读取」：如果连接没有完成 INSTALL/LOAD ducklake、S3 Secret 与 ATTACH，
 * 首次执行就会失败。</p>
 *
 * <p>覆盖：</p>
 * <ol>
 *   <li>SQL 输入组件直接选择 DuckLake 数据源；</li>
 *   <li>数据源输入组件（StreamJdbcInput）选择 DuckLake 下的表；</li>
 *   <li>生成表SQL -> SQL 输入 的串联读取。</li>
 * </ol>
 */
class DuckLakeReadSyncIntegrationTest {

    private static final String DRIVER = "org.duckdb.DuckDBDriver";
    private static final int ROWS = 100;
    private static final long ID_SUM = 5050L;

    @Test
    @Timeout(180000)
    void sqlInputReadsDuckLakeOnFirstUse() throws Exception {
        DuckLake lake = createDuckLake("sql-input");
        String targetUrl = duckDbUrl("ducklake-read-sql-input.duckdb");
        deleteIfExists(targetUrl);

        String job = jobJson(
            "{\"sourceId\":\"sql\",\"targetId\":\"out\",\"sourcePort\":0}",
            "{" +
            "\".id\":\"sql\",\".name\":\"SqlInput\"," +
            "\"sql\":\"SELECT * FROM ducklake.demo.ods_records\"," +
            "\"sourceId\":" + duckLakeJson(lake) +
            "}",
            "{" +
            "\".id\":\"out\",\".name\":\"StreamJdbcOutput\",\"schema\":\"main\",\"table\":\"ods_records_sql\"," +
            "\"model\":\"overwrite\",\"sourceId\":" + duckDbDataSourceJson(targetUrl) +
            "}"
        );

        assertDoesNotThrow(() -> newTask("ducklake-sql-input-" + System.nanoTime()).runJob(job), "SQL 输入首次读取 DuckLake 应成功");
        assertSynced(targetUrl, "main.ods_records_sql");
    }

    @Test
    @Timeout(180000)
    void streamJdbcInputQualifiesDuckLakeCatalog() throws Exception {
        DuckLake lake = createDuckLake("stream-input");
        String targetUrl = duckDbUrl("ducklake-read-stream-input.duckdb");
        deleteIfExists(targetUrl);

        String job = jobJson(
            "{\"sourceId\":\"in\",\"targetId\":\"out\",\"sourcePort\":0}",
            "{" +
            "\".id\":\"in\",\".name\":\"StreamJdbcInput\"," +
            "\"schema\":\"demo\",\"table\":\"ods_records\"," +
            "\"sourceId\":" + duckLakeJson(lake) +
            "}",
            "{" +
            "\".id\":\"out\",\".name\":\"StreamJdbcOutput\",\"schema\":\"main\",\"table\":\"ods_records_input\"," +
            "\"model\":\"overwrite\",\"sourceId\":" + duckDbDataSourceJson(targetUrl) +
            "}"
        );

        assertDoesNotThrow(() -> newTask("ducklake-stream-input-" + System.nanoTime()).runJob(job),
            "数据源输入首次读取 DuckLake 应成功（表名需带 ducklake catalog）");
        assertSynced(targetUrl, "main.ods_records_input");
    }

    @Test
    @Timeout(180000)
    void generateTableSelectSqlReadsDuckLakeCatalog() throws Exception {
        DuckLake lake = createDuckLake("generate-sql");
        String targetUrl = duckDbUrl("ducklake-read-generate-sql.duckdb");
        deleteIfExists(targetUrl);

        String job = jobJson(
            "{\"sourceId\":\"gen\",\"targetId\":\"sql\",\"sourcePort\":0}," +
            "{\"sourceId\":\"sql\",\"targetId\":\"out\",\"sourcePort\":0}",
            "{" +
            "\".id\":\"gen\",\".name\":\"GenerateTableSelectSql\"," +
            "\"schema\":\"demo\",\"table\":\"ods_records\"," +
            "\"sourceId\":" + duckLakeJson(lake) +
            "}",
            "{" +
            "\".id\":\"sql\",\".name\":\"SqlInput\",\"outputTable\":\"ods_records_gen\"," +
            "\"sourceId\":" + duckLakeJson(lake) +
            "}",
            "{" +
            "\".id\":\"out\",\".name\":\"StreamJdbcOutput\",\"schema\":\"main\",\"table\":\"ods_records_gen\"," +
            "\"model\":\"overwrite\",\"sourceId\":" + duckDbDataSourceJson(targetUrl) +
            "}"
        );

        assertDoesNotThrow(() -> newTask("ducklake-generate-sql-" + System.nanoTime()).runJob(job),
            "生成表SQL 串联 SQL 输入读取 DuckLake 应成功");
        assertSynced(targetUrl, "main.ods_records_gen");
    }

    /**
     * 创建一个全新的 DuckLake，并删除 Provider 对应的本地挂载文件，确保首次读取需要完整初始化。
     */
    private DuckLake createDuckLake(String prefix) throws Exception {
        Path base = Files.createTempDirectory("ducklake-read-" + prefix);
        String meta = base.resolve("metadata.ducklake").toString();
        String data = base.resolve("data").toString();

        File creator = File.createTempFile("ducklake_creator", ".duckdb");
        creator.delete();

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + creator.getAbsolutePath())) {
            try (Statement st = conn.createStatement()) {
                st.execute("INSTALL ducklake");
                st.execute("LOAD ducklake");
                st.execute("ATTACH 'ducklake:" + escape(meta) + "' AS ducklake (data_path '" + escape(data) + "')");
            } catch (Exception e) {
                Assumptions.assumeTrue(false, "DuckLake 扩展不可用，跳过: " + e.getMessage());
            }
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE SCHEMA ducklake.demo");
                st.execute(
                    "CREATE TABLE ducklake.demo.ods_records (id INT, name VARCHAR, amount DECIMAL(10,2))"
                );
                st.execute(
                    "INSERT INTO ducklake.demo.ods_records " +
                    "SELECT i, 'row-' || i, CAST(i AS DECIMAL(10,2)) * 1.5 FROM range(1, " + (ROWS + 1) + ") t(i)"
                );
            }
        } finally {
            creator.delete();
        }

        DuckLake lake = new DuckLake();
        lake.meta = meta;
        lake.data = data;

        // 模拟新进程：删除 Provider 本地 scratch 文件，避免复用此前的 ATTACH 状态
        String jdbcUrl = DBUtils.resolveDuckLakeJdbcUrl(toInfo(lake));
        String path = jdbcUrl.substring("jdbc:duckdb:".length());
        deleteIfExists(path);
        deleteIfExists(path + ".wal");
        return lake;
    }

    private void assertSynced(String targetUrl, String table) throws Exception {
        try (Connection conn = DBUtils.getConnection(targetUrl, "", "");
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT COUNT(*), SUM(id), MIN(id), MAX(id) FROM " + table)) {
            assertTrue(rs.next(), "目标表 " + table + " 不存在");
            assertEquals(ROWS, rs.getInt(1), table + " 行数不正确");
            assertEquals(ID_SUM, rs.getLong(2), table + " id 累计和不正确");
            assertEquals(1, rs.getInt(3), table + " 最小值不正确");
            assertEquals(ROWS, rs.getInt(4), table + " 最大值不正确");
        }
    }

    private ETLFlowTask newTask(String jobCode) {
        ETLFlowTask task = new ETLFlowTask();
        task.getContext().setJobCode(jobCode);
        task.setStatusStorageStrategy(new InMemoryStatusStorage());
        return task;
    }

    private String jobJson(String connections, String... units) {
        return "{\n" +
            "  \"units\": [\n" + String.join(",\n", units) + "\n  ],\n" +
            "  \"connections\": [\n" + connections + "\n  ],\n" +
            "  \"version\": \"1.0.0\"\n" +
            "}";
    }

    private String duckLakeJson(DuckLake lake) {
        return "{\"type\":\"DUCKLAKE\",\"url\":\"ducklake:" + jsonEscape(lake.meta) +
            "\",\"username\":\"\",\"password\":\"\",\"dbschema\":\"demo\"," +
            "\"extraParams\":{\"s3.data_path\":\"" + jsonEscape(lake.data) + "\"}}";
    }

    private String duckDbDataSourceJson(String url) {
        return "{\"type\":\"DUCKDB\",\"url\":\"" + jsonEscape(url) +
            "\",\"username\":\"\",\"password\":\"\",\"dbschema\":\"main\"}";
    }

    private DatasourceInfo toInfo(DuckLake lake) {
        DatasourceInfo info = new DatasourceInfo();
        info.setType("DUCKLAKE");
        info.setUrl("ducklake:" + lake.meta);
        info.setDbschema("demo");
        info.setExtraParams(java.util.Map.of("s3.data_path", lake.data));
        return info;
    }

    private String duckDbUrl(String fileName) {
        return "jdbc:duckdb:" + new File("target/" + fileName).getAbsolutePath();
    }

    private void deleteIfExists(String path) {
        File file = new File(path);
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("无法删除测试文件: " + file.getAbsolutePath());
        }
    }

    private static String escape(String value) {
        return value.replace("'", "''");
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static class DuckLake {

        private String meta;
        private String data;
    }

    private static class InMemoryStatusStorage implements com.data.status.StatusStorageStrategy {

        private final java.util.Map<String, java.util.Map<String, Object>> store = new java.util.HashMap<>();

        @Override
        public synchronized boolean saveStatus(java.util.Map<String, Object> status, String jobCode) {
            store.put(jobCode, new java.util.HashMap<>(status));
            return true;
        }

        @Override
        public synchronized java.util.Map<String, Object> loadStatus(String jobCode) {
            java.util.Map<String, Object> status = store.get(jobCode);
            return status == null ? null : new java.util.HashMap<>(status);
        }

        @Override
        public synchronized boolean deleteStatus(String jobCode) {
            store.remove(jobCode);
            return true;
        }

        @Override
        public synchronized java.util.List<String> listJobCodes() {
            return new java.util.ArrayList<>(store.keySet());
        }

        @Override
        public String getStrategyName() {
            return "InMemory";
        }
    }
}
