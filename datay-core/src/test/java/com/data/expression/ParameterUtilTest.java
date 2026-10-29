package com.data.expression;

import com.alibaba.fastjson2.JSONObject;
import com.data.job.FlowFile;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParameterUtilTest {

    @Test
    public void test1() throws Exception {
        String text = "Hello ${name}, your age is #{age}. " + "Your email is ${attr:email} and address is ${address}.";

        List<ParameterInfo> parameters = ParameterUtil.parseParameters(text);
        // 获取并打印解析结果
        System.out.println("解析到的参数:");
        for (ParameterInfo param : parameters) {
            System.out.println(param);
        }
    }

    @Test
    public void test2() throws Exception {
        String text = "Hello ,Your email is ${email} and address is address.";

        List<ParameterInfo> parameters = ParameterUtil.parseParameters(text);
        // 获取并打印解析结果
        System.out.println("解析到的参数:");
        for (ParameterInfo param : parameters) {
            System.out.println(param);
        }
    }

    @Test
    public void test3() throws Exception {
        String text = "";

        List<ParameterInfo> parameters = ParameterUtil.parseParameters(text);
        // 获取并打印解析结果
        System.out.println("解析到的参数:");
        for (ParameterInfo param : parameters) {
            System.out.println(param);
        }
    }

    @Test
    public void test4() throws Exception {
        String text = "{\"table\":\"${TIMESTAMP}\"}";
        // 获取并打印解析结果
        System.out.println(ParameterUtil.replaceParameters(text));

    }

    @Test
    public void testTimeToTimestamp() throws Exception {
        String text = "#{NOW - 1d, timestamp}";
        String result = ParameterUtil.replaceParameters(text);
        System.out.println(result);
        long expected = java.time.LocalDateTime.now().minusDays(1)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        org.junit.jupiter.api.Assertions.assertTrue(Math.abs(Long.parseLong(result) - expected) < 5000);
    }

    @Test
    public void testPrepareParameterizedSqlConvertsParametersInOrder() {
        String text =
            "INSERT INTO t (id, name, before_amount, event_type) " +
            "VALUES (${id}, ${name}, ${__before.amount}, ${attr:_eventType})";

        ParameterUtil.ParameterizedSql prepared = ParameterUtil.prepareParameterizedSql(text);

        assertEquals("INSERT INTO t (id, name, before_amount, event_type) VALUES (?, ?, ?, ?)", prepared.getSql());
        assertEquals(4, prepared.getParameters().size());

        ParameterUtil.ParameterizedSql duplicated = ParameterUtil.prepareParameterizedSql("VALUES (${id}, ${id}, #{UUID})");
        assertEquals("VALUES (?, ?, ?)", duplicated.getSql());
        assertEquals(3, duplicated.getParameters().size());
    }

    @Test
    public void testResolveParameterValuesInOrder() {
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_eventType", "UPDATE");

        JSONObject before = new JSONObject();
        before.put("amount", 5);
        JSONObject row = new JSONObject();
        row.put("id", 1);
        row.put("name", "O'Reilly");
        row.put("__before", before);

        Map<String, Object> variables = new HashMap<>(row);
        variables.put("ROW_DATA", row);

        ParameterUtil.ParameterizedSql prepared = ParameterUtil.prepareParameterizedSql(
            "INSERT INTO t (id, name, before_amount, event_type) " +
            "VALUES (${id}, ${name}, ${__before.amount}, ${attr:_eventType})"
        );
        ParameterUtil.ParameterValues values = ParameterUtil.resolveParameterValues(
            prepared.getParameters(),
            flowFile,
            variables
        );

        assertFalse(values.hasUnresolved());
        assertEquals(List.of(1, "O'Reilly", 5, "UPDATE"), values.getValues());
    }

    @Test
    public void testStrictReplacementSupportsNestedFieldAndAttribute() {
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("eventType", "UPDATE");

        JSONObject before = new JSONObject();
        before.put("amount", 5);
        JSONObject row = new JSONObject();
        row.put("__before", before);
        row.put("name", "A");

        Map<String, Object> variables = new HashMap<>(row);
        variables.put("ROW_DATA", row);

        ParameterUtil.ReplacementResult result = ParameterUtil.replaceParametersStrict(
            "amount = ${__before.amount}, name = '${name}', event = '${attr:eventType}', row = ${ROW_DATA}",
            flowFile,
            variables
        );

        assertFalse(result.hasUnresolved());
        assertTrue(result.getText().contains("amount = 5"));
        assertTrue(result.getText().contains("name = 'A'"));
        assertTrue(result.getText().contains("event = 'UPDATE'"));
        assertTrue(result.getText().contains("\"__before\""));
        assertFalse(result.getText().contains("${ROW_DATA}"));
    }

    @Test
    public void testStrictReplacementReportsUnresolvedParameters() {
        ParameterUtil.ReplacementResult result = ParameterUtil.replaceParametersStrict(
            "id = ${missing}, value = #{UNKNOWN}",
            new FlowFile(),
            new HashMap<>()
        );

        assertTrue(result.hasUnresolved());
        assertTrue(result.getUnresolvedParameters().contains("${missing}"));
        assertTrue(result.getUnresolvedParameters().contains("#{UNKNOWN}"));
    }

    @Test
    public void testStrictReplacementTreatsNullAsUnresolved() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("nullable", null);

        ParameterUtil.ReplacementResult result = ParameterUtil.replaceParametersStrict(
            "value = ${nullable}",
            new FlowFile(),
            variables
        );

        assertTrue(result.hasUnresolved());
        assertTrue(result.getUnresolvedParameters().contains("${nullable}"));
    }

    @Test
    public void testStrictReplacementResolvesBuiltInParameters() {
        ParameterUtil.ReplacementResult result = ParameterUtil.replaceParametersStrict(
            "id = #{UUID}, ts = #{TIMESTAMP}",
            new FlowFile(),
            new HashMap<>()
        );

        assertFalse(result.hasUnresolved());
        assertFalse(result.getText().contains("#{UUID}"));
        assertFalse(result.getText().contains("#{TIMESTAMP}"));
    }
}
