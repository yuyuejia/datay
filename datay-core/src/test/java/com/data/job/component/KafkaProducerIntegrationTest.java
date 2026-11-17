package com.data.job.component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ETLFlowTask;
import com.data.job.FlowFile;
import com.data.job.TaskInstance;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "KAFKA_IT_BROKERS", matches = ".+")
public class KafkaProducerIntegrationTest extends ComponentTestBase {

    private final String brokers = System.getenv("KAFKA_IT_BROKERS");

    @Test
    @Timeout(90000)
    public void testSendJsonRecordsWithKeyField() throws Exception {
        String topic = "datay-producer-it-" + UUID.randomUUID();
        createTopic(topic, 1);

        KafkaProducerComponent component = new KafkaProducerComponent();
        initComponent(component, "kafka-producer-it", "KafkaProducer");
        component.setBootstrapServers(brokers);
        component.setTopic(topic);
        component.setKeyField("deviceId");
        component.setAcks("all");

        FlowFile flowFile = new FlowFile();
        JSONArray array = JSON.parseArray(
            "[{\"deviceId\":\"d-1\",\"temperature\":23.5},{\"deviceId\":\"d-2\",\"temperature\":24.5}]"
        );
        flowFile.setJsonArray(array);

        component.execute(flowFile);

        List<ConsumerRecord<String, String>> records = consume(topic, 2);
        assertEquals(2, records.size());

        ConsumerRecord<String, String> first = records.get(0);
        assertEquals("d-1", first.key());
        JSONObject firstValue = JSON.parseObject(first.value());
        assertEquals("d-1", firstValue.getString("deviceId"));
        assertEquals(23.5, firstValue.getDoubleValue("temperature"));

        ConsumerRecord<String, String> second = records.get(1);
        assertEquals("d-2", second.key());
        assertEquals("d-2", JSON.parseObject(second.value()).getString("deviceId"));
    }

    @Test
    @Timeout(90000)
    public void testSendTextFieldAsSingleMessage() throws Exception {
        String topic = "datay-producer-text-" + UUID.randomUUID();
        createTopic(topic, 1);

        KafkaProducerComponent component = new KafkaProducerComponent();
        initComponent(component, "kafka-producer-text", "KafkaProducer");
        component.setBootstrapServers(brokers);
        component.setTopic(topic);

        component.execute(createTextFlowFile("hello-kafka-producer"));

        List<ConsumerRecord<String, String>> records = consume(topic, 1);
        assertEquals(1, records.size());
        assertEquals("hello-kafka-producer", records.get(0).value());
    }

    @Test
    @Timeout(90000)
    public void testSendValueFieldOnly() throws Exception {
        String topic = "datay-producer-field-" + UUID.randomUUID();
        createTopic(topic, 1);

        KafkaProducerComponent component = new KafkaProducerComponent();
        initComponent(component, "kafka-producer-field", "KafkaProducer");
        component.setBootstrapServers(brokers);
        component.setTopic(topic);
        component.setValueField("temperature");

        FlowFile flowFile = new FlowFile();
        flowFile.setJsonObject(JSON.parseObject("{\"deviceId\":\"d-1\",\"temperature\":23.5}"));

        component.execute(flowFile);

        List<ConsumerRecord<String, String>> records = consume(topic, 1);
        assertEquals(1, records.size());
        assertEquals("23.5", records.get(0).value());
    }

    @Test
    @Timeout(90000)
    public void testSendToSpecifiedPartition() throws Exception {
        String topic = "datay-producer-part-" + UUID.randomUUID();
        createTopic(topic, 2);

        KafkaProducerComponent component = new KafkaProducerComponent();
        initComponent(component, "kafka-producer-part", "KafkaProducer");
        component.setBootstrapServers(brokers);
        component.setTopic(topic);
        component.setPartition(1);

        FlowFile flowFile = new FlowFile();
        JSONArray array = JSON.parseArray("[{\"seq\":1},{\"seq\":2}]");
        flowFile.setJsonArray(array);

        component.execute(flowFile);

        List<ConsumerRecord<String, String>> records = consume(topic, 2);
        assertEquals(2, records.size());
        assertTrue(records.stream().allMatch(record -> record.partition() == 1), "所有消息都应写入指定分区 1");
    }

    @Test
    @Timeout(90000)
    public void testSendLargeBatch() throws Exception {
        String topic = "datay-producer-batch-" + UUID.randomUUID();
        createTopic(topic, 1);

        KafkaProducerComponent component = new KafkaProducerComponent();
        initComponent(component, "kafka-producer-batch", "KafkaProducer");
        component.setBootstrapServers(brokers);
        component.setTopic(topic);

        int total = 200;
        JSONArray array = new JSONArray();
        for (int i = 0; i < total; i++) {
            JSONObject record = new JSONObject();
            record.put("seq", i);
            array.add(record);
        }
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(array);

        component.execute(flowFile);

        List<ConsumerRecord<String, String>> records = consume(topic, total);
        assertEquals(total, records.size(), "大批量数据应全部发送成功");
        assertEquals(0, JSON.parseObject(records.get(0).value()).getIntValue("seq"));
        assertEquals(total - 1, JSON.parseObject(records.get(records.size() - 1).value()).getIntValue("seq"));
    }

