package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.data.job.Component.ComponentType;
import com.data.job.ComponentFactory;
import com.data.job.FlowFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KafkaProducerComponentTest extends ComponentTestBase {

    private KafkaProducerComponent component;

    @BeforeEach
    public void setUp() {
        super.setUpBase();
        component = new KafkaProducerComponent();
        initComponent(component, "test-kafka-producer", "KafkaProducer");
    }

    @Test
    @Timeout(60000)
    public void testComponentRegisteredAndSinkType() {
        assertEquals(ComponentType.SINK, component.getType());
        assertTrue(ComponentFactory.components.containsKey("KafkaProducer"));
        assertTrue(
            ComponentFactory.getFields("KafkaProducer").stream().anyMatch(field -> "bootstrapServers".equals(field.getKey())),
            "组件应暴露 bootstrapServers 配置字段供前端动态表单使用"
        );
    }

    @Test
    @Timeout(60000)
    public void testDefaultConfiguration() {
        assertEquals("localhost:9092", component.getBootstrapServers());
        assertNull(component.getTopic());
        assertNull(component.getKeyField());
        assertNull(component.getValueField());
        assertEquals(-1, component.getPartition());
        assertEquals("all", component.getAcks());
        assertEquals("none", component.getCompressionType());
        assertEquals(0, component.getLingerMs());
        assertEquals(3, component.getRetries());
        assertEquals(30000, component.getSendTimeoutMs());
    }

    @Test
    @Timeout(60000)
    public void testSetters() {
        component.setBootstrapServers(" kafka1:9092,kafka2:9092 ");
        component.setTopic("events");
        component.setKeyField("deviceId");
        component.setValueField("payload");
        component.setAcks("1");
        component.setCompressionType("gzip");
        component.setSendTimeoutMs("500");

        assertEquals("kafka1:9092,kafka2:9092", component.getBootstrapServers());
        assertEquals("events", component.getTopic());
        assertEquals("deviceId", component.getKeyField());
        assertEquals("payload", component.getValueField());
        assertEquals("1", component.getAcks());
        assertEquals("gzip", component.getCompressionType());
        assertEquals(1000, component.getSendTimeoutMs(), "发送超时不应小于 1000ms");
    }

    @Test
    @Timeout(60000)
    public void testNumericSettersFallback() {
        component.setPartition("2");
        assertEquals(2, component.getPartition());
        component.setPartition("invalid");
        assertEquals(-1, component.getPartition());

        component.setLingerMs("-10");
        assertEquals(0, component.getLingerMs());

        component.setRetries("invalid");
        assertEquals(3, component.getRetries());

        component.setSendTimeoutMs("invalid");
        assertEquals(30000, component.getSendTimeoutMs());
    }

    @Test
    @Timeout(60000)
    public void testDebugModeSkipsSending() {
        context.setDebugMode(true);
        component.setTopic("debug-topic");
        component.setBootstrapServers("127.0.0.1:1");

        FlowFile flowFile = new FlowFile();
        JSONArray array = new JSONArray();
        array.add("{\"deviceId\":\"d-1\"}");
        flowFile.setJsonArray(array);

        assertDoesNotThrow(() -> component.execute(flowFile));
    }

    @Test
    @Timeout(60000)
    public void testEndAndEmptyFlowFileAreIgnored() {
        component.setBootstrapServers("127.0.0.1:1");
        assertDoesNotThrow(() -> component.execute(createEndFlowFile()));
        assertDoesNotThrow(() -> component.execute(new FlowFile()));
    }
}
