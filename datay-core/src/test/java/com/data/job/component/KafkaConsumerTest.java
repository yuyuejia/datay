package com.data.job.component;

import com.data.job.Component.ComponentType;
import com.data.job.ExecutionContext;
import com.data.job.TaskLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KafkaConsumerTest {

    private KafkaConsumerComponent component;
    private ExecutionContext context;
    private TaskLogger taskLogger;

    @BeforeEach
    public void setUp() {
        component = new KafkaConsumerComponent();
        
        context = new ExecutionContext();
        context.setJobInstanceCode("test-instance");
        context.setJobCode("test-job");
        
        taskLogger = new TaskLogger("test-job", "test-instance");
        
        component.setContext(context);
        component.setTaskLogger(taskLogger);
        component.setId("test-kafka-consumer");
        component.setName("KafkaConsumer");
        component.setActiveThreads(new AtomicInteger(1));
    }

    @Test
    @Timeout(60000)
    public void testComponentType() {
        assertEquals(ComponentType.SOURCE, component.getType());
    }

    @Test
    @Timeout(60000)
    public void testDefaultConfiguration() {
        assertEquals("localhost:9092", component.getBootstrapServers());
        assertEquals("datay-consumer", component.getGroupId());
        assertEquals("earliest", component.getAutoOffsetReset());
        assertTrue(component.isEnableAutoCommit());
        assertEquals(100, component.getBatchSize());
        assertEquals(30000, component.getConsumerTimeoutMs());
        assertEquals(5000, component.getConnectionTimeoutMs());
        assertEquals(-1, component.getPartition());
        assertNull(component.getStartOffset());
        assertNull(component.getStartTimestamp());
    }

    @Test
    @Timeout(60000)
    public void testSetBootstrapServers() {
        component.setBootstrapServers("kafka1:9092,kafka2:9092");
        assertEquals("kafka1:9092,kafka2:9092", component.getBootstrapServers());
    }

    @Test
    @Timeout(60000)
    public void testSetTopic() {
        component.setTopic("test-topic");
        assertEquals("test-topic", component.getTopic());
    }

    @Test
    @Timeout(60000)
    public void testSetGroupId() {
        component.setGroupId("my-consumer-group");
        assertEquals("my-consumer-group", component.getGroupId());
    }

    @Test
    @Timeout(60000)
    public void testSetAutoOffsetReset() {
        component.setAutoOffsetReset("latest");
        assertEquals("latest", component.getAutoOffsetReset());
    }

    @Test
    @Timeout(60000)
    public void testSetEnableAutoCommit() {
        component.setEnableAutoCommit(false);
        assertEquals(false, component.isEnableAutoCommit());
        
        component.setEnableAutoCommit("true");
        assertEquals(true, component.isEnableAutoCommit());
        
        component.setEnableAutoCommit("false");
        assertEquals(false, component.isEnableAutoCommit());
    }

    @Test
    @Timeout(60000)
    public void testSetBatchSize() {
        component.setBatchSize(500);
        assertEquals(500, component.getBatchSize());
        
        component.setBatchSize("200");
        assertEquals(200, component.getBatchSize());
        
        component.setBatchSize("invalid");
        assertEquals(100, component.getBatchSize());
    }

    @Test
    @Timeout(60000)
    public void testSetConsumerTimeoutMs() {
        component.setConsumerTimeoutMs(60000);
        assertEquals(60000, component.getConsumerTimeoutMs());
        
        component.setConsumerTimeoutMs("120000");
        assertEquals(120000, component.getConsumerTimeoutMs());
        
        component.setConsumerTimeoutMs("invalid");
        assertEquals(30000, component.getConsumerTimeoutMs());
    }

    @Test
    @Timeout(60000)
    public void testSetPartition() {
        component.setPartition(0);
        assertEquals(0, component.getPartition());
        
        component.setPartition("1");
        assertEquals(1, component.getPartition());
        
        component.setPartition("invalid");
        assertEquals(-1, component.getPartition());
    }

    @Test
    @Timeout(60000)
    public void testSetStartOffset() {
        component.setStartOffset(100L);
        assertEquals(100L, component.getStartOffset());
        
        component.setStartOffset("200");
        assertEquals(200L, component.getStartOffset());
        
        component.setStartOffset("invalid");
        assertNull(component.getStartOffset());
    }

    @Test
    @Timeout(60000)
    public void testSetStartTimestamp() {
        component.setStartTimestamp(System.currentTimeMillis());
        assertTrue(component.getStartTimestamp() > 0);
        
        component.setStartTimestamp("1609459200000");
        assertEquals(1609459200000L, component.getStartTimestamp());
        
        component.setStartTimestamp("invalid");
        assertNull(component.getStartTimestamp());
    }

    @Test
    @Timeout(60000)
    public void testSetConnectionTimeoutMs() {
        component.setConnectionTimeoutMs(10000);
        assertEquals(10000, component.getConnectionTimeoutMs());
        
        component.setConnectionTimeoutMs("15000");
        assertEquals(15000, component.getConnectionTimeoutMs());
        
        component.setConnectionTimeoutMs("invalid");
        assertEquals(5000, component.getConnectionTimeoutMs());
    }

    @Test
    @Timeout(60000)
    public void testStopMethod() {
        component.stop();
    }
}