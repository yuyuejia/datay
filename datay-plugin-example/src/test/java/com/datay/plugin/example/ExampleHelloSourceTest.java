package com.datay.plugin.example;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentFieldDescriptor;
import com.data.job.ComponentMetadataExtractor;
import com.data.job.DatasourceInfo;
import com.data.job.FlowFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleHelloSourceTest {

    @Test
    void shouldExposeFieldsForDynamicForm() {
        List<ComponentFieldDescriptor> fields = ComponentMetadataExtractor.extract(ExampleHelloSource.class);

        assertEquals(11, fields.size());
        assertTrue(fields.stream().anyMatch(field -> "message".equals(field.getKey()) && field.isRequired()));
        assertTrue(fields.stream().anyMatch(field -> "count".equals(field.getKey()) && "NUMBER".equals(field.getType())));
        assertTrue(fields.stream().anyMatch(field -> "outputFormat".equals(field.getKey()) && field.getOptions().size() == 2));
        assertTrue(
            fields.stream().anyMatch(
                field -> "sourceId".equals(field.getKey())
                    && "DATASOURCE".equals(field.getType())
                    && field.isRequired()
                    && "schema".equals(field.getSchemaField())
            )
        );
        assertTrue(
            fields.stream().anyMatch(
                field -> "schema".equals(field.getKey())
                    && "SCHEMA".equals(field.getType())
                    && "sourceId".equals(field.getDataSourceField())
            )
        );
        assertTrue(
            fields.stream().anyMatch(
                field -> "table".equals(field.getKey())
                    && "TABLE".equals(field.getType())
                    && "sourceId".equals(field.getDataSourceField())
                    && "schema".equals(field.getSchemaField())
            )
        );
        assertTrue(
            fields.stream().anyMatch(
                field -> "columns".equals(field.getKey())
                    && "COLUMN".equals(field.getType())
                    && field.isMultiple()
                    && "ARRAY".equals(field.getValueFormat())
                    && "sourceId".equals(field.getDataSourceField())
                    && "table".equals(field.getTableField())
            )
        );
        assertTrue(
            fields.stream().anyMatch(
                field -> "tags".equals(field.getKey())
                    && "MULTI_SELECT".equals(field.getType())
                    && field.isMultiple()
                    && "ARRAY".equals(field.getValueFormat())
            )
        );
        assertTrue(fields.stream().anyMatch(field -> "includeTimestamp".equals(field.getKey()) && "SWITCH".equals(field.getType())));
        assertTrue(fields.stream().anyMatch(field -> "templateFile".equals(field.getKey()) && "FILE".equals(field.getType())));
        assertTrue(fields.stream().anyMatch(field -> "customParams".equals(field.getKey()) && "TEXTAREA".equals(field.getType())));
    }

    @Test
    void shouldGenerateJsonRows() {
        ExampleHelloSource source = new ExampleHelloSource();
        source.setMessage("hello plugin");
        source.setCount(2);
        source.setOutputFormat("JSON_ARRAY");
        source.setTags(List.of("alpha", "beta"));
        source.setIncludeTimestamp(false);
        DatasourceInfo datasource = new DatasourceInfo();
        datasource.setType("DUCKDB");
        datasource.setUrl("jdbc:duckdb:./data/demo.duckdb");
        datasource.setDbschema("main");
        source.setDatasource(datasource);
        source.setSchema("main");
        source.setTable("demo_table");
        source.setColumns(List.of("id", "name"));
        source.setTemplateFile("/tmp/example-template.json");
        source.setCustomParams("{\"region\":\"cn\"}");

        FlowFile output = source.buildFlowFile(new FlowFile());

        JSONArray rows = output.getJsonArray();
        assertNotNull(rows);
        assertEquals(2, rows.size());

        JSONObject first = rows.getJSONObject(0);
        assertEquals(1, first.getIntValue("id"));
        assertEquals("hello plugin", first.getString("message"));
        assertEquals(List.of("alpha", "beta"), first.getList("tags", String.class));
        assertNull(first.get("timestamp"));

        assertEquals("ExampleHelloSource", output.getAttribute("_source"));
        assertEquals("hello plugin", output.getAttribute("_message"));
        assertEquals("DUCKDB", output.getAttribute("_datasourceType"));
        assertEquals("main", output.getAttribute("_datasourceSchema"));
        assertEquals("demo_table", output.getAttribute("_demoTable"));
        assertEquals(List.of("id", "name"), output.getAttribute("_demoColumns"));
        assertEquals("/tmp/example-template.json", output.getAttribute("_templateFile"));
        assertEquals("{\"region\":\"cn\"}", output.getAttribute("_customParams"));
    }

    @Test
    void shouldGenerateTextData() {
        ExampleHelloSource source = new ExampleHelloSource();
        source.setMessage("hello plugin");
        source.setCount(3);
        source.setOutputFormat("TEXT");
        source.setTags(List.of());
        source.setIncludeTimestamp(false);

        FlowFile output = source.buildFlowFile(new FlowFile());

        assertEquals("1: hello plugin\n2: hello plugin\n3: hello plugin", output.getTextData());
        assertNull(output.getJsonArray());
        assertFalse(output.getAttributeMap().isEmpty());
    }
}
