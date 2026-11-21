package com.data.job.component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ETLFlowTask;
import com.data.job.TaskInstance;
import com.data.job.TaskLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Doris -> MySQL 端到端同步验证。
 *
 * <p>默认跳过；设置 {@code DORIS_ARROW_IT=true}，并准备可访问的 MySQL 后运行。</p>
 *
 * <p>MySQL 连接参数可通过环境变量覆盖：{@code MYSQL_HOST}、{@code MYSQL_PORT}、
 * {@code MYSQL_USER}、{@code MYSQL_PASSWORD}。</p>
 */
@EnabledIfEnvironmentVariable(named = "DORIS_ARROW_IT", matches = "true")
class DorisArrowFlightToMysqlIntegrationTest {

    private static final String DORIS_HOST = System.getenv().getOrDefault("DORIS_FE_HOST", "127.0.0.1");
    private static final int DORIS_QUERY_PORT = Integer.parseInt(System.getenv().getOrDefault("DORIS_QUERY_PORT", "9030"));
    private static final int DORIS_ARROW_PORT = Integer.parseInt(System.getenv().getOrDefault("DORIS_ARROW_PORT", "8070"));

    private static final String MYSQL_HOST = System.getenv().getOrDefault("MYSQL_HOST", "127.0.0.1");
    private static final int MYSQL_PORT = Integer.parseInt(System.getenv().getOrDefault("MYSQL_PORT", "3307"));
    private static final String MYSQL_USER = System.getenv().getOrDefault("MYSQL_USER", "root");
    private static final String MYSQL_PASSWORD = System.getenv().getOrDefault("MYSQL_PASSWORD", "password");
    private static final String MYSQL_SCHEMA = "doris_verify";
    private static final String TARGET_TABLE = "orders_from_doris";

    private static final String MYSQL_SERVER_URL =
        "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String MYSQL_DB_URL =
        "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT + "/" + MYSQL_SCHEMA +
        "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";

    @BeforeEach
    void prepareMysql() throws Exception {
        assumeTrue(canConnectMysql(), "MySQL 不可用，跳过 Doris -> MySQL 验证");
        try (Connection connection = DriverManager.getConnection(MYSQL_SERVER_URL, MYSQL_USER, MYSQL_PASSWORD)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE IF NOT EXISTS " + MYSQL_SCHEMA);
            }
        }
        dropTargetTable();
    }

    @AfterEach
    void cleanup() {
        try {
            dropTargetTable();
        } catch (Exception ignored) {
            // 清理失败不影响测试结论
        }
    }

    @Test
    void syncsDorisTableToMysql() throws Exception {
        String instanceCode = "doris-mysql-it-" + System.currentTimeMillis();
        String jobJson = buildJobJson();

        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setJobCode("doris-mysql-it");
        taskInstance.setInstanceCode(instanceCode);
        taskInstance.setJobContext(jobJson);
        taskInstance.setType("ETL");

        TaskLogger taskLogger = new TaskLogger("doris-mysql-it", instanceCode);
        ETLFlowTask flowTask = new ETLFlowTask(taskInstance, taskLogger);
        flowTask.runJob(jobJson);

        try (Connection connection = DriverManager.getConnection(MYSQL_DB_URL, MYSQL_USER, MYSQL_PASSWORD)) {
            try (Statement statement = connection.createStatement()) {
                try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + TARGET_TABLE)) {
                    rs.next();
                    assertEquals(3, rs.getInt(1));
                }
                try (
                    ResultSet rs = statement.executeQuery(
                        "SELECT id, name, amount, biz_date, update_time FROM " + TARGET_TABLE + " ORDER BY id"
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
                            assertEquals(0, new BigDecimal("12.34").compareTo(rs.getBigDecimal("amount")));
                            assertEquals("2026-11-19", rs.getDate("biz_date").toLocalDate().toString());
                            assertEquals(
                                LocalDateTime.of(2026, 11, 19, 10, 0),
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
    }

    private String buildJobJson() {
        JSONObject source = new JSONObject();
        source.put(".id", "doris_source");
        source.put(".name", "DorisArrowFlightInput");
        source.put("schema", "demo");
        source.put("table", "orders");
        source.put("arrowFlightHost", DORIS_HOST);
        source.put("arrowFlightPort", DORIS_ARROW_PORT);
        source.put("batchSize", 2);

        JSONObject dorisDatasource = new JSONObject();
        dorisDatasource.put("type", "DORIS");
        dorisDatasource.put("url", "jdbc:mysql://" + DORIS_HOST + ":" + DORIS_QUERY_PORT + "/demo");
        dorisDatasource.put("username", "root");
        dorisDatasource.put("password", "");
        dorisDatasource.put("dbschema", "demo");
        source.put("sourceId", dorisDatasource);

        JSONObject sink = new JSONObject();
        sink.put(".id", "mysql_sink");
        sink.put(".name", "StreamJdbcOutput");
        sink.put("schema", MYSQL_SCHEMA);
        sink.put("table", TARGET_TABLE);
        sink.put("model", "overwrite");

        JSONObject mysqlDatasource = new JSONObject();
        mysqlDatasource.put("type", "MYSQL");
        mysqlDatasource.put("url", MYSQL_DB_URL);
        mysqlDatasource.put("username", MYSQL_USER);
        mysqlDatasource.put("password", MYSQL_PASSWORD);
        mysqlDatasource.put("dbschema", MYSQL_SCHEMA);
        sink.put("sourceId", mysqlDatasource);

        JSONArray units = new JSONArray();
        units.add(source);
        units.add(sink);

        JSONObject connection = new JSONObject();
        connection.put("sourceId", "doris_source");
        connection.put("targetId", "mysql_sink");
        connection.put("sourcePort", 0);
        JSONArray connections = new JSONArray();
        connections.add(connection);

        JSONObject job = new JSONObject();
        job.put("units", units);
        job.put("connections", connections);
        return JSON.toJSONString(job);
    }

    private boolean canConnectMysql() {
        try (Connection ignored = DriverManager.getConnection(MYSQL_SERVER_URL, MYSQL_USER, MYSQL_PASSWORD)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void dropTargetTable() throws Exception {
        try (Connection connection = DriverManager.getConnection(MYSQL_DB_URL, MYSQL_USER, MYSQL_PASSWORD)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS " + TARGET_TABLE);
            }
        }
    }
}
