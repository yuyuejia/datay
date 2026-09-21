package com.data.job.component;

import com.alibaba.fastjson2.JSONObject;
import com.data.expression.ParameterUtil;
import com.data.job.Component.ComponentType;
import com.data.job.FlowFile;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LlmComponentTest {

    @Test
    public void testComponentType() {
        assertEquals(ComponentType.OPERATOR, new LlmComponent().getType());
    }

    @Test
    public void testExtractJsonObjectPlain() {
        assertEquals("{\"a\":1}", LlmComponent.extractJsonObject("{\"a\":1}"));
    }

    @Test
    public void testExtractJsonObjectWithCodeFence() {
        String content = "```json\n{\"sentiment\": \"positive\", \"score\": 0.9}\n```";
        assertEquals("{\"sentiment\": \"positive\", \"score\": 0.9}", LlmComponent.extractJsonObject(content).trim());
    }

    @Test
    public void testExtractJsonObjectText() {
        assertNull(LlmComponent.extractJsonObject("这是一段普通文本"));
    }

    @Test
    public void testMergeResultJsonObject() {
        JSONObject record = new JSONObject();
        record.put("id", 1);
        LlmComponent.mergeResult(record, "{\"sentiment\":\"positive\",\"score\":0.9}");
        assertEquals("positive", record.getString("sentiment"));
        assertEquals(0.9, record.getDoubleValue("score"), 0.0001);
        assertEquals(1, record.getIntValue("id"));
        assertFalse(record.containsKey(LlmComponent.RESULT_FIELD));
    }

    @Test
    public void testMergeResultText() {
        JSONObject record = new JSONObject();
        record.put("id", 1);
        LlmComponent.mergeResult(record, "正面情感");
        assertEquals("正面情感", record.getString(LlmComponent.RESULT_FIELD));
    }

    @Test
    public void testReplaceParametersWithRowVariables() {
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("trace_id", "t-001");

        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "张三");
        variables.put("age", 18);

        String result = ParameterUtil.replaceParameters(
            "用户${name}，年龄${age}，trace=${trace_id}",
            flowFile,
            variables
        );
        assertEquals("用户张三，年龄18，trace=t-001", result);
    }

    @Test
    public void testReplaceParametersFallbackToAttributes() {
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("name", "李四");

        Map<String, Object> variables = new HashMap<>();
        variables.put("age", 20);

        String result = ParameterUtil.replaceParameters("${name}-${age}", flowFile, variables);
        assertEquals("李四-20", result);
    }

    @Test
    public void testRowDataOverridesFlowFileData() {
        com.alibaba.fastjson2.JSONArray wholeData = new com.alibaba.fastjson2.JSONArray();
        wholeData.add("整个数据集");
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(wholeData);

        JSONObject row = new JSONObject();
        row.put("order_id", 10001);
        Map<String, Object> variables = new HashMap<>();
        variables.put("FLOW_FILE_DATA", row);
        variables.put("ROW_DATA", row);

        String result = ParameterUtil.replaceParameters("数据=${FLOW_FILE_DATA}", flowFile, variables);
        assertEquals("数据=" + row, result);
        assertEquals("数据=" + row, ParameterUtil.replaceParameters("数据=${ROW_DATA}", flowFile, variables));
    }

    @Test
    public void testReplaceParametersBuiltIn() {
        FlowFile flowFile = new FlowFile();
        String result = ParameterUtil.replaceParameters("id=#{UUID}", flowFile, new HashMap<>());
        assertTrue(result.startsWith("id="));
        assertFalse(result.contains("#{UUID}"));
    }
}
