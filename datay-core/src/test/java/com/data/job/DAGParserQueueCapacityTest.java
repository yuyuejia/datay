package com.data.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 连线队列容量的解析规则验证。
 *
 * <p>优先级：连线配置 > 流程默认值（flowConfig.queueCapacity） > 引擎默认值 10。
 */
public class DAGParserQueueCapacityTest {

    private static final String UNITS = """
        "units": [
          {".id": "a", ".name": "GenerateSequenceNumber", "countValue": "1"},
          {".id": "b", ".name": "LogFlowFile"}
        ]
        """;

    private int resolveCapacity(String flowConfig, String connectionExtra) throws Exception {
        String json = "{" + UNITS + ",\"connections\":[{\"sourceId\":\"a\",\"targetId\":\"b\",\"sourcePort\":0"
            + connectionExtra + "}]" + flowConfig + "}";
        DAGParser parser = new DAGParser();
        parser.parse(json);
        assertEquals(1, parser.getResolvedConnections().size());
        return parser.getResolvedConnections().get(0).getQueueCapacity();
    }

    @Test
    public void testDefaultCapacityIsTen() throws Exception {
        assertEquals(10, resolveCapacity("", ""));
    }

    @Test
    public void testConnectionCapacityOverridesDefault() throws Exception {
        assertEquals(25, resolveCapacity("", ",\"queueCapacity\":25"));
    }

    @Test
    public void testFlowConfigCapacityOverridesEngineDefault() throws Exception {
        assertEquals(30, resolveCapacity(",\"flowConfig\":{\"queueCapacity\":30}", ""));
    }

    @Test
    public void testConnectionCapacityOverridesFlowConfig() throws Exception {
        assertEquals(40, resolveCapacity(",\"flowConfig\":{\"queueCapacity\":30}", ",\"queueCapacity\":40"));
    }

    @Test
    public void testInvalidConnectionCapacityFallsBackToFlowDefault() throws Exception {
        assertEquals(30, resolveCapacity(",\"flowConfig\":{\"queueCapacity\":30}", ",\"queueCapacity\":0"));
        assertEquals(30, resolveCapacity(",\"flowConfig\":{\"queueCapacity\":30}", ",\"queueCapacity\":-5"));
    }

    @Test
    public void testCapacityIsCapped() throws Exception {
        assertEquals(DAGParser.MAX_QUEUE_CAPACITY, resolveCapacity("", ",\"queueCapacity\":99999999"));
    }
}
