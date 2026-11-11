package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.Component.ComponentType;
import com.data.job.Connection;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EvaluateJsonPathTest extends ComponentTestBase {

    private EvaluateJsonPath component;
    private LinkedBlockingQueue<Object> outputQueue;

    @BeforeEach
    public void setUp() {
        component = new EvaluateJsonPath();
        initComponent(component, "eval1", "EvaluateJsonPath");

        component.getOutput().add(new Connection("eval1", "t1", 0, null));
        outputQueue = new LinkedBlockingQueue<>();
        context.getConnections().put("eval1_t1", outputQueue);
    }

    private FlowFile arrayFlowFile(String json) {
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(JSONArray.parseArray(json));
        return flowFile;
    }

    private FlowFile objectFlowFile(String json) {
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonObject(JSONObject.parseObject(json));
        return flowFile;
    }

    private FlowFile pollOutput() {
        Object value = outputQueue.poll();
        assertInstanceOf(FlowFile.class, value);
        return (FlowFile) value;
    }

    @Test
    @Timeout(60000)
    public void testComponentType() {
        assertEquals(ComponentType.OPERATOR, component.getType());
    }

    @Test
    @Timeout(60000)
    public void testPerRecordExtractsObject() {
        component.setJsonPath("$.data");

        FlowFile input = arrayFlowFile(
            "[{\"id\":1,\"data\":{\"name\":\"a\"}},{\"id\":2,\"data\":{\"name\":\"b\"}}]"
        );
        component.execute(input);

        FlowFile output = pollOutput();
        JSONArray records = output.getJsonArray();
        assertNotNull(records);
        assertEquals(2, records.size());
        assertEquals("a", records.getJSONObject(0).getString("name"));
        assertEquals("b", records.getJSONObject(1).getString("name"));
    }

    @Test
    @Timeout(60000)
    public void testPerRecordWrapsScalarWithValueField() {
        component.setJsonPath("$.data.name");

        FlowFile input = arrayFlowFile(
            "[{\"id\":1,\"data\":{\"name\":\"a\"}},{\"id\":2,\"data\":{\"name\":\"b\"}}]"
        );
        component.execute(input);

        JSONArray records = pollOutput().getJsonArray();
        assertEquals(2, records.size());
        assertEquals("a", records.getJSONObject(0).getString("value"));
        assertEquals("b", records.getJSONObject(1).getString("value"));
    }

    @Test
    @Timeout(60000)
    public void testPerRecordArrayBecomesDataContent() {
        component.setJsonPath("$.tags");
        component.setValueField("tag_list");

        FlowFile input = arrayFlowFile(
            "[{\"id\":1,\"tags\":[\"x\",\"y\"]},{\"id\":2,\"tags\":[\"z\"]}]"
        );
        component.execute(input);

        JSONArray records = pollOutput().getJsonArray();
        assertEquals(3, records.size());
        assertEquals("x", records.getString(0));
        assertEquals("y", records.getString(1));
        assertEquals("z", records.getString(2));
    }

    @Test
    @Timeout(60000)
    public void testPerRecordArrayOfObjectsBecomesDataContent() {
        component.setJsonPath("$.items");

        FlowFile input = arrayFlowFile(
            "[{\"items\":[{\"id\":1},{\"id\":2}]},{\"items\":[{\"id\":3}]}]"
        );
        component.execute(input);

        JSONArray records = pollOutput().getJsonArray();
        assertEquals(3, records.size());
        assertEquals(1, records.getJSONObject(0).getIntValue("id"));
        assertEquals(2, records.getJSONObject(1).getIntValue("id"));
        assertEquals(3, records.getJSONObject(2).getIntValue("id"));
    }

    @Test
    @Timeout(60000)
    public void testPerRecordEmptyArrayStillProducesOutput() {
        component.setJsonPath("$.items");

        component.execute(arrayFlowFile("[{\"items\":[]}]"));

        JSONArray records = pollOutput().getJsonArray();
        assertNotNull(records);
        assertTrue(records.isEmpty());
    }

    @Test
    @Timeout(60000)
    public void testPerRecordSkipsMissingRows() {
        component.setJsonPath("$.id");

        FlowFile input = arrayFlowFile("[{\"id\":1},{\"name\":\"no-id\"},{\"id\":3}]");
        component.execute(input);

        JSONArray records = pollOutput().getJsonArray();
        assertEquals(2, records.size());
        assertEquals(1, records.getJSONObject(0).getIntValue("value"));
        assertEquals(3, records.getJSONObject(1).getIntValue("value"));
    }

    @Test
    @Timeout(60000)
    public void testAllRowsMissingProducesNoOutput() {
        component.setJsonPath("$.missing");

        component.execute(arrayFlowFile("[{\"id\":1},{\"id\":2}]"));

        assertTrue(outputQueue.isEmpty());
    }

    @Test
    @Timeout(60000)
    public void testJsonObjectExtractsArrayDirectly() {
        component.setJsonPath("$.data.items");

        FlowFile input = objectFlowFile("{\"data\":{\"items\":[{\"id\":1},{\"id\":2}]}}");
        component.execute(input);

        FlowFile output = pollOutput();
        assertEquals(FlowFile.DataFormat.JSON_ARRAY, output.getDataFormat());
        JSONArray records = output.getJsonArray();
        assertEquals(2, records.size());
        assertEquals(1, records.getJSONObject(0).getIntValue("id"));
    }

    @Test
    @Timeout(60000)
    public void testJsonObjectWrapsScalar() {
        component.setJsonPath("$.data.name");

        component.execute(objectFlowFile("{\"data\":{\"name\":\"a\"}}"));

        JSONArray records = pollOutput().getJsonArray();
        assertEquals(1, records.size());
        assertEquals("a", records.getJSONObject(0).getString("value"));
    }

    @Test
    @Timeout(60000)
    public void testJsonPathWithoutDollarPrefixIsNormalized() {
        component.setJsonPath("data.name");

        component.execute(objectFlowFile("{\"data\":{\"name\":\"a\"}}"));

        assertEquals("a", pollOutput().getJsonArray().getJSONObject(0).getString("value"));
    }

    @Test
    @Timeout(60000)
    public void testTextJsonIsParsedAndEvaluatedPerRow() {
        component.setJsonPath("$.id");

        FlowFile input = new FlowFile();
        input.setTextData("[{\"id\":1},{\"id\":2}]");
        component.execute(input);

        JSONArray records = pollOutput().getJsonArray();
        assertEquals(2, records.size());
        assertEquals(2, records.getJSONObject(1).getIntValue("value"));
    }

    @Test
    @Timeout(60000)
    public void testNonJsonTextIsSkipped() {
        component.setJsonPath("$.id");

        FlowFile input = new FlowFile();
        input.setTextData("not a json");
        component.execute(input);

        assertTrue(outputQueue.isEmpty());
    }

    @Test
    @Timeout(60000)
    public void testMalformedJsonTextIsSkipped() {
        component.setJsonPath("$.id");

        FlowFile input = new FlowFile();
        input.setTextData("[{\"id\":1");
        component.execute(input);

        assertTrue(outputQueue.isEmpty());
    }

    @Test
    @Timeout(60000)
    public void testMissingJsonPathConfigSkips() {
        component.execute(arrayFlowFile("[{\"id\":1}]"));

        assertTrue(outputQueue.isEmpty());
    }

    @Test
    @Timeout(60000)
    public void testEndSignalIsNotForwarded() {
        component.execute(createEndFlowFile());

        assertTrue(outputQueue.isEmpty());
    }

    @Test
    @Timeout(60000)
    public void testAttributesAreCopiedAndTableMetaRebuilt() {
        component.setJsonPath("$.data.name");

        FlowFile input = arrayFlowFile("[{\"data\":{\"name\":\"a\"}}]");
        input.setAttribute(FlowFile.ATTRIBUTE_TABLE, "orders");
        input.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, new TableMeta("orders"));

        component.execute(input);

        FlowFile output = pollOutput();
        assertEquals("orders", output.getAttribute(FlowFile.ATTRIBUTE_TABLE));
        Object meta = output.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        assertInstanceOf(TableMeta.class, meta);
        TableMeta tableMeta = (TableMeta) meta;
        assertEquals("orders", tableMeta.getTable());
        assertTrue(tableMeta.columns().stream().anyMatch(column -> "value".equals(column.getName())));
        assertFalse(tableMeta.columns().stream().anyMatch(column -> "data".equals(column.getName())));
    }

    @Test
    @Timeout(60000)
    public void testJsonObjectMissingPathProducesNoOutput() {
        component.setJsonPath("$.nope");

        component.execute(objectFlowFile("{\"id\":1}"));

        assertNull(outputQueue.poll());
    }
}