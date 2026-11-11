package com.data.job.component;

import com.data.job.ETLFlowTask;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端验证：MQTT 消息 -> EvaluateJsonPath 提取 payload -> MySQL。
 *
 * <p>依赖本地 MQTT Broker 与 MySQL，通过环境变量/系统属性配置：
 * <pre>
 *   MQTT_IT_BROKER=tcp://127.0.0.1:1883
 *   -Dmysql.url=... -Dmysql.username=... -Dmysql.password=... -Dmysql.dbschema=...
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "MQTT_IT_BROKER", matches = ".+")
public class MqttEvaluateJsonPathToMysqlTest {

    private static final String MQTT_BROKER = System.getenv("MQTT_IT_BROKER");
    private static final String MQTT_USERNAME = System.getenv("MQTT_IT_USERNAME");
    private static final String MQTT_PASSWORD = System.getenv("MQTT_IT_PASSWORD");

    private static final String MYSQL_URL = System.getProperty(
        "mysql.url",
        "jdbc:mysql://127.0.0.1:3306/test_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
    );
    private static final String MYSQL_USER = System.getProperty("mysql.username", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("mysql.password", "1234qwer");
    private static final String DBSCHEMA = System.getProperty("mysql.dbschema", "test_db");

    @Test
    @Timeout(120000)
    public void testMqttPayloadObjectExtractedAndWrittenToMysql() throws Exception {
        String table = "mqtt_jsonpath_verify";
        String payload = "{\"msgId\":1001,\"payload\":{\"deviceId\":\"d-1\",\"temperature\":23.5}}";

        runPipeline(table, payload, "$.payload");

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT deviceId, temperature FROM " + table + " LIMIT 10")) {
            assertTrue(rs.next(), "MySQL 中应存在写入的数据");
            assertEquals("d-1", rs.getString("deviceId"));
            assertEquals(23.5, rs.getDouble("temperature"), 0.0001);
        }
        assertExtractedColumns(table);
    }

