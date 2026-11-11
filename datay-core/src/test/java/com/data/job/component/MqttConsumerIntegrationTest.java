package com.data.job.component;

import com.alibaba.fastjson2.JSONObject;
import com.data.job.Connection;
import com.data.job.DebugResult;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@EnabledIfEnvironmentVariable(named = "MQTT_IT_BROKER", matches = ".+")
public class MqttConsumerIntegrationTest {

    @Test
    @Timeout(90000)
    public void testConsumeJsonAndTextMessages() throws Exception {
        String broker = System.getenv("MQTT_IT_BROKER");
        String username = System.getenv("MQTT_IT_USERNAME");
        String password = System.getenv("MQTT_IT_PASSWORD");
        String topicFilter = "datay/it/+/" + UUID.randomUUID();
        String publishTopic = topicFilter.replace("+", "d-1");

        MqttConsumerComponent component = new MqttConsumerComponent();
        component.setBrokerUrl(broker);
        component.setTopic(topicFilter);
        component.setQos(1);
        component.setBatchSize(10);
        component.setConsumerTimeoutMs(200);
        component.setUsername(username);
        component.setPassword(password);
        component.setId("mqtt-it");
        component.setName("MqttConsumer");

        ExecutionContext context = new ExecutionContext();
        context.setJobInstanceCode("mqtt-it-instance");
        context.setJobCode("mqtt-it-job");
        component.setContext(context);
        component.setTaskLogger(new TaskLogger("mqtt-it-job", "mqtt-it-instance"));
        component.setActiveThreads(new AtomicInteger(1));

        LinkedBlockingQueue<Object> outputQueue = new LinkedBlockingQueue<>(100);
        context.getConnections().put("mqtt-it_sink", outputQueue);
        component.getOutput().add(new Connection("mqtt-it", "sink", 0));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> component.execute(new FlowFile()));
        try {
            awaitConnected(component, 15000);
            publish(broker, username, password, publishTopic,
                "{\"deviceId\":\"d-1\",\"temperature\":23.5}",
                "hello-mqtt");

            List<JSONObject> records = collectRecords(outputQueue, 2, 15000);
            assertEquals(2, records.size());

            JSONObject jsonRecord = records.stream()
                .filter(record -> "d-1".equals(record.getString("deviceId")))
                .findFirst()
                .orElse(null);
            assertNotNull(jsonRecord);
            assertEquals(23.5, jsonRecord.getDoubleValue("temperature"));
            assertEquals(publishTopic, jsonRecord.getString("_topic"));
            assertEquals(1, jsonRecord.getIntValue("_qos"));

            JSONObject textRecord = records.stream()
                .filter(record -> "hello-mqtt".equals(record.getString("_payload")))
                .findFirst()
                .orElse(null);
            assertNotNull(textRecord);
            assertEquals(publishTopic, textRecord.getString("_topic"));
        } finally {
            component.stop();
            executor.shutdownNow();
        }
        future.get(15, TimeUnit.SECONDS);
    }

    @Test
    @Timeout(90000)
    public void testDebugModeConsumesOneBatchFromRealBroker() throws Exception {
        String broker = System.getenv("MQTT_IT_BROKER");
        String username = System.getenv("MQTT_IT_USERNAME");
        String password = System.getenv("MQTT_IT_PASSWORD");
        String publishTopic = "datay/it/debug/" + UUID.randomUUID();

        MqttConsumerComponent component = new MqttConsumerComponent();
        component.setBrokerUrl(broker);
        component.setTopic(publishTopic);
        component.setQos(1);
        component.setBatchSize(10);
        component.setConsumerTimeoutMs(200);
        component.setDebugWaitTimeoutMs(30000);
        component.setUsername(username);
        component.setPassword(password);
        component.setId("mqtt-it-debug");
        component.setName("MqttConsumer");

        ExecutionContext context = new ExecutionContext();
        context.setJobInstanceCode("mqtt-it-debug-instance");
        context.setJobCode("mqtt-it-debug-job");
        context.setDebugMode(true);
        component.setContext(context);
        component.setTaskLogger(new TaskLogger("mqtt-it-debug-job", "mqtt-it-debug-instance"));
        component.setActiveThreads(new AtomicInteger(1));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> component.execute(new FlowFile()));
        try {
            awaitConnected(component, 15000);
            Thread.sleep(500);
            publish(broker, username, password, publishTopic,
                "{\"deviceId\":\"debug-1\",\"temperature\":21.5}");

            future.get(30, TimeUnit.SECONDS);
        } finally {
            component.stop();
            executor.shutdownNow();
        }

        DebugResult result = context.getDebugResults().get("mqtt-it-debug");
        assertNotNull(result, "调试模式应采集真实 MQTT 消息");
        assertEquals(1, result.getRows().size(), "调试模式接收一批数据后应结束");
        assertTrue(result.getColumns().contains("deviceId"));
        assertTrue(result.getColumns().contains("temperature"));
        assertEquals(publishTopic, result.getAttributes().get("_mqttTopic"));
        assertEquals("1", result.getAttributes().get("_recordCount"));
    }

    private void awaitConnected(MqttConsumerComponent component, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (component.isConnected()) {
                return;
            }
            Thread.sleep(100);
        }
        fail("MQTT 消费组件连接超时: " + System.getenv("MQTT_IT_BROKER"));
    }

    private void publish(String broker, String username, String password, String topic, String... payloads)
        throws Exception {
        try (MqttClient producer = new MqttClient(
            broker,
            "datay-it-publisher-" + UUID.randomUUID(),
            new MemoryPersistence()
        )) {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(false);
            if (username != null && !username.trim().isEmpty()) {
                options.setUserName(username.trim());
            }
            if (password != null) {
                options.setPassword(password.toCharArray());
            }
            producer.connect(options);
            try {
                for (String payload : payloads) {
                    MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
                    message.setQos(1);
                    producer.publish(topic, message);
                }
            } finally {
                if (producer.isConnected()) {
                    producer.disconnect();
                }
            }
        }
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
            assertTrue(flowFile.getAttribute("_recordCount") != null);
            for (Object item : flowFile.getJsonArray()) {
                if (item instanceof JSONObject) {
                    records.add((JSONObject) item);
                }
            }
        }
        return records;
    }
}
