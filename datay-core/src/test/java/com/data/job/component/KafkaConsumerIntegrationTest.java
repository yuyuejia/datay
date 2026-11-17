package com.data.job.component;

import com.alibaba.fastjson2.JSONObject;
import com.data.job.Connection;
import com.data.job.DebugLimitReachedException;
import com.data.job.DebugResult;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "KAFKA_IT_BROKERS", matches = ".+")
public class KafkaConsumerIntegrationTest {

    @Test
    @Timeout(90000)
    public void testDebugModeConsumesOneBatchFromRealBroker() throws Exception {
        String bootstrapServers = System.getenv("KAFKA_IT_BROKERS");
        String topic = "datay-it-debug-" + UUID.randomUUID();
        createTopic(bootstrapServers, topic);
        publish(bootstrapServers, topic,
            "{\"deviceId\":\"d-1\",\"temperature\":23.5}",
            "{\"deviceId\":\"d-2\",\"temperature\":24.5}",
            "{\"deviceId\":\"d-3\",\"temperature\":25.5}");

        KafkaConsumerComponent component = newDebugComponent(bootstrapServers, topic, "kafka-it-debug");
        component.setBatchSize(10);

        component.execute(new FlowFile());

        DebugResult result = component.getContext().getDebugResults().get("kafka-it-debug");
        assertNotNull(result, "调试模式应采集真实 Kafka 消息");
        assertEquals(3, result.getRows().size(), "调试模式使用 earliest 起始位点，应消费到已有的 3 条消息");
        assertTrue(result.getColumns().contains("deviceId"));
        assertTrue(result.getColumns().contains("temperature"));
        assertEquals(topic, result.getAttributes().get("_kafkaTopic"));
        assertEquals("3", result.getAttributes().get("_recordCount"));
    }

    @Test
    @Timeout(90000)
    public void testStartTimestampWithPartitionConsumesOnlyNewerMessages() throws Exception {
        String bootstrapServers = System.getenv("KAFKA_IT_BROKERS");
        String topic = "datay-it-ts-" + UUID.randomUUID();
        createTopic(bootstrapServers, topic);
        publish(bootstrapServers, topic, "before-1", "before-2");
        Thread.sleep(1500);
        long startTimestamp = System.currentTimeMillis();
        Thread.sleep(1500);
        publish(bootstrapServers, topic, "after-1", "after-2");

        KafkaConsumerComponent component = newDebugComponent(bootstrapServers, topic, "kafka-it-ts");
        component.setPartition(0);
        component.setStartTimestamp(startTimestamp);

        component.execute(new FlowFile());

        DebugResult result = component.getContext().getDebugResults().get("kafka-it-ts");
        assertNotNull(result);
        assertEquals(2, result.getRows().size(), "指定分区时按时间戳起始应只消费时间戳之后的消息");
        assertEquals("after-1", valueOf(result, result.getRows().get(0)));
        assertEquals("after-2", valueOf(result, result.getRows().get(1)));
    }

    @Test
    @Timeout(90000)
    public void testStartTimestampWithSubscriptionConsumesOnlyNewerMessages() throws Exception {
        String bootstrapServers = System.getenv("KAFKA_IT_BROKERS");
        String topic = "datay-it-ts-sub-" + UUID.randomUUID();
        createTopic(bootstrapServers, topic);
        publish(bootstrapServers, topic, "before-1", "before-2");
        Thread.sleep(1500);
        long startTimestamp = System.currentTimeMillis();
        Thread.sleep(1500);
        publish(bootstrapServers, topic, "after-1", "after-2");

        KafkaConsumerComponent component = newDebugComponent(bootstrapServers, topic, "kafka-it-ts-sub");
        component.setStartTimestamp(startTimestamp);

        component.execute(new FlowFile());

        DebugResult result = component.getContext().getDebugResults().get("kafka-it-ts-sub");
        assertNotNull(result);
        assertEquals(2, result.getRows().size(), "订阅模式按时间戳起始应只消费时间戳之后的消息");
        assertEquals("after-1", valueOf(result, result.getRows().get(0)));
        assertEquals("after-2", valueOf(result, result.getRows().get(1)));
    }

    @Test
    @Timeout(90000)
    public void testDebugModeBatchAtRowLimitFinishesNormally() throws Exception {
        String bootstrapServers = System.getenv("KAFKA_IT_BROKERS");
        String topic = "datay-it-limit-" + UUID.randomUUID();
        createTopic(bootstrapServers, topic);
        String[] payloads = new String[150];
        for (int i = 0; i < payloads.length; i++) {
            payloads[i] = "{\"idx\":" + i + "}";
        }
        publish(bootstrapServers, topic, payloads);

        ExecutionContext context = new ExecutionContext();
        context.setJobInstanceCode("kafka-it-limit-instance");
        context.setJobCode("kafka-it-limit-job");
        context.setDebugMode(true);
        context.setDebugRowLimit(100);

        KafkaConsumerComponent component = new KafkaConsumerComponent();
        component.setBootstrapServers(bootstrapServers);
        component.setTopic(topic);
        component.setGroupId("datay-it-limit-" + UUID.randomUUID());
        component.setBatchSize(100);
        component.setConsumerTimeoutMs(2000);
        component.setConnectionTimeoutMs(5000);
        component.setId("kafka-it-limit");
        component.setName("KafkaConsumer");
        component.setContext(context);
        component.setTaskLogger(new TaskLogger("kafka-it-limit-job", "kafka-it-limit-instance"));
        component.setActiveThreads(new AtomicInteger(1));

        // 采样上限异常必须原样抛出，由 FlowComponent.execute 当作正常结束，不能被包装成执行失败
        assertThrows(DebugLimitReachedException.class, () -> component.execute(new FlowFile()));

        DebugResult result = context.getDebugResults().get("kafka-it-limit");
        assertNotNull(result);
        assertEquals(100, result.getRows().size());
        assertTrue(result.isTruncated());
    }

    @Test
    @Timeout(90000)
    public void testStopClosesConsumerFromConsumerThread() throws Exception {
        String bootstrapServers = System.getenv("KAFKA_IT_BROKERS");
        String topic = "datay-it-stop-" + UUID.randomUUID();
        createTopic(bootstrapServers, topic);
        publish(bootstrapServers, topic, "a", "b", "c");

        KafkaConsumerComponent component = new KafkaConsumerComponent();
        component.setBootstrapServers(bootstrapServers);
        component.setTopic(topic);
        component.setGroupId("datay-it-stop-" + UUID.randomUUID());
        component.setConsumerTimeoutMs(200);
        component.setConnectionTimeoutMs(5000);
        component.setId("kafka-it-stop");
        component.setName("KafkaConsumer");

        ExecutionContext context = new ExecutionContext();
        context.setJobInstanceCode("kafka-it-stop-instance");
        context.setJobCode("kafka-it-stop-job");
        component.setContext(context);
        component.setTaskLogger(new TaskLogger("kafka-it-stop-job", "kafka-it-stop-instance"));
        component.setActiveThreads(new AtomicInteger(1));

        LinkedBlockingQueue<Object> outputQueue = new LinkedBlockingQueue<>(100);
        context.getConnections().put("kafka-it-stop_sink", outputQueue);
        component.getOutput().add(new Connection("kafka-it-stop", "sink", 0));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> component.execute(new FlowFile()));
        try {
            assertEquals(3, collectRecords(outputQueue, 3, 20000).size());
            assertDoesNotThrow(component::stop, "stop() 不应与其他线程并发关闭 KafkaConsumer");
            future.get(20, TimeUnit.SECONDS);
        } finally {
            component.stop();
            executor.shutdownNow();
        }
    }

    private KafkaConsumerComponent newDebugComponent(String bootstrapServers, String topic, String id) {
        ExecutionContext context = new ExecutionContext();
        context.setJobInstanceCode(id + "-instance");
        context.setJobCode(id + "-job");
        context.setDebugMode(true);

        KafkaConsumerComponent component = new KafkaConsumerComponent();
        component.setBootstrapServers(bootstrapServers);
        component.setTopic(topic);
        component.setGroupId(id + "-" + UUID.randomUUID());
        component.setAutoOffsetReset("earliest");
        component.setBatchSize(10);
        component.setConsumerTimeoutMs(2000);
        component.setConnectionTimeoutMs(5000);
        component.setId(id);
        component.setName("KafkaConsumer");
        component.setContext(context);
        component.setTaskLogger(new TaskLogger(id + "-job", id + "-instance"));
        component.setActiveThreads(new AtomicInteger(1));
        return component;
    }

    private String valueOf(DebugResult result, List<Object> row) {
        int index = result.getColumns().indexOf("_value");
        Object value = index >= 0 ? row.get(index) : null;
        return value == null ? null : String.valueOf(value);
    }

    private List<JSONObject> collectRecords(BlockingQueue<Object> outputQueue, int expected, long timeoutMs)
        throws InterruptedException {
        List<JSONObject> records = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (records.size() < expected && System.currentTimeMillis() < deadline) {
            FlowFile flowFile = (FlowFile) outputQueue.poll(200, TimeUnit.MILLISECONDS);
            if (flowFile == null || flowFile.getJsonArray() == null) {
                continue;
            }
            flowFile.getJsonArray().forEach(item -> records.add((JSONObject) item));
        }
        return records;
    }

    private void createTopic(String bootstrapServers, String topic) throws Exception {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
        try (AdminClient admin = AdminClient.create(props)) {
            admin.createTopics(Collections.singletonList(new NewTopic(topic, 1, (short) 1)))
                .all()
                .get(15, TimeUnit.SECONDS);
        }
    }

    private void publish(String bootstrapServers, String topic, String... payloads) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            List<Future<RecordMetadata>> futures = new ArrayList<>(payloads.length);
            for (String payload : payloads) {
                futures.add(producer.send(new ProducerRecord<>(topic, payload)));
            }
            // 一次性 flush，保证同一批测试消息尽量在同一个 produce 批次中写入，
            // 避免调试模式按“第一批消息”结束时只取到部分数据
            producer.flush();
            for (Future<RecordMetadata> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
        }
    }
}
