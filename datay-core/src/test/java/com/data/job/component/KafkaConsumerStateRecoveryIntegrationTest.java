package com.data.job.component;

import com.alibaba.fastjson2.JSON;
import com.data.job.ETLFlowTask;
import com.data.job.TaskInstance;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Kafka 消费 → MySQL 的位点状态保存与异常恢复验证。
 *
 * <p>场景：首次运行消费 3 条消息并写入 MySQL，消费位点状态随批次持久化；任务停止期间
 * 主题新增 2 条消息；使用<b>不同消费组</b>重启任务（排除 Kafka 组位点的影响），组件应从
 * 保存的位点继续消费，只补写新增的 2 条，不重复消费历史数据。
 *
 * <p>前置条件：
 * <ul>
 *   <li>{@code KAFKA_IT_BROKERS}，如 127.0.0.1:9092；</li>
 *   <li>{@code KAFKA_IT_MYSQL_URL} 指向可写 MySQL 库，如 jdbc:mysql://127.0.0.1:3307/datay_kafka_mysql?...；</li>
 *   <li>可选 {@code KAFKA_IT_MYSQL_USER} / {@code KAFKA_IT_MYSQL_PASSWORD}，默认 root/password。</li>
 * </ul>
 */
@EnabledIfEnvironmentVariable(named = "KAFKA_IT_BROKERS", matches = ".+")
public class KafkaConsumerStateRecoveryIntegrationTest {

    private static final String JOB_CODE = "kafka-state-recovery-it";
    private static final String TABLE = "kafka_state_it";

    private final String brokers = System.getenv("KAFKA_IT_BROKERS");
    private final String mysqlUrl = System.getenv("KAFKA_IT_MYSQL_URL");
    private final String mysqlUser = envOrDefault("KAFKA_IT_MYSQL_USER", "root");
    private final String mysqlPassword = envOrDefault("KAFKA_IT_MYSQL_PASSWORD", "password");

    @Test
    @Timeout(180000)
    public void shouldResumeFromSavedOffsetWithDifferentConsumerGroup() throws Exception {
        assumeTrue(mysqlUrl != null && !mysqlUrl.trim().isEmpty(), "未配置 KAFKA_IT_MYSQL_URL，跳过状态恢复测试");

        String topic = "datay-state-it-" + UUID.randomUUID();
        deleteStatusFile();
        createTopic(topic);
        resetTable();

        try {
            // ===== 第一次运行：消费 3 条消息，位点状态应持久化为 3 =====
            ETLFlowTask firstRun = new ETLFlowTask(taskInstance("run1"), null);
            ExecutorService firstExecutor = Executors.newSingleThreadExecutor();
            AtomicReference<Throwable> firstError = new AtomicReference<>();
            Future<?> firstFuture = firstExecutor.submit(() -> runQuietly(firstRun, job(topic, "datay-state-it-1-" + UUID.randomUUID()), firstError));
            try {
                publish(topic, 1, 2, 3);
                awaitTrue(() -> tableCount() == 3, 45000, "首次运行未把 3 条 Kafka 消息写入 MySQL");
                awaitTrue(() -> Long.valueOf(3L).equals(readStatusOffset(topic)), 20000, "消费位点状态未持久化为 3");
                assertEquals(3L, readStatusOffset(topic));
            } finally {
                stop(firstRun, firstFuture, firstExecutor);
                assertNoUnexpectedError(firstError.get());
            }

            // ===== 停机期间新增 2 条消息，重启时使用不同消费组 =====
            publish(topic, 4, 5);

            ETLFlowTask secondRun = new ETLFlowTask(taskInstance("run2"), null);
            ExecutorService secondExecutor = Executors.newSingleThreadExecutor();
            AtomicReference<Throwable> secondError = new AtomicReference<>();
            Future<?> secondFuture = secondExecutor.submit(() -> runQuietly(secondRun, job(topic, "datay-state-it-2-" + UUID.randomUUID()), secondError));
            try {
                awaitTrue(() -> tableCount() == 5, 45000, "重启后未从保存的位点继续消费（若回放 earliest 会出现重复数据）");
                awaitTrue(() -> Long.valueOf(5L).equals(readStatusOffset(topic)), 20000, "重启后位点状态未更新为 5");
                assertEquals(5L, readStatusOffset(topic));
            } finally {
                stop(secondRun, secondFuture, secondExecutor);
                assertNoUnexpectedError(secondError.get());
            }

            // 校验写入内容：恰好 s-1..s-5，无重复
            assertEquals(5, tableCount());
            assertEquals(List.of("s-1", "s-2", "s-3", "s-4", "s-5"), tableEventIds());
        } finally {
            deleteTopic(topic);
            dropTable();
            deleteStatusFile();
        }
    }

    private void runQuietly(ETLFlowTask task, String jobJson, AtomicReference<Throwable> error) {
        try {
            task.runJob(jobJson);
        } catch (Throwable e) {
            error.set(e);
        }
    }

