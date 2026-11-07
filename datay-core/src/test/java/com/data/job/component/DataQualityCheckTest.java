package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.DuckDBEngine;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataQualityCheckTest {

    @Test
    void shouldWriteResultFileAndFailWhenRuleNotPassed(@TempDir Path tempDir) throws Exception {
        String instanceCode = "dq-check-fail-" + System.currentTimeMillis();
        try {
            DataQualityCheck component = component(tempDir, instanceCode, "NOT_NULL", "ERROR");
            component.execute(dataFlow());
            component.upstreamFinish = true;
            RuntimeException exception = assertThrows(RuntimeException.class, () -> component.execute(endFlow()));
            assertTrue(exception.getMessage().contains("数据质量检核未通过"));

            Path summary = tempDir.resolve("tenants/test/data-quality-test").resolve(instanceCode).resolve("quality_summary.csv");
            assertTrue(Files.exists(summary));
            assertTrue(Files.readString(summary).contains("FAIL"));
        } finally {
            DuckDBEngine.deleteDBFile(instanceCode);
        }
    }

    @Test
    void shouldPassAndWriteResultFile(@TempDir Path tempDir) throws Exception {
        String instanceCode = "dq-check-pass-" + System.currentTimeMillis();
        try {
            DataQualityCheck component = component(tempDir, instanceCode, "ROW_COUNT_MIN", "ERROR");
            component.execute(dataFlow());
            component.upstreamFinish = true;
            assertDoesNotThrow(() -> component.execute(endFlow()));

            Path summary = tempDir.resolve("tenants/test/data-quality-test").resolve(instanceCode).resolve("quality_summary.csv");
            assertTrue(Files.exists(summary));
            assertTrue(Files.readString(summary).contains("PASS"));
        } finally {
            DuckDBEngine.deleteDBFile(instanceCode);
        }
    }

    private DataQualityCheck component(Path tempDir, String instanceCode, String ruleType, String severity) {
        DataQualityCheck component = new DataQualityCheck();
        component.setId("dq_check");
        component.setName("DataQualityCheck");
        component.setCheckMode("FLOW");
        component.setSourceTable("dq_source");
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

    private String ruleJson(String ruleType, String severity) {
        if ("ROW_COUNT_MIN".equals(ruleType)) {
            return "[{\"ruleCode\":\"count\",\"ruleName\":\"表非空\",\"ruleType\":\"ROW_COUNT_MIN\",\"config\":{\"minRows\":1},\"severity\":\"" +
                severity +
                "\",\"maxInvalidRows\":0}]";
        }
        return "[{\"ruleCode\":\"not_null\",\"ruleName\":\"名称非空\",\"ruleType\":\"NOT_NULL\",\"column\":\"name\",\"severity\":\"" +
            severity +
            "\",\"maxInvalidRows\":0}]";
    }

    private FlowFile dataFlow() {
        TableMeta tableMeta = new TableMeta("dq_source", java.util.List.of(new ColumnMeta("id", "BIGINT"), new ColumnMeta("name", "VARCHAR")));
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
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, "dq_source");
        return flowFile;
    }

    private FlowFile endFlow() {
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_end", true);
        return flowFile;
    }
}
