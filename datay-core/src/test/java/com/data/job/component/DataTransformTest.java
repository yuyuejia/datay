package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.Component;
import com.data.job.Component.ComponentType;
import com.data.job.ComponentFactory;
import com.data.job.DuckDBEngine;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DataTransformTest {

    private static final String DB_FILE = "test-data-transform-instance";

    private CapturingDataTransform component;
    private ExecutionContext context;

    @BeforeEach
    public void setUp() {
        component = new CapturingDataTransform();

        context = new ExecutionContext();
        context.setJobInstanceCode(DB_FILE);
        context.setJobCode("test-data-transform-job");

        TaskLogger taskLogger = new TaskLogger("test-data-transform-job", DB_FILE);

        component.setContext(context);
        component.setTaskLogger(taskLogger);
        component.setId("dt1");
        component.setName("DataTransform");
        component.setActiveThreads(new AtomicInteger(1));
    }

    @Test
    @Timeout(60000)
    public void testComponentType() {
        assertEquals(ComponentType.OPERATOR, component.getType());
        assertEquals("tmp_dt1", component.resolveOutputTable());
    }

    @Test
    @Timeout(60000)
    public void testFactoryBindsConfig() {
        JSONArray rules = new JSONArray();
        JSONObject upper = new JSONObject();
        upper.put("type", "upper");
        upper.put("column", "name");
        upper.put("targetColumn", "name_upper");
        rules.add(upper);

        Map<String, Object> params = new HashMap<>();
        params.put(".id", "dt2");
        params.put(".name", "DataTransform");
        params.put("rules", rules);
        params.put("outputTable", "my_out");

        Component created = ComponentFactory.create("DataTransform", params);

        assertTrue(created instanceof DataTransform);
        assertEquals("my_out", ((DataTransform) created).getOutputTable());
    }

    @Test
    @Timeout(60000)
    public void testTransformEndToEnd() throws Exception {
        JSONArray records = new JSONArray();
        records.add(row(1, "alice", 20, "95.5"));
        records.add(row(2, "bob", 17, "88"));

        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(records);

        component.setRules(buildRules());
        component.setOutputTable("transform_out");

        component.execute(flowFile);

        assertEquals(1, component.outputs.size());
        FlowFile outFile = component.outputs.get(0);
        JSONArray output = outFile.getJsonArray();
        assertNotNull(output);
        assertEquals(1, output.size());

        JSONObject row = output.getJSONObject(0);
        assertEquals(1, row.getIntValue("id"));
        assertEquals("alice", row.getString("name"));
        assertEquals(20, row.getIntValue("age"));
        assertEquals("ALICE", row.getString("name_upper"));
        assertEquals(95.5d, row.getDoubleValue("score_num"), 0.0001d);

        try (Connection conn = DuckDBEngine.getInstance().getConnection(DB_FILE);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS main.tmp_dt1");
        } finally {
            component.outputs.clear();
            DuckDBEngine.deleteDBFile(DB_FILE);
        }
    }

    @Test
    @Timeout(60000)
    public void testFiltersConfigWithOrLogic() throws Exception {
        JSONArray records = new JSONArray();
        records.add(row(1, "alice", 20, "95.5"));
        records.add(row(2, "bob", 17, "88"));
        records.add(row(3, "carol", 30, "70"));
        records.add(row(4, "dave", 10, "60"));

        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(records);

        JSONArray filters = new JSONArray();
        JSONObject ageFilter = new JSONObject();
        ageFilter.put("column", "age");
        ageFilter.put("operator", ">=");
        // 前端输入框为文本，数值型值以字符串传递
        ageFilter.put("value", "18");
        filters.add(ageFilter);
        JSONObject nameFilter = new JSONObject();
        nameFilter.put("expression", "name = 'bob'");
        filters.add(nameFilter);

        component.setFilters(filters);
        component.setFilterLogic("OR");
        component.execute(flowFile);

        assertEquals(1, component.outputs.size());
        JSONArray output = component.outputs.get(0).getJsonArray();
        assertEquals(3, output.size());

        try (Connection conn = DuckDBEngine.getInstance().getConnection(DB_FILE);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS main.tmp_dt1");
        } finally {
            component.outputs.clear();
            DuckDBEngine.deleteDBFile(DB_FILE);
        }
    }

    @Test
    @Timeout(60000)
    public void testDefaultOutputTableUsesUpstreamTableName() throws Exception {
        JSONArray records = new JSONArray();
        records.add(row(1, "alice", 20, "95.5"));

        TableMeta meta = new TableMeta("upstream_orders");
        meta.setDbType("duckdb");
        meta.addColumn(new ColumnMeta("id", "INTEGER"));
        meta.addColumn(new ColumnMeta("name", "VARCHAR"));
        meta.addColumn(new ColumnMeta("age", "INTEGER"));
        meta.addColumn(new ColumnMeta("score", "VARCHAR"));

        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(records);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, meta);

        component.execute(flowFile);

        assertEquals(1, component.outputs.size());
        TableMeta outputMeta = (TableMeta) component.outputs.get(0).getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        assertEquals("upstream_orders", outputMeta.getTable());

        try (Connection conn = DuckDBEngine.getInstance().getConnection(DB_FILE);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS main.tmp_dt1");
        } finally {
            component.outputs.clear();
            DuckDBEngine.deleteDBFile(DB_FILE);
        }
    }

    private JSONArray buildRules() {
        JSONArray rules = new JSONArray();

        JSONObject cast = new JSONObject();
        cast.put("type", "cast");
        cast.put("column", "score");
        cast.put("targetColumn", "score_num");
        cast.put("targetType", "DOUBLE");
        rules.add(cast);

        JSONObject upper = new JSONObject();
        upper.put("type", "upper");
        upper.put("column", "name");
        upper.put("targetColumn", "name_upper");
        rules.add(upper);

        JSONObject filter = new JSONObject();
        filter.put("type", "filter");
        filter.put("column", "age");
        filter.put("operator", ">=");
        filter.put("value", 18);
        rules.add(filter);

        return rules;
    }

    private JSONObject row(int id, String name, int age, String score) {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("name", name);
        obj.put("age", age);
        obj.put("score", score);
        return obj;
    }

    /**
     * 捕获组件输出的 FlowFile，便于断言转换结果。
     */
    static class CapturingDataTransform extends DataTransform {
        final List<FlowFile> outputs = new ArrayList<>();

        @Override
        public void writeRecords(FlowFile flowFile) {
            outputs.add(flowFile);
        }
    }
}