    private String job(String topic, String groupId) {
        String datasource = "{\"type\":\"mysql\",\"url\":\"" + mysqlUrl + "\",\"driver\":\"com.mysql.cj.jdbc.Driver\","
            + "\"username\":\"" + mysqlUser + "\",\"password\":\"" + mysqlPassword + "\"}";
        String sql = "INSERT INTO " + TABLE + " (event_id) VALUES (${eventId})";
        return "{\"units\":["
            + "{\".id\":\"n1\",\".name\":\"KafkaConsumer\",\"bootstrapServers\":\"" + brokers + "\",\"topic\":\"" + topic + "\","
            + "\"groupId\":\"" + groupId + "\",\"autoOffsetReset\":\"earliest\",\"enableAutoCommit\":true,"
            + "\"batchSize\":10,\"consumerTimeoutMs\":1000,\"connectionTimeoutMs\":5000,\"partition\":-1},"
            + "{\".id\":\"n2\",\".name\":\"SqlSink\",\"sourceId\":" + datasource + ","
            + "\"sql\":\"" + sql + "\",\"batchSize\":100,\"transaction\":true}"
            + "],\"connections\":[{\"sourceId\":\"n1\",\"targetId\":\"n2\",\"sourcePort\":0,\"queueCapacity\":10}],"
            + "\"version\":\"1.0.0\"}";
    }

    private TaskInstance taskInstance(String suffix) {
        TaskInstance instance = new TaskInstance();
        instance.setId(JOB_CODE + "-" + suffix);
        instance.setInstanceCode(JOB_CODE + "-" + suffix + "-" + System.currentTimeMillis());
        instance.setJobCode(JOB_CODE);
        instance.setJobName(JOB_CODE);
        return instance;
    }

    private void createTopic(String topic) throws Exception {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
        try (AdminClient admin = AdminClient.create(props)) {
            admin.createTopics(Collections.singletonList(new NewTopic(topic, 1, (short) 1)))
                .all()
                .get(15, TimeUnit.SECONDS);
        }
    }

    private void deleteTopic(String topic) {
        try {
            Properties props = new Properties();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
            props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
            try (AdminClient admin = AdminClient.create(props)) {
                admin.deleteTopics(Collections.singletonList(topic)).all().get(15, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {
            // 清理失败不影响验证结论
        }
    }

    private void publish(String topic, int... ids) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (int id : ids) {
                producer.send(new ProducerRecord<>(topic, "{\"eventId\":\"s-" + id + "\"}"));
            }
            producer.flush();
        }
    }

    private void resetTable() throws Exception {
        try (Connection conn = mysqlConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + TABLE);
            st.execute("CREATE TABLE " + TABLE + " (id BIGINT AUTO_INCREMENT PRIMARY KEY, event_id VARCHAR(64))");
        }
    }

    private void dropTable() {
        try (Connection conn = mysqlConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + TABLE);
        } catch (Exception ignored) {
            // 清理失败不影响验证结论
        }
    }

    private int tableCount() {
        try (Connection conn = mysqlConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + TABLE)) {
            return rs.next() ? rs.getInt(1) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private List<String> tableEventIds() throws Exception {
        List<String> ids = new ArrayList<>();
        try (Connection conn = mysqlConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT event_id FROM " + TABLE + " ORDER BY id")) {
            while (rs.next()) {
                ids.add(rs.getString(1));
            }
        }
        return ids;
    }

    private Connection mysqlConnection() throws Exception {
        return DriverManager.getConnection(mysqlUrl, mysqlUser, mysqlPassword);
    }

    private Long readStatusOffset(String topic) {
        Map<String, Object> status = readStatus();
        if (status == null) {
            return null;
        }
        Object value = status.get("n1.kafkaOffset." + topic + ".0");
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> readStatus() {
        try {
            Path path = statusPath();
            if (!Files.exists(path)) {
                return null;
            }
            return JSON.parseObject(Files.readString(path, StandardCharsets.UTF_8), Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private Path statusPath() {
        return Path.of(System.getProperty("user.dir"), "log", "status_" + JOB_CODE + ".json");
    }

    private void deleteStatusFile() throws Exception {
        Files.deleteIfExists(statusPath());
    }

    private void stop(ETLFlowTask runner, Future<?> future, ExecutorService executor) {
        runner.cancel();
        try {
            future.get(15, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // 流式任务取消后正常结束
        }
        executor.shutdownNow();
    }

    private void assertNoUnexpectedError(Throwable error) {
        if (error != null && !isCancellation(error)) {
            throw new AssertionError("Kafka 状态恢复任务异常结束: " + error.getMessage(), error);
        }
    }

    private boolean isCancellation(Throwable throwable) {
        while (throwable != null) {
            if (throwable instanceof InterruptedException) {
                return true;
            }
            String message = throwable.getMessage();
            if (message != null && message.contains("InterruptedException")) {
                return true;
            }
            throwable = throwable.getCause();
        }
        return false;
    }

    private void awaitTrue(BooleanSupplier condition, long timeoutMillis, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        assertTrue(false, message + "（当前表行数=" + tableCount() + "）");
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }
}
