package com.data.job.component;

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
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

        KafkaConsumerComponent component = new KafkaConsumerComponent();
        component.setBootstrapServers(bootstrapServers);
        component.setTopic(topic);
        component.setGroupId("datay-it-debug-" + UUID.randomUUID());
        component.setAutoOffsetReset("earliest");
        component.setBatchSize(10);
        component.setConsumerTimeoutMs(2000);
        component.setConnectionTimeoutMs(5000);
        component.setId("kafka-it-debug");
        component.setName("KafkaConsumer");

        ExecutionContext context = new ExecutionContext();
        context.setJobInstanceCode("kafka-it-debug-instance");
        context.setJobCode("kafka-it-debug-job");
        context.setDebugMode(true);
        component.setContext(context);
        component.setTaskLogger(new TaskLogger("kafka-it-debug-job", "kafka-it-debug-instance"));
        component.setActiveThreads(new AtomicInteger(1));

        component.execute(new FlowFile());

        DebugResult result = context.getDebugResults().get("kafka-it-debug");
        assertNotNull(result, "调试模式应采集真实 Kafka 消息");
        assertEquals(3, result.getRows().size(), "调试模式接收一批数据后应结束");
        assertTrue(result.getColumns().contains("deviceId"));
        assertTrue(result.getColumns().contains("temperature"));
        assertEquals(topic, result.getAttributes().get("_kafkaTopic"));
        assertEquals("3", result.getAttributes().get("_recordCount"));
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
            for (String payload : payloads) {
                producer.send(new ProducerRecord<>(topic, payload)).get(15, TimeUnit.SECONDS);
            }
            producer.flush();
        }
    }
}
