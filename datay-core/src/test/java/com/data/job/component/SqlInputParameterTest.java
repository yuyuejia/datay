package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.FlowFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SqlInput 动态参数与上游 SQL 解析测试。
 */
class SqlInputParameterTest extends ComponentTestBase {

    private SqlInput component;

    @BeforeEach
    void setUp() {
        component = new SqlInput();
        initComponent(component, "sql-input-test", "SqlInput");
    }

    @Test
    void replacesAttrParameter() {
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("id_condition", "1,2,3");
        component.setSql("SELECT * FROM orders WHERE id IN (${attr:id_condition})");

        assertEquals(
            "SELECT * FROM orders WHERE id IN (1,2,3)",
            component.resolveSqlToExecute(flowFile)
        );
    }

    @Test
    void replacesShortParameter() {
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("id_condition", "1,2,3");
        component.setSql("SELECT * FROM orders WHERE id IN (${id_condition})");

        assertEquals(
            "SELECT * FROM orders WHERE id IN (1,2,3)",
            component.resolveSqlToExecute(flowFile)
        );
    }

    @Test
    void replacesParameterFromSingleJsonObject() {
        JSONObject row = new JSONObject();
        row.put("id_condition", "1,2,3");
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonObject(row);
        component.setSql("SELECT * FROM orders WHERE id IN (${id_condition})");

        assertEquals(
            "SELECT * FROM orders WHERE id IN (1,2,3)",
            component.resolveSqlToExecute(flowFile)
        );
    }

    @Test
    void replacesParameterFromSingleRowJsonArray() {
        JSONObject row = new JSONObject();
        row.put("id_condition", "1,2,3");
        JSONArray rows = new JSONArray();
        rows.add(row);
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(rows);
        component.setSql("SELECT * FROM orders WHERE id IN (${id_condition})");

        assertEquals(
            "SELECT * FROM orders WHERE id IN (1,2,3)",
            component.resolveSqlToExecute(flowFile)
        );
    }

    @Test
    void attributeTakesPrecedenceOverRowField() {
        JSONObject row = new JSONObject();
        row.put("id_condition", "row_value");
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonObject(row);
        flowFile.setAttribute("id_condition", "attr_value");
        component.setSql("SELECT '${attr:id_condition}' AS a, '${id_condition}' AS b");

        assertEquals(
            "SELECT 'attr_value' AS a, 'attr_value' AS b",
            component.resolveSqlToExecute(flowFile)
        );
    }

    @Test
    void doesNotUseRowVariablesForMultiRowArray() {
        JSONObject row = new JSONObject();
        row.put("id_condition", "1,2,3");
        JSONArray rows = new JSONArray();
        rows.add(row);
        rows.add(row);
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(rows);
        component.setSql("SELECT * FROM orders WHERE id IN (${id_condition})");

        assertEquals(
            "SELECT * FROM orders WHERE id IN (${id_condition})",
            component.resolveSqlToExecute(flowFile)
        );
    }

    @Test
    void replacesFlowFileData() {
        FlowFile flowFile = new FlowFile();
        flowFile.setTextData("orders_2026");
        component.setSql("SELECT * FROM ${FLOW_FILE_DATA}");

        assertEquals("SELECT * FROM orders_2026", component.resolveSqlToExecute(flowFile));
    }

    @Test
    void replacesAttrFlowFileData() {
        FlowFile flowFile = new FlowFile();
        flowFile.setTextData("orders_2026");
        component.setSql("SELECT * FROM ${attr:FLOW_FILE_DATA}");

        assertEquals("SELECT * FROM orders_2026", component.resolveSqlToExecute(flowFile));
    }

    @Test
    void replacesBuiltinParameter() {
        component.setSql("SELECT #{TIMESTAMP} AS ts");

        String resolved = component.resolveSqlToExecute(new FlowFile());

        assertFalse(resolved.contains("#{TIMESTAMP}"));
        assertTrue(resolved.matches("SELECT \\d+ AS ts"));
    }

    @Test
    void keepsUnknownParameter() {
        component.setSql("SELECT * FROM orders WHERE id = ${missing}");

        assertEquals(
            "SELECT * FROM orders WHERE id = ${missing}",
            component.resolveSqlToExecute(new FlowFile())
        );
    }

    @Test
    void configuredSqlTakesPrecedenceOverUpstreamSql() {
        component.setSql("SELECT 1");
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute(FlowFile.ATTRIBUTE_SQL, "SELECT 2");

        assertEquals("SELECT 1", component.resolveSqlToExecute(flowFile));
    }

    @Test
    void usesUpstreamSqlAndAppliesParameters() {
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("id_condition", "1,2,3");
        flowFile.setAttribute(
            FlowFile.ATTRIBUTE_SQL,
            "SELECT * FROM orders WHERE id IN (${attr:id_condition})"
        );
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, "orders_result");

        assertEquals(
            "SELECT * FROM orders WHERE id IN (1,2,3)",
            component.resolveSqlToExecute(flowFile)
        );
        assertEquals("orders_result", component.resolveResultTableName(flowFile));
    }

    @Test
    void resultTableNameFallsBackWhenUpstreamTableMissing() {
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute(FlowFile.ATTRIBUTE_SQL, "SELECT 1");

        assertEquals("sql_query_result", component.resolveResultTableName(flowFile));
    }

    @Test
    void configuredSqlUsesDefaultResultTableName() {
        component.setSql("SELECT 1");

        assertEquals("sql_query_result", component.resolveResultTableName(new FlowFile()));
    }

    @Test
    void returnsNullWhenNoSqlAvailable() {
        FlowFile startFlowFile = new FlowFile();
        startFlowFile.setAttribute("_start", true);

        assertNull(component.resolveSqlToExecute(startFlowFile));
    }

    @Test
    void nullFlowFileKeepsOriginalSql() {
        assertEquals("SELECT 1", component.applyDynamicParameters("SELECT 1", null));
    }

    @Test
    void endSignalDoesNotFailWithoutDatasource() {
        FlowFile endFlowFile = new FlowFile();
        endFlowFile.setAttribute("_end", true);

        assertDoesNotThrow(() -> component.execute(endFlowFile));
    }
}
