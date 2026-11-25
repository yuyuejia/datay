package com.data.metadata.util;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.net.InetAddress;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;

/**
 * JDBC 读取值归一化工具。
 *
 * <p>不同驱动对同一逻辑类型会返回不同的 Java 类型：ClickHouse 的数组返回 {@code int[]}/{@code Object[]}，
 * Map 返回 {@link Map}，Tuple 返回 {@link java.util.Arrays$ArrayList}，无符号整数返回
 * {@code com.clickhouse.data.value.Unsigned*}，IP 返回 {@link InetAddress}，FixedString 末尾补 {@code \0}。
 * 这里统一转换成可序列化、可跨库写入的形态：数组/集合 -> {@link JSONArray}，Map -> {@link JSONObject}，
 * IP -> 字符串，无符号整数 -> {@link Long}/{@link BigInteger}，并去掉字符串末尾的 NUL 填充。</p>
 */
public final class JdbcValueNormalizer {

    private JdbcValueNormalizer() {}

    /**
     * 归一化单个 JDBC 读取值，递归处理数组、集合与 Map。
     */
    public static Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        // 二进制必须原样保留，不能按数组展开成数字列表
        if (value instanceof byte[]) {
            return value;
        }
        if (value instanceof java.sql.Array) {
            try {
                return normalize(((java.sql.Array) value).getArray());
            } catch (SQLException e) {
                return value.toString();
            }
        }
        if (value.getClass().isArray()) {
            return toJsonArray(value);
        }
        if (value instanceof Map<?, ?>) {
            return toJsonObject((Map<?, ?>) value);
        }
        if (value instanceof Collection<?>) {
            return toJsonArray((Collection<?>) value);
        }
        if (value instanceof InetAddress) {
            return ((InetAddress) value).getHostAddress();
        }
        if (isClickHouseUnsigned(value)) {
            return toUnsigned(value);
        }
        if (value instanceof String) {
            return trimTrailingNul((String) value);
        }
        return value;
    }

    private static JSONArray toJsonArray(Object array) {
        int length = Array.getLength(array);
        JSONArray result = new JSONArray(length);
        for (int i = 0; i < length; i++) {
            result.add(normalize(Array.get(array, i)));
        }
        return result;
    }

    private static JSONArray toJsonArray(Collection<?> collection) {
        JSONArray result = new JSONArray(collection.size());
        for (Object item : collection) {
            result.add(normalize(item));
        }
        return result;
    }

    private static JSONObject toJsonObject(Map<?, ?> map) {
        JSONObject result = new JSONObject();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = entry.getKey() == null ? "null" : entry.getKey().toString();
            result.put(key, normalize(entry.getValue()));
        }
        return result;
    }

    private static boolean isClickHouseUnsigned(Object value) {
        return value instanceof Number && value.getClass().getName().startsWith("com.clickhouse.data.value.Unsigned");
    }

    /**
     * ClickHouse 无符号整数转为标准 Java 数值：不超过 long 范围转 Long，否则保留 BigInteger。
     */
    private static Number toUnsigned(Object value) {
        BigInteger big = new BigInteger(value.toString());
        if (big.bitLength() < 64) {
            return big.longValue();
        }
        return big;
    }

    private static String trimTrailingNul(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '\u0000') {
            end--;
        }
        return end == value.length() ? value : value.substring(0, end);
    }
}