    @Test
    @Timeout(120000)
    public void testMqttArrayPayloadInUnderscoreFieldExtractedAndWrittenToMysql() throws Exception {
        String table = "mqtt_jsonpath_verify_array";
        // 多条记录组成的 JSON 数组会被 MqttConsumer 放入 _payload 字段
        String payload = "[{\"deviceId\":\"d-1\",\"temperature\":23.5},"
            + "{\"deviceId\":\"d-2\",\"temperature\":21.8}]";

        runPipeline(table, payload, "$._payload");

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT deviceId, temperature FROM " + table
                     + " GROUP BY deviceId, temperature ORDER BY deviceId")) {
            assertTrue(rs.next(), "MySQL 中应存在写入的数据");
            assertEquals("d-1", rs.getString("deviceId"));
            assertEquals(23.5, rs.getDouble("temperature"), 0.0001);
            assertTrue(rs.next(), "数组中的第二条记录也应写入");
            assertEquals("d-2", rs.getString("deviceId"));
            assertEquals(21.8, rs.getDouble("temperature"), 0.0001);
        }
        assertExtractedColumns(table);
    }

    private void runPipeline(String table, String payload, String jsonPath) throws Exception {
        dropTable(table);

        String topic = "datay/verify/" + UUID.randomUUID();
        String job = buildJob(topic, jsonPath, table);

        ETLFlowTask runner = new ETLFlowTask();
        AtomicReference<Throwable> jobError = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> {
            try {
                runner.runJob(job);
            } catch (InterruptedException e) {
                // 验证完成后主动 cancel，属于正常结束
                Thread.currentThread().interrupt();
            } catch (Throwable e) {
                jobError.set(e);
            }
        });

        try (MqttClient producer = new MqttClient(
            MQTT_BROKER,
            "datay-verify-publisher-" + UUID.randomUUID(),
            new MemoryPersistence()
        )) {
            connectProducer(producer);
            try {
                MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
                message.setQos(1);

                long deadline = System.currentTimeMillis() + 60000;
                boolean written = false;
                while (System.currentTimeMillis() < deadline) {
                    if (jobError.get() != null) {
                        throw new RuntimeException("ETL 任务执行失败", jobError.get());
                    }
                    producer.publish(topic, message);
                    if (queryRowCount(table) > 0) {
                        written = true;
                        break;
                    }
                    Thread.sleep(500);
                }
                assertTrue(written, "MQTT -> EvaluateJsonPath -> MySQL 链路在超时时间内未产生数据");
            } finally {
                if (producer.isConnected()) {
                    producer.disconnect();
                }
            }
        } finally {
            runner.cancel();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        future.get(10, TimeUnit.SECONDS);
    }

    /**
     * 确认写库结构来自提取结果，而不是 MQTT 原始记录。
     */
    private void assertExtractedColumns(String table) throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM " + table)) {
                boolean hasDeviceId = false;
                boolean hasTemperature = false;
                boolean hasRawField = false;
                while (rs.next()) {
                    String column = rs.getString("Field");
                    if ("deviceId".equalsIgnoreCase(column)) {
                        hasDeviceId = true;
                    } else if ("temperature".equalsIgnoreCase(column)) {
                        hasTemperature = true;
                    } else if ("msgId".equalsIgnoreCase(column) || "payload".equalsIgnoreCase(column)
                        || "_payload".equalsIgnoreCase(column) || "_topic".equalsIgnoreCase(column)) {
                        hasRawField = true;
                    }
                }
                assertTrue(hasDeviceId, "应写入提取后的 deviceId 列");
                assertTrue(hasTemperature, "应写入提取后的 temperature 列");
                assertFalse(hasRawField, "不应写入 MQTT 原始字段 msgId/payload/_payload/_topic");
            }
        }
    }

    private String buildJob(String topic, String jsonPath, String table) {
        return "{\n" +
            "  \"units\": [\n" +
            "    {\n" +
            "      \".id\": \"mqtt1\",\n" +
            "      \".name\": \"MqttConsumer\",\n" +
            "      \"brokerUrl\": \"" + MQTT_BROKER + "\",\n" +
            "      \"topic\": \"" + topic + "\",\n" +
            "      \"qos\": \"1\",\n" +
            "      \"cleanSession\": \"true\",\n" +
            "      \"batchSize\": \"10\",\n" +
            "      \"consumerTimeoutMs\": \"200\",\n" +
            "      \"payloadFormat\": \"AUTO\"\n" +
            "    },\n" +
            "    {\n" +
            "      \".id\": \"eval1\",\n" +
            "      \".name\": \"EvaluateJsonPath\",\n" +
            "      \"jsonPath\": \"" + jsonPath + "\"\n" +
            "    },\n" +
            "    {\n" +
            "      \".id\": \"mysql1\",\n" +
            "      \".name\": \"StreamJdbcOutput\",\n" +
            "      \"datasource\": {\n" +
            "        \"url\": \"" + MYSQL_URL + "\",\n" +
            "        \"driver\": \"com.mysql.cj.jdbc.Driver\",\n" +
            "        \"username\": \"" + MYSQL_USER + "\",\n" +
            "        \"password\": \"" + MYSQL_PASSWORD + "\",\n" +
            "        \"dbschema\": \"" + DBSCHEMA + "\"\n" +
            "      },\n" +
            "      \"table\": \"" + table + "\",\n" +
            "      \"schema\": \"" + DBSCHEMA + "\",\n" +
            "      \"model\": \"append\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"connections\": [\n" +
            "    { \"sourceId\": \"mqtt1\", \"targetId\": \"eval1\" },\n" +
            "    { \"sourceId\": \"eval1\", \"targetId\": \"mysql1\" }\n" +
            "  ]\n" +
            "}";
    }

    private void connectProducer(MqttClient producer) throws Exception {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(false);
        if (MQTT_USERNAME != null && !MQTT_USERNAME.trim().isEmpty()) {
            options.setUserName(MQTT_USERNAME.trim());
        }
        if (MQTT_PASSWORD != null) {
            options.setPassword(MQTT_PASSWORD.toCharArray());
        }
        producer.connect(options);
    }

    private void dropTable(String table) throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + table);
        }
    }

    private int queryRowCount(String table) {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
    }
}
