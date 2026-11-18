package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.job.Component.ComponentType;
import com.data.job.ComponentFactory;
import com.data.job.FlowFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedisInputTest extends ComponentTestBase {

    private RedisInput component;

    @BeforeEach
    void setUp() {
        super.setUpBase();
        component = new RedisInput();
        initComponent(component, "test-redis-input", "RedisInput");
    }

    @Test
    void componentRegisteredAsSource() {
        assertEquals(ComponentType.SOURCE, component.getType());
        assertTrue(ComponentFactory.components.containsKey("RedisInput"));
        assertTrue(
            ComponentFactory.getFields("RedisInput").stream().anyMatch(field -> "sourceId".equals(field.getKey())),
            "组件应暴露 sourceId 配置字段"
        );
    }

    @Test
    void defaultConfiguration() {
        assertNull(component.getSourceId());
        assertEquals("*", component.getKeyPattern());
        assertEquals("ALL", component.getTypeFilter());
        assertEquals(1000, component.getScanCount());
        assertEquals(500, component.getBatchSize());
        assertEquals(0, component.getMaxKeys());
        assertEquals(1000, component.getMaxEntriesPerKey());
        assertFalse(component.isIncludeTtl());
    }

    @Test
    void settersAndFallbacks() {
        component.setKeyPattern("user:*");
        component.setTypeFilter("hash");
        component.setScanCount("2000");
        component.setBatchSize("invalid");
        component.setMaxKeys("10");
        component.setMaxEntriesPerKey("-1");
        component.setIncludeTtl("true");

        assertEquals("user:*", component.getKeyPattern());
        assertEquals("hash", component.getTypeFilter());
        assertEquals(2000, component.getScanCount());
        assertEquals(500, component.getBatchSize());
        assertEquals(10, component.getMaxKeys());
        assertEquals(1, component.getMaxEntriesPerKey());
        assertTrue(component.isIncludeTtl());
    }

    @Test
    void endFlowFileIsIgnoredWithoutDatasource() {
        assertDoesNotThrow(() -> component.execute(createEndFlowFile()));
        assertDoesNotThrow(() -> component.execute(null));
    }
}
