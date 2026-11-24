package com.data.job.component;

import com.alibaba.fastjson2.JSONObject;
import com.data.job.DuckDBEngine;
import com.data.job.ETLFlowTask;
import com.data.job.TaskInstance;
import com.data.job.TaskLogger;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 真实 Doris 端到端验证：多表 LEFT JOIN → DorisStreamLoad。
 *
 * <p>复现线上链路形状（主表 + 维表 LEFT JOIN 后写 Doris），验证：</p>
 * <ol>
 *   <li>LEFT JOIN 后主表行数不减少；</li>
 *   <li>Join 按 10000 行分批后，所有批次都真实写入 Doris；</li>
 *   <li>没有匹配维表的主表行仍然保留（维表字段为 NULL）。</li>
 * </ol>
 *
 * <p>默认跳过，设置 {@code DORIS_STREAM_LOAD_IT=true} 后运行。</p>
 */
@EnabledIfEnvironmentVariable(named = "DORIS_STREAM_LOAD_IT", matches = "true")
class DorisJoinStreamLoadIntegrationTest {

    private static final String FE_HOST = env("DORIS_FE_HOST", "127.0.0.1");
    private static final int FE_HTTP_PORT = Integer.parseInt(env("DORIS_HTTP_PORT", "8030"));
    private static final int QUERY_PORT = Integer.parseInt(env("DORIS_QUERY_PORT", "9030"));
    private static final String SCHEMA = "dwd_join_it";
    private static final String JDBC_URL =
        "jdbc:mysql://" + FE_HOST + ":" + QUERY_PORT +
            "/?useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true";
    private static final int MAIN_ROWS = 12_000;
    private static final int DIM_ROWS = 3_000;

    @Test
    @Timeout(300_000)
    void leftJoinKeepsAllMainRowsAndWritesEveryBatchToDoris() throws Exception {
        prepareDoris();

        String instanceCode = "doris-join-it-" + System.nanoTime();
        String datasourceUrl = "jdbc:mysql://" + FE_HOST + ":" + QUERY_PORT + "/" + SCHEMA +
            "?useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true";

        String job = buildJobJson(datasourceUrl);

        try {
            TaskInstance taskInstance = new TaskInstance();
            taskInstance.setJobCode("doris-join-it");
            taskInstance.setInstanceCode(instanceCode);
            taskInstance.setJobContext(job);
            taskInstance.setType("ETL");

            TaskLogger taskLogger = new TaskLogger("doris-join-it", instanceCode);
            ETLFlowTask flowTask = new ETLFlowTask(taskInstance, taskLogger);
            flowTask.runJob(job);

            try (Connection connection = DriverManager.getConnection(JDBC_URL, "root", "");
                 Statement statement = connection.createStatement()) {
                try (ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM " + SCHEMA + ".e2e_out")) {
                    rs.next();
                    assertEquals(MAIN_ROWS, rs.getInt(1),
                        "LEFT JOIN 后写入 Doris 的行数应等于主表行数");
                }
                try (ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM " + SCHEMA + ".e2e_out WHERE agency IS NULL")) {
                    rs.next();
                    assertEquals(MAIN_ROWS - DIM_ROWS, rs.getInt(1),
                        "未匹配维表的行应保留，agency 为 NULL");
                }
            }
        } finally {
            DuckDBEngine.deleteDBFile(instanceCode);
            DBUtils.closePool(datasourceUrl, null);
        }
    }

    private String buildJobJson(String datasourceUrl) {
        JSONObject mainInput = unit("main_input", "StreamJdbcInput");
        mainInput.put("sourceId", datasource(datasourceUrl));
        mainInput.put("schema", SCHEMA);
        mainInput.put("table", "e2e_main");
        mainInput.put("batchSize", 10_000);

        JSONObject dimInput = unit("dim_input", "StreamJdbcInput");
        dimInput.put("sourceId", datasource(datasourceUrl));
        dimInput.put("schema", SCHEMA);
        dimInput.put("table", "e2e_dim");
        dimInput.put("batchSize", 10_000);

        JSONObject join = unit("join", "Join");
        join.put("outputTable", "join_result");
        join.put("fromTable", "e2e_main");
        join.put(
            "selectColumns",
            "e2e_main.sellid AS sellid, e2e_main.amount AS amount, e2e_dim.agency AS agency"
        );
        com.alibaba.fastjson2.JSONArray joins = new com.alibaba.fastjson2.JSONArray();
        JSONObject joinConfig = new JSONObject();
        joinConfig.put("type", "LEFT");
        joinConfig.put("table", "e2e_dim");
        joinConfig.put("leftTable", "e2e_main");
        joinConfig.put("leftField", "sellid");
        joinConfig.put("rightField", "sellid");
        joins.add(joinConfig);
        join.put("joins", joins);

        JSONObject sink = unit("sink", "DorisStreamLoad");
        sink.put("sourceId", datasource(datasourceUrl));
        sink.put("schema", SCHEMA);
        sink.put("table", "e2e_out");
        sink.put("model", "INSERT");

        com.alibaba.fastjson2.JSONArray units = new com.alibaba.fastjson2.JSONArray();
        units.add(mainInput);
        units.add(dimInput);
        units.add(join);
        units.add(sink);

        com.alibaba.fastjson2.JSONArray connections = new com.alibaba.fastjson2.JSONArray();
        connections.add(connection("main_input", "join"));
        connections.add(connection("dim_input", "join"));
        connections.add(connection("join", "sink"));

        JSONObject job = new JSONObject();
        job.put("units", units);
        job.put("connections", connections);
        return job.toJSONString();
    }

