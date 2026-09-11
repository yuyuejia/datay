package com.data.job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 调试模式的核心行为验证：数据量限制、输出采样、指定目标节点运行。
 */
public class DebugModeTest {

    private String buildJob() {
        return """
            {
              "units": [
                {".id": "gen", ".name": "GenerateSequenceNumber", "startValue": "1", "countValue": "1000", "dataFormat": "TEXT"},
                {".id": "log", ".name": "LogFlowFile"},
                {".id": "gen2", ".name": "GenerateSequenceNumber", "startValue": "1", "countValue": "1000", "dataFormat": "TEXT"},
                {".id": "log2", ".name": "LogFlowFile"}
              ],
              "connections": [
                {"sourceId": "gen", "targetId": "log", "sourcePort": 0},
                {"sourceId": "gen2", "targetId": "log2", "sourcePort": 0}
              ],
              "version": "1.0.0"
            }
            """;
    }

    private ETLFlowTask newDebugTask(int rowLimit, String targetNodeId) {
        ETLFlowTask runner = new ETLFlowTask();
        runner.getContext().setJobInstanceCode("debug-test-" + System.nanoTime());
        runner.getContext().setDebugMode(true);
        runner.getContext().setDebugRowLimit(rowLimit);
        runner.getContext().setDebugTargetNodeId(targetNodeId);
        return runner;
    }

    @Test
    @Timeout(60000)
    public void testWholeGraphRespectsRowLimitAndCapturesSamples() throws Exception {
        ETLFlowTask runner = newDebugTask(10, null);
        runner.runJob(buildJob());

        DebugResult gen = runner.getContext().getDebugResults().get("gen");
        assertNotNull(gen, "源组件应被采样");
        assertEquals(10, gen.getRows().size(), "源组件最多采样 10 行");
        assertTrue(gen.isTruncated(), "超过采样上限应标记为截断");

        assertTrue(gen.getAttributes().containsKey("_source"), "应采集关键属性");
        assertFalse(gen.getAttributes().containsKey("_start"), "不应采集内部开始信号属性");

        DebugResult log = runner.getContext().getDebugResults().get("log");
        assertNotNull(log, "下游组件应被采样");
        assertTrue(log.getRows().size() <= 10, "下游组件采样行数不应超过上限");
    }

    @Test
    @Timeout(60000)
    public void testFailureMessageContainsComponentAndRootCause() {
        ETLFlowTask runner = newDebugTask(10, null);
        String job = """
            {
              "units": [
                {".id": "gen", ".name": "GenerateSequenceNumber", "startValue": "1", "countValue": "3", "dataFormat": "INVALID_FORMAT"}
              ],
              "connections": [],
              "version": "1.0.0"
            }
            """;
        Exception ex = assertThrows(Exception.class, () -> runner.runJob(job));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("GenerateSequenceNumber"), "错误信息应包含失败组件名：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("不支持的数据格式"), "错误信息应包含根因：" + ex.getMessage());
    }

    @Test
    @Timeout(60000)
    public void testRealtimeInputComponentsProduceMockData() throws Exception {
        String kafkaJob = """
            {
              "units": [
                {".id": "kafka", ".name": "KafkaConsumer", "topic": "test-topic"}
              ],
              "connections": [],
              "version": "1.0.0"
            }
            """;
        ETLFlowTask kafkaRunner = newDebugTask(10, null);
        kafkaRunner.runJob(kafkaJob);
        DebugResult kafka = kafkaRunner.getContext().getDebugResults().get("kafka");
        assertNotNull(kafka, "Kafka 组件应生成模拟数据");
        assertEquals(1, kafka.getRows().size());
        assertEquals("test-topic", kafka.getAttributes().get("_kafkaTopic"));

        String httpJob = """
            {
              "units": [
                {".id": "http", ".name": "HttpListener", "port": "18080", "path": "/debug"}
              ],
              "connections": [],
              "version": "1.0.0"
            }
            """;
        ETLFlowTask httpRunner = newDebugTask(10, null);
        httpRunner.runJob(httpJob);
        DebugResult http = httpRunner.getContext().getDebugResults().get("http");
        assertNotNull(http, "HttpListener 组件应生成模拟数据");
        assertEquals(1, http.getRows().size());
        assertEquals("POST", http.getAttributes().get("_method"));
        assertEquals("/debug", http.getAttributes().get("_path"));

        String cdcJob = """
            {
              "units": [
                {".id": "cdc", ".name": "MySQLBinlogInput"}
              ],
              "connections": [],
              "version": "1.0.0"
            }
            """;
        ETLFlowTask cdcRunner = newDebugTask(10, null);
        cdcRunner.runJob(cdcJob);
        DebugResult cdc = cdcRunner.getContext().getDebugResults().get("cdc");
        assertNotNull(cdc, "MySQLBinlogInput 组件应生成模拟数据");
        assertEquals(1, cdc.getRows().size());
        assertEquals("INSERT", cdc.getAttributes().get("_eventType"));
        assertEquals("test_table", cdc.getAttributes().get("_table"));
    }

    @Test
    @Timeout(60000)
    public void testTargetNodeOnlyRunsUpstream() throws Exception {
        ETLFlowTask runner = newDebugTask(10, "log");
        runner.runJob(buildJob());

        assertNotNull(runner.getContext().getDebugResults().get("gen"), "目标节点的上游应运行");
        assertNotNull(runner.getContext().getDebugResults().get("log"), "目标节点应运行");
        assertNull(runner.getContext().getDebugResults().get("gen2"), "目标节点之外的分支不应运行");
        assertNull(runner.getContext().getDebugResults().get("log2"), "目标节点之外的分支不应运行");
    }
}
