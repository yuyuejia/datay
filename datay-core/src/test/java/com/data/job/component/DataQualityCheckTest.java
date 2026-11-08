package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.Connection;
import com.data.job.DuckDBEngine;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataQualityCheckTest {

    @Test
    void streamModeShouldFailCurrentBatchAndWriteBatchResult(@TempDir Path tempDir) throws Exception {
        String instanceCode = "dq-stream-fail-" + System.currentTimeMillis();
        try {
            DataQualityCheck component = component(tempDir, instanceCode, "STREAM", "NOT_NULL", "ERROR");
            LinkedBlockingQueue<Object> queue = attachOutput(component);
            RuntimeException exception = assertThrows(RuntimeException.class, () -> component.execute(dataFlow()));
            assertTrue(exception.getMessage().contains("数据质量检核未通过"));
            assertTrue(queue.isEmpty());
            assertTrue(Files.exists(summaryPath(tempDir, instanceCode, "quality_batch_1_summary.csv")));
        } finally {
            DuckDBEngine.deleteDBFile(instanceCode);
        }
    }

    @Test
    void streamModeShouldPassAndForwardCurrentBatch(@TempDir Path tempDir) throws Exception {
        String instanceCode = "dq-stream-pass-" + System.currentTimeMillis();
        try {
            DataQualityCheck component = component(tempDir, instanceCode, "STREAM", "NOT_NULL_ID", "ERROR");
            LinkedBlockingQueue<Object> queue = attachOutput(component);
            assertDoesNotThrow(() -> component.execute(dataFlow()));
            assertEquals(1, queue.size());
            FlowFile forwarded = (FlowFile) queue.poll();
            assertEquals(2, forwarded.getJsonArray().size());
            assertFalse(Files.exists(summaryPath(tempDir, instanceCode, "quality_batch_1_summary.csv")));
        } finally {
            DuckDBEngine.deleteDBFile(instanceCode);
        }
    }

    @Test
    void fullModeShouldFailAfterAllDataArrivesAndNotForwardData(@TempDir Path tempDir) throws Exception {
        String instanceCode = "dq-full-fail-" + System.currentTimeMillis();
        try {
            DataQualityCheck component = component(tempDir, instanceCode, "FULL", "NOT_NULL", "ERROR");
            LinkedBlockingQueue<Object> queue = attachOutput(component);
            component.execute(dataFlow());
            assertTrue(queue.isEmpty());
            component.upstreamFinish = true;
            RuntimeException exception = assertThrows(RuntimeException.class, () -> component.execute(endFlow()));
            assertTrue(exception.getMessage().contains("数据质量检核未通过"));
            assertTrue(queue.isEmpty());
            assertTrue(Files.exists(summaryPath(tempDir, instanceCode, "quality_summary.csv")));
        } finally {
            DuckDBEngine.deleteDBFile(instanceCode);
        }
    }

    @Test
    void fullModeShouldForwardAllDataAfterCheckPassed(@TempDir Path tempDir) throws Exception {
        String instanceCode = "dq-full-pass-" + System.currentTimeMillis();
        try {
            DataQualityCheck component = component(tempDir, instanceCode, "FULL", "NOT_NULL_ID", "ERROR");
            LinkedBlockingQueue<Object> queue = attachOutput(component);
            component.execute(dataFlow());
            component.execute(dataFlow());
            assertTrue(queue.isEmpty());
            component.upstreamFinish = true;
            assertDoesNotThrow(() -> component.execute(endFlow()));

            assertEquals(1, queue.size());
            FlowFile forwarded = (FlowFile) queue.poll();
            assertEquals(4, forwarded.getJsonArray().size());
            assertEquals("source_table", forwarded.getAttribute(FlowFile.ATTRIBUTE_TABLE));
            assertTrue(Files.exists(summaryPath(tempDir, instanceCode, "quality_summary.csv")));
            assertTrue(Files.readString(summaryPath(tempDir, instanceCode, "quality_summary.csv")).contains("PASS"));
        } finally {
            DuckDBEngine.deleteDBFile(instanceCode);
        }
    }

    private DataQualityCheck component(Path tempDir, String instanceCode, String mode, String ruleType, String severity) {
        DataQualityCheck component = new DataQualityCheck();
        component.setId("dq_check");
        component.setName("DataQualityCheck");
        component.setCheckMode(mode);
        component.setRules(ruleJson(ruleType, severity));
        component.setFailOnError(true);
        component.setSampleLimit(10);
        component.setOutputDir("data-quality-test");
        component.setResultPrefix("quality");
        component.setTenantPrefix("tenants/test");
        component.setStorageType("local");
        component.setLocalBasePath(tempDir.toString());
        component.setWriteResult(true);

        ExecutionContext context = new ExecutionContext();
        context.setJobInstanceCode(instanceCode);
        context.setJobCode("test-job");
        component.setContext(context);
        component.setTaskLogger(new TaskLogger("test-job", instanceCode));
        component.setActiveThreads(new AtomicInteger(1));
        return component;
    }

    private LinkedBlockingQueue<Object> attachOutput(DataQualityCheck component) {
        component.getOutput().add(new Connection("dq_check", "sink", 0));
        LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        component.getContext().getConnections().put("dq_check_sink", queue);
        return queue;
    }

    private String ruleJson(String ruleType, String severity) {
        if ("NOT_NULL_ID".equals(ruleType)) {
            return "[{\"ruleCode\":\"not_null_id\",\"ruleName\":\"ID非空\",\"ruleType\":\"NOT_NULL\",\"column\":\"id\",\"severity\":\"" +
                severity +
                "\",\"maxInvalidRows\":0}]";
        }
        return "[{\"ruleCode\":\"not_null\",\"ruleName\":\"名称非空\",\"ruleType\":\"NOT_NULL\",\"column\":\"name\",\"severity\":\"" +
            severity +
            "\",\"maxInvalidRows\":0}]";
    }

    private FlowFile dataFlow() {
        TableMeta tableMeta = new TableMeta("source_table", java.util.List.of(new ColumnMeta("id", "BIGINT"), new ColumnMeta("name", "VARCHAR")));
        tableMeta.setDbType("duckdb");
        JSONArray rows = new JSONArray();
        JSONObject row1 = new JSONObject();
        row1.put("id", 1);
        row1.put("name", "Alice");
        rows.add(row1);
        JSONObject row2 = new JSONObject();
        row2.put("id", 2);
        row2.put("name", null);
        rows.add(row2);

        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(rows);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, tableMeta);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, "source_table");
        return flowFile;
    }

    private FlowFile endFlow() {
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_end", true);
        return flowFile;
    }

    private Path summaryPath(Path tempDir, String instanceCode, String fileName) {
        return tempDir.resolve("tenants/test/data-quality-test").resolve(instanceCode).resolve(fileName);
    }
}
