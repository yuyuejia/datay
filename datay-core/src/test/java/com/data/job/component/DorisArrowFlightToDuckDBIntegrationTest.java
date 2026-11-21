package com.data.job.component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.DuckDBEngine;
import com.data.job.ETLFlowTask;
import com.data.job.TaskInstance;
import com.data.job.TaskLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Doris -> DuckDB 端到端同步验证。
 *
 * <p>默认跳过；设置 {@code DORIS_ARROW_IT=true} 且 Doris 已准备 demo.orders 后运行。</p>
 */
@EnabledIfEnvironmentVariable(named = "DORIS_ARROW_IT", matches = "true")
class DorisArrowFlightToDuckDBIntegrationTest {

    private static final String FE_HOST = System.getenv().getOrDefault("DORIS_FE_HOST", "127.0.0.1");
    private static final int QUERY_PORT = Integer.parseInt(System.getenv().getOrDefault("DORIS_QUERY_PORT", "9030"));
    private static final int ARROW_PORT = Integer.parseInt(System.getenv().getOrDefault("DORIS_ARROW_PORT", "8070"));

    @Test
    void syncsDorisTableToDuckDB() throws Exception {
        String dbFileName = "doris-duckdb-it-" + System.nanoTime();
        String dbFilePath = DuckDBEngine.getDBFile(dbFileName);
        Files.deleteIfExists(Path.of(dbFilePath));
        Files.deleteIfExists(Path.of(dbFilePath + ".wal"));
        String instanceCode = "doris-duckdb-it-" + System.currentTimeMillis();

        try {
            String jobJson = buildJobJson(dbFileName);

            TaskInstance taskInstance = new TaskInstance();
            taskInstance.setJobCode("doris-duckdb-it");
            taskInstance.setInstanceCode(instanceCode);
            taskInstance.setJobContext(jobJson);
            taskInstance.setType("ETL");

            TaskLogger taskLogger = new TaskLogger("doris-duckdb-it", instanceCode);
            ETLFlowTask flowTask = new ETLFlowTask(taskInstance, taskLogger);
            flowTask.runJob(jobJson);

            try (Connection connection = DuckDBEngine.getInstance().getConnection(dbFileName)) {
                try (Statement statement = connection.createStatement()) {
                    try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM main.orders_from_doris")) {
                        rs.next();
                        assertEquals(3, rs.getInt(1));
                    }
                    try (
                        ResultSet rs = statement.executeQuery(
                            "SELECT id, name, amount, biz_date, update_time FROM main.orders_from_doris ORDER BY id"
                        )
                    ) {
                        int rowCount = 0;
                        int lastId = 0;
                        String lastName = null;
                        boolean firstChecked = false;
                        while (rs.next()) {
                            rowCount++;
                            if (!firstChecked) {
                                assertEquals(1, rs.getInt("id"));
                                assertEquals("订单-1", rs.getString("name"));
                                assertEquals(0, new java.math.BigDecimal("12.34").compareTo(rs.getBigDecimal("amount")));
                                assertEquals("2026-11-19", rs.getObject("biz_date").toString());
                                assertEquals(
                                    java.time.LocalDateTime.of(2026, 11, 19, 10, 0),
                                    rs.getTimestamp("update_time").toLocalDateTime()
                                );
                                firstChecked = true;
                            }
                            lastId = rs.getInt("id");
                            lastName = rs.getString("name");
                        }
                        assertEquals(3, rowCount);
                        assertEquals(3, lastId);
                        assertEquals("订单-3", lastName);
                    }
                }
            }
        } finally {
            try {
                try (Connection connection = DuckDBEngine.getInstance().getConnection(dbFileName)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("DROP TABLE IF EXISTS main.orders_from_doris");
                    }
                }
            } catch (Exception ignored) {
                // 清理失败不影响测试结论
            }
            DuckDBEngine.deleteDBFile(dbFileName);
            Files.deleteIfExists(Path.of(dbFilePath));
            Files.deleteIfExists(Path.of(dbFilePath + ".wal"));
        }
    }

    private String buildJobJson(String dbFile) {
        JSONObject source = new JSONObject();
        source.put(".id", "doris_source");
        source.put(".name", "DorisArrowFlightInput");
        source.put("schema", "demo");
        source.put("table", "orders");
        source.put("arrowFlightHost", FE_HOST);
        source.put("arrowFlightPort", ARROW_PORT);
        source.put("batchSize", 2);

        JSONObject datasource = new JSONObject();
        datasource.put("type", "DORIS");
        datasource.put("url", "jdbc:mysql://" + FE_HOST + ":" + QUERY_PORT + "/demo");
        datasource.put("username", "root");
        datasource.put("password", "");
        datasource.put("dbschema", "demo");
        source.put("sourceId", datasource);

        JSONObject sink = new JSONObject();
        sink.put(".id", "duckdb_sink");
        sink.put(".name", "DuckDBWrite");
        sink.put("table", "orders_from_doris");
        sink.put("model", "overwrite");
        sink.put("dbFile", dbFile);

        JSONArray units = new JSONArray();
        units.add(source);
        units.add(sink);

        JSONObject connection = new JSONObject();
        connection.put("sourceId", "doris_source");
        connection.put("targetId", "duckdb_sink");
        connection.put("sourcePort", 0);

        JSONArray connections = new JSONArray();
        connections.add(connection);

        JSONObject job = new JSONObject();
        job.put("units", units);
        job.put("connections", connections);
        return JSON.toJSONString(job);
    }
}
