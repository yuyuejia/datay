package com.data.job.component;

import com.alibaba.fastjson2.JSONObject;
import com.data.job.Component.ComponentType;
import com.data.job.ComponentFactory;
import com.data.job.DebugResult;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MqttConsumerTest {

    private MqttConsumerComponent component;
    private ExecutionContext context;

    @BeforeEach
    public void setUp() {
        component = new MqttConsumerComponent();

        context = new ExecutionContext();
        context.setJobInstanceCode("test-instance");
        context.setJobCode("test-job");

        TaskLogger taskLogger = new TaskLogger("test-job", "test-instance");
        component.setContext(context);
        component.setTaskLogger(taskLogger);
        component.setId("test-mqtt-consumer");
        component.setName("MqttConsumer");
        component.setActiveThreads(new AtomicInteger(1));
    }

    @Test
    @Timeout(60000)
    public void testComponentTypeAndRegistration() {
        assertEquals(ComponentType.SOURCE, component.getType());
        assertNotNull(ComponentFactory.getDescriptor("MqttConsumer"));
        assertEquals("MQTT消费", ComponentFactory.getDescriptor("MqttConsumer").getName());
        assertEquals("实时输入", ComponentFactory.getDescriptor("MqttConsumer").getGroup());
    }

    @Test
    @Timeout(60000)
    public void testDefaultConfiguration() {
        assertEquals("tcp://localhost:1883", component.getBrokerUrl());
        assertNull(component.getClientId());
        assertNull(component.getTopic());
        assertEquals(1, component.getQos());
        assertNull(component.getUsername());
        assertNull(component.getPassword());
        assertTrue(component.isCleanSession());
        assertTrue(component.isAutomaticReconnect());
        assertEquals(60, component.getKeepAliveInterval());
        assertEquals(10, component.getConnectionTimeout());
        assertEquals(100, component.getMaxInflight());
        assertEquals(100, component.getBatchSize());
        assertEquals(1000, component.getConsumerTimeoutMs());
        assertEquals(10000, component.getMaxQueueSize());
        assertEquals("AUTO", component.getPayloadFormat());
    }

    @Test
    @Timeout(60000)
    public void testSetters() {
        component.setBrokerUrl("ssl://broker.example.com:8883");
        assertEquals("ssl://broker.example.com:8883", component.getBrokerUrl());

        component.setClientId("client-1");
        assertEquals("client-1", component.getClientId());

        component.setTopic("device/+/telemetry");
        assertEquals("device/+/telemetry", component.getTopic());

        component.setQos(2);
        assertEquals(2, component.getQos());
        component.setQos("0");
        assertEquals(0, component.getQos());
        component.setQos("invalid");
        assertEquals(1, component.getQos());

        component.setUsername("user");
        assertEquals("user", component.getUsername());
        component.setPassword("secret");
        assertEquals("secret", component.getPassword());

        component.setCleanSession(false);
        assertFalse(component.isCleanSession());
        component.setCleanSession("true");
        assertTrue(component.isCleanSession());

        component.setAutomaticReconnect(false);
        assertFalse(component.isAutomaticReconnect());
        component.setAutomaticReconnect("true");
        assertTrue(component.isAutomaticReconnect());

        component.setKeepAliveInterval("120");
        assertEquals(120, component.getKeepAliveInterval());
        component.setKeepAliveInterval("invalid");
        assertEquals(60, component.getKeepAliveInterval());

        component.setConnectionTimeout("30");
        assertEquals(30, component.getConnectionTimeout());
        component.setConnectionTimeout("invalid");
        assertEquals(10, component.getConnectionTimeout());

        component.setMaxInflight("200");
        assertEquals(200, component.getMaxInflight());
        component.setMaxInflight("invalid");
        assertEquals(100, component.getMaxInflight());

        component.setBatchSize("500");
        assertEquals(500, component.getBatchSize());
        component.setBatchSize("invalid");
        assertEquals(100, component.getBatchSize());

        component.setConsumerTimeoutMs("2000");
        assertEquals(2000, component.getConsumerTimeoutMs());
        component.setConsumerTimeoutMs("invalid");
        assertEquals(1000, component.getConsumerTimeoutMs());

        component.setMaxQueueSize("5000");
        assertEquals(5000, component.getMaxQueueSize());
        component.setMaxQueueSize("invalid");
        assertEquals(10000, component.getMaxQueueSize());

        component.setPayloadFormat("JSON");
        assertEquals("JSON", component.getPayloadFormat());
    }

    @Test
    @Timeout(60000)
    public void testJsonPayloadIsMergedIntoRecord() {
        component.setPayloadFormat("AUTO");
        MqttMessage message = message("{\"deviceId\":\"d-1\",\"temperature\":23.5}");
        message.setQos(1);
        message.setRetained(true);

        JSONObject record = component.toRecord("device/d-1", message);

        assertEquals("d-1", record.getString("deviceId"));
        assertEquals(23.5, record.getDoubleValue("temperature"));
        assertEquals("device/d-1", record.getString("_topic"));
        assertEquals(1, record.getIntValue("_qos"));
        assertTrue(record.getBooleanValue("_retained"));
        assertTrue(record.containsKey("_timestamp"));
    }

    @Test
    @Timeout(60000)
    public void testTextPayloadIsStoredAsPayloadField() {
        component.setPayloadFormat("TEXT");
        JSONObject record = component.toRecord("device/d-1", message("hello mqtt"));

        assertEquals("hello mqtt", record.getString("_payload"));
        assertFalse(record.containsKey("deviceId"));
    }

    @Test
    @Timeout(60000)
    public void testAutoFormatFallsBackToText() {
        component.setPayloadFormat("AUTO");
        JSONObject record = component.toRecord("device/d-1", message("not-json"));

        assertEquals("not-json", record.getString("_payload"));
    }

    @Test
    @Timeout(60000)
    public void testSingleObjectJsonArrayPayloadIsMerged() {
        component.setPayloadFormat("AUTO");
        JSONObject record = component.toRecord("device/d-1", message("[{\"id\":7}]"));

        assertEquals(7, record.getIntValue("id"));
    }

    private MqttMessage message(String payload) {
        return new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @Timeout(60000)
    public void testDebugModeGeneratesMockMessage() {
        context.setDebugMode(true);
        component.setTopic("debug/topic");

        component.execute(new FlowFile());

        DebugResult result = context.getDebugResults().get("test-mqtt-consumer");
        assertNotNull(result);
        assertEquals(1, result.getRows().size());
        assertEquals("debug/topic", result.getAttributes().get("_mqttTopic"));
        assertEquals("1", result.getAttributes().get("_recordCount"));
    }

    @Test
    @Timeout(60000)
    public void testMissingTopicFailsFast() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> component.execute(new FlowFile()));
        assertTrue(ex.getMessage().contains("订阅主题"));
    }

    @Test
    @Timeout(60000)
    public void testParallelismGreaterThanOneFailsFast() {
        component.setTopic("debug/topic");
        component.setParallelism(2);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> component.execute(new FlowFile()));
        assertTrue(ex.getMessage().contains("并发数"));
    }

    @Test
    @Timeout(60000)
    public void testStopBeforeExecute() {
        component.stop();
    }
}
