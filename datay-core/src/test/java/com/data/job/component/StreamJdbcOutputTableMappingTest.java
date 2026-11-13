package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.data.job.ComponentFactory;
import com.data.job.FlowFile;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 验证 StreamJdbcOutput 的目标表重命名映射：
 * 上游 FlowFile 携带源表名时，按 tableMapping 解析为配置的目标表名。
 */
class StreamJdbcOutputTableMappingTest {

    @Test
    void resolvesRenamedTargetTableFromMapping() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("schema", "tgt_schema");
        params.put("table", "");
        params.put("tableMapping", Map.of("orders", "orders_new"));
        StreamJdbcOutput output = (StreamJdbcOutput) ComponentFactory.create("StreamJdbcOutput", params);

        Method method = StreamJdbcOutput.class.getDeclaredMethod("getTargetTableName", FlowFile.class);
        method.setAccessible(true);

        FlowFile renamed = new FlowFile();
        renamed.setAttribute(FlowFile.ATTRIBUTE_TABLE, "orders");
        assertEquals("orders_new", method.invoke(output, renamed), "命中映射时应写入重命名后的目标表");

        FlowFile untouched = new FlowFile();
        untouched.setAttribute(FlowFile.ATTRIBUTE_TABLE, "users");
        assertEquals("users", method.invoke(output, untouched), "未命中映射时沿用上游表名");
    }

    @Test
    void resolvesRenamedTargetTableIgnoringCase() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("tableMapping", Map.of("orders", "orders_new"));
        StreamJdbcOutput output = (StreamJdbcOutput) ComponentFactory.create("StreamJdbcOutput", params);

        Method method = StreamJdbcOutput.class.getDeclaredMethod("getTargetTableName", FlowFile.class);
        method.setAccessible(true);

        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, "ORDERS");
        assertEquals("orders_new", method.invoke(output, flowFile), "表名大小写不一致时也应命中映射");
    }
}
