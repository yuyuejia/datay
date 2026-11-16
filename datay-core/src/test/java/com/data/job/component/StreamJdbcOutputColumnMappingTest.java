package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.FlowFile;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 验证 StreamJdbcOutput 的字段匹配忽略大小写：
 * header_map 未配置时按同名字段自动匹配，配置后同样兼容上下游字段大小写差异。
 */
class StreamJdbcOutputColumnMappingTest {

    @Test
    void autoMatchesSourceColumnsIgnoringCase() throws Exception {
        StreamJdbcOutput output = new StreamJdbcOutput();

        TableMeta target = table("target_table", "id", "user_name");
        FlowFile flowFile = flowFile(table("source_table", "ID", "USER_NAME"), row("ID", 1L, "USER_NAME", "alice"));

        Map<String, String> mapping = buildColumnMapping(output, target, flowFile);

        assertEquals("ID", mapping.get("id"), "目标字段应匹配到源数据中的大写字段");
        assertEquals("USER_NAME", mapping.get("user_name"), "目标字段应匹配到源数据中的大写字段");
    }

    @Test
    void headerMapMatchesIgnoringCase() throws Exception {
        StreamJdbcOutput output = new StreamJdbcOutput();
        output.setHeader_map(
            List.of(
                Map.of("source", "USERID", "target", "userId"),
                Map.of("source", "CREATED_AT", "target", "createdAt")
            )
        );

        TableMeta target = table("target_table", "UserId", "created_at", "remark");
        FlowFile flowFile = flowFile(
            table("source_table", "userid", "created_at", "remark"),
            row("userid", 1L, "created_at", "2024-01-01", "remark", "note")
        );

        Map<String, String> mapping = buildColumnMapping(output, target, flowFile);

        assertEquals("userid", mapping.get("UserId"), "header_map 的目标字段大小写不一致时应命中映射");
        assertEquals("created_at", mapping.get("created_at"), "header_map 的源字段大小写不一致时应命中映射");
        assertEquals("remark", mapping.get("remark"), "未配置映射的字段应按同名自动匹配");
    }

    @Test
    void readsValueIgnoringCaseWhenRecordKeyDiffers() throws Exception {
        StreamJdbcOutput output = new StreamJdbcOutput();

        JSONObject record = row("Id", 2L);
        ColumnMeta targetColumn = new ColumnMeta("id", "BIGINT");

        Method method = StreamJdbcOutput.class.getDeclaredMethod(
            "getMappedValue",
            JSONObject.class,
            ColumnMeta.class,
            Map.class
        );
        method.setAccessible(true);

        assertEquals(2L, method.invoke(output, record, targetColumn, Map.of("id", "ID")), "大小写不一致时应能取到值");
        assertNull(method.invoke(output, record, targetColumn, Map.of("id", "missing")), "源字段不存在时返回 null");
    }

    private Map<String, String> buildColumnMapping(StreamJdbcOutput output, TableMeta target, FlowFile flowFile)
        throws Exception {
        Method method = StreamJdbcOutput.class.getDeclaredMethod(
            "buildColumnMapping",
            TableMeta.class,
            FlowFile.class
        );
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> mapping = (Map<String, String>) method.invoke(output, target, flowFile);
        return mapping;
    }

    private TableMeta table(String name, String... columns) {
        TableMeta table = new TableMeta(name);
        for (String column : columns) {
            table.addColumn(new ColumnMeta(column, "VARCHAR"));
        }
        return table;
    }

    private FlowFile flowFile(TableMeta sourceTable, JSONObject record) {
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, sourceTable);
        JSONArray records = new JSONArray();
        records.add(record);
        flowFile.setJsonArray(records);
        return flowFile;
    }

    private JSONObject row(Object... keyValues) {
        JSONObject row = new JSONObject();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        return row;
    }
}
