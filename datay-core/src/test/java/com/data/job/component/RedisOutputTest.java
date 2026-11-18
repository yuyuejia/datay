package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.fastjson2.JSON;
import com.data.job.Component.ComponentType;
import com.data.job.ComponentFactory;
import com.data.job.FlowFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedisOutputTest extends ComponentTestBase {

    private RedisOutput component;

    @BeforeEach
    void setUp() {
        super.setUpBase();
        component = new RedisOutput();
        initComponent(component, "test-redis-output", "RedisOutput");
    }

    @Test
    void componentRegisteredAsSink() {
        assertEquals(ComponentType.SINK, component.getType());
        assertTrue(ComponentFactory.components.containsKey("RedisOutput"));
        assertTrue(
            ComponentFactory.getFields("RedisOutput").stream().anyMatch(field -> "sourceId".equals(field.getKey())),
            "组件应暴露 sourceId 配置字段"
        );
    }

    @Test
    void defaultConfiguration() {
        assertEquals("STRING", component.getDataType());
        assertEquals("key", component.getKeyField());
        assertEquals("field", component.getFieldField());
        assertEquals("score", component.getScoreField());
        assertEquals(0, component.getTtlSeconds());
        assertEquals("APPEND", component.getWriteMode());
        assertEquals(500, component.getBatchSize());
    }

    @Test
    void settersAndFallbacks() {
        component.setDataType("hash");
        component.setKeyField("id");
        component.setKeyPrefix("sync:");
        component.setValueField("payload");
        component.setTtlSeconds("-5");
        component.setBatchSize("invalid");

        assertEquals("hash", component.getDataType());
        assertEquals("id", component.getKeyField());
        assertEquals("sync:", component.getKeyPrefix());
        assertEquals("payload", component.getValueField());
        assertEquals(0, component.getTtlSeconds());
        assertEquals(500, component.getBatchSize());
    }

    @Test
    void debugModeSkipsWritingWithoutDatasource() {
        context.setDebugMode(true);
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(JSON.parseArray("[{\"key\":\"k1\",\"value\":\"v1\"}]"));

        assertDoesNotThrow(() -> component.execute(flowFile));
    }

    @Test
    void missingDatasourceFailsOutsideDebugMode() {
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(JSON.parseArray("[{\"key\":\"k1\",\"value\":\"v1\"}]"));

        assertThrows(IllegalArgumentException.class, () -> component.execute(flowFile));
    }

    @Test
    void endFlowFileIsIgnored() {
        assertDoesNotThrow(() -> component.execute(createEndFlowFile()));
    }
}
