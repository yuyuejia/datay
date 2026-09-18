package com.data.expression;

import org.junit.jupiter.api.Test;

import java.util.List;

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
}