    @Test
    @Timeout(120000)
    public void testEndToEndKafkaConsumerToProducer() throws Exception {
        String inputTopic = "datay-e2e-in-" + UUID.randomUUID();
        String outputTopic = "datay-e2e-out-" + UUID.randomUUID();
        createTopic(inputTopic, 1);
        createTopic(outputTopic, 1);
        publish(inputTopic,
            "{\"eventId\":\"e-1\",\"deviceId\":\"d-1\",\"temperature\":23.5}",
            "{\"eventId\":\"e-2\",\"deviceId\":\"d-2\",\"temperature\":24.5}");

        String jobCode = "kafka-producer-e2e-" + UUID.randomUUID();
        String job = "{\"units\":["
            + "{\".id\":\"n1\",\".name\":\"KafkaConsumer\",\"bootstrapServers\":\"" + brokers + "\","
            + "\"topic\":\"" + inputTopic + "\",\"groupId\":\"datay-e2e-" + UUID.randomUUID() + "\","
            + "\"autoOffsetReset\":\"earliest\",\"batchSize\":10,\"consumerTimeoutMs\":500},"
            + "{\".id\":\"n2\",\".name\":\"KafkaProducer\",\"bootstrapServers\":\"" + brokers + "\","
            + "\"topic\":\"" + outputTopic + "\",\"keyField\":\"deviceId\",\"acks\":\"all\"}"
            + "],\"connections\":[{\"sourceId\":\"n1\",\"targetId\":\"n2\",\"sourcePort\":0,\"queueCapacity\":10}],"
            + "\"version\":\"1.0.0\"}";

        ETLFlowTask task = new ETLFlowTask(taskInstance(jobCode), null);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Future<?> future = executor.submit(() -> {
            try {
                task.runJob(job);
            } catch (Throwable e) {
                error.set(e);
            }
        });
        try {
            List<ConsumerRecord<String, String>> records = consume(outputTopic, 2);
            assertEquals(2, records.size(), "端到端任务应把 Kafka 消息转发到输出主题");
            assertEquals("d-1", records.get(0).key());
            assertEquals("e-1", JSON.parseObject(records.get(0).value()).getString("eventId"));
            assertEquals("d-2", records.get(1).key());
            assertEquals("e-2", JSON.parseObject(records.get(1).value()).getString("eventId"));
        } finally {
            task.cancel();
            try {
                future.get(15, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // 流式任务取消后正常结束
            }
            executor.shutdownNow();
            java.nio.file.Files.deleteIfExists(
                java.nio.file.Path.of(System.getProperty("user.dir"), "log", "status_" + jobCode + ".json")
            );
        }
        if (error.get() != null && !isCancellation(error.get())) {
            throw new AssertionError("端到端任务异常结束: " + error.get().getMessage(), error.get());
        }
    }

    private TaskInstance taskInstance(String jobCode) {
        TaskInstance instance = new TaskInstance();
        instance.setId(jobCode);
        instance.setInstanceCode(jobCode + "-" + System.currentTimeMillis());
        instance.setJobCode(jobCode);
        instance.setJobName(jobCode);
        return instance;
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

    private void publish(String topic, String... payloads) throws Exception {
        Properties props = new Properties();
        props.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            org.apache.kafka.common.serialization.StringSerializer.class.getName());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            org.apache.kafka.common.serialization.StringSerializer.class.getName());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG, "all");
        try (org.apache.kafka.clients.producer.KafkaProducer<String, String> producer =
                 new org.apache.kafka.clients.producer.KafkaProducer<>(props)) {
            for (String payload : payloads) {
                producer.send(new org.apache.kafka.clients.producer.ProducerRecord<>(topic, payload));
            }
            producer.flush();
        }
    }

    private void createTopic(String topic, int partitions) throws Exception {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
        try (AdminClient admin = AdminClient.create(props)) {
            admin.createTopics(Collections.singletonList(new NewTopic(topic, partitions, (short) 1)))
                .all()
                .get(15, TimeUnit.SECONDS);
        }
    }

    private List<ConsumerRecord<String, String>> consume(String topic, int expected) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "datay-producer-verify-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));
            long deadline = System.currentTimeMillis() + 30000;
            while (records.size() < expected && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : polled) {
                    records.add(record);
                }
            }
        }
        assertNotNull(records, "应能从 Kafka 读取到生产组件发送的消息");
        return records;
    }
}