    private JSONObject datasource(String jdbcUrl) {
        JSONObject datasource = new JSONObject();
        datasource.put("type", "DORIS");
        datasource.put("url", jdbcUrl);
        datasource.put("hostname", FE_HOST);
        datasource.put("port", String.valueOf(QUERY_PORT));
        datasource.put("username", "root");
        datasource.put("password", "");
        datasource.put("dbschema", SCHEMA);
        JSONObject extraParams = new JSONObject();
        extraParams.put("fe_endpoint", "http://" + FE_HOST + ":" + FE_HTTP_PORT);
        datasource.put("extraParams", extraParams);
        return datasource;
    }

    private JSONObject unit(String id, String name) {
        JSONObject unit = new JSONObject();
        unit.put(".id", id);
        unit.put(".name", name);
        return unit;
    }

    private JSONObject connection(String sourceId, String targetId) {
        JSONObject connection = new JSONObject();
        connection.put("sourceId", sourceId);
        connection.put("targetId", targetId);
        connection.put("sourcePort", 0);
        return connection;
    }

    private void prepareDoris() throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "root", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS " + SCHEMA);
            statement.execute("DROP TABLE IF EXISTS " + SCHEMA + ".e2e_out");
            statement.execute("DROP TABLE IF EXISTS " + SCHEMA + ".e2e_main");
            statement.execute("DROP TABLE IF EXISTS " + SCHEMA + ".e2e_dim");

            statement.execute("CREATE TABLE " + SCHEMA + ".e2e_main (\n" +
                "  sellid VARCHAR(32),\n" +
                "  amount INT\n" +
                ") ENGINE=OLAP DUPLICATE KEY(sellid)\n" +
                "DISTRIBUTED BY HASH(sellid) BUCKETS 1\n" +
                "PROPERTIES (\"replication_num\" = \"1\")");
            statement.execute("CREATE TABLE " + SCHEMA + ".e2e_dim (\n" +
                "  sellid VARCHAR(32),\n" +
                "  agency VARCHAR(64)\n" +
                ") ENGINE=OLAP DUPLICATE KEY(sellid)\n" +
                "DISTRIBUTED BY HASH(sellid) BUCKETS 1\n" +
                "PROPERTIES (\"replication_num\" = \"1\")");
            statement.execute("CREATE TABLE " + SCHEMA + ".e2e_out (\n" +
                "  sellid VARCHAR(32),\n" +
                "  amount INT,\n" +
                "  agency VARCHAR(64)\n" +
                ") ENGINE=OLAP DUPLICATE KEY(sellid)\n" +
                "DISTRIBUTED BY HASH(sellid) BUCKETS 1\n" +
                "PROPERTIES (\"replication_num\" = \"1\")");
        }

        try (Connection connection = DriverManager.getConnection(JDBC_URL, "root", "")) {
            connection.setAutoCommit(false);

            try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO " + SCHEMA + ".e2e_main (sellid, amount) VALUES (?, ?)")) {
                for (int i = 1; i <= MAIN_ROWS; i++) {
                    ps.setString(1, String.format("S%06d", i));
                    ps.setInt(2, i);
                    ps.addBatch();
                    if (i % 1000 == 0) {
                        ps.executeBatch();
                        connection.commit();
                    }
                }
                ps.executeBatch();
                connection.commit();
            }

            try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO " + SCHEMA + ".e2e_dim (sellid, agency) VALUES (?, ?)")) {
                for (int i = 1; i <= DIM_ROWS; i++) {
                    ps.setString(1, String.format("S%06d", i));
                    ps.setString(2, "agency-" + i);
                    ps.addBatch();
                    if (i % 1000 == 0) {
                        ps.executeBatch();
                        connection.commit();
                    }
                }
                ps.executeBatch();
                connection.commit();
            }
            connection.setAutoCommit(true);
        }
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
