package com.data.datafusion.mcp.harness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具入参的类型化读取工具。
 *
 * <p>MCP 客户端传来的 JSON 会被反序列化成 {@code Map<String, Object>}，
 * 数值可能是 {@code Integer} / {@code Long} / {@code String}，这里统一收敛，
 * 并在必填参数缺失时抛出可读的 {@link IllegalArgumentException}，
 * 由 {@link DatayHarnessExecutor} 转换成 MCP 错误结果。
 */
public final class DatayHarnessArgs {

    private DatayHarnessArgs() {}

    public static String str(Map<String, Object> args, String key) {
        Object value = args == null ? null : args.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public static String reqStr(Map<String, Object> args, String key) {
        String value = str(args, key);
        if (value == null) {
            throw new IllegalArgumentException("缺少必填参数: " + key);
        }
        return value;
    }

    public static Long longVal(Map<String, Object> args, String key) {
        Object value = args == null ? null : args.get(key);
        if (value == null || (value instanceof String text && text.isBlank())) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数 " + key + " 需要是整数，实际为: " + value);
        }
    }

    public static Long reqLong(Map<String, Object> args, String key) {
        Long value = longVal(args, key);
        if (value == null) {
            throw new IllegalArgumentException("缺少必填参数: " + key);
        }
        return value;
    }

    public static Integer intVal(Map<String, Object> args, String key, Integer defaultValue) {
        Long value = longVal(args, key);
        return value == null ? defaultValue : value.intValue();
    }

    /**
     * 把整数收敛到闭区间，用于限制分页大小等防止模型给出过大的值。
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static boolean boolVal(Map<String, Object> args, String key, boolean defaultValue) {
        Object value = args == null ? null : args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

    /**
     * 读取对象属性并复制为可修改的 {@code Map<String, Object>}。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> mapVal(Map<String, Object> args, String key) {
        Object value = args == null ? null : args.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        throw new IllegalArgumentException("参数 " + key + " 需要是对象，实际为: " + value.getClass().getSimpleName());
    }

    /**
     * 读取数组属性；若调用方直接给了单个对象，则包装成单元素列表，容忍常见的模型输出偏差。
     */
    public static List<Object> listVal(Map<String, Object> args, String key) {
        Object value = args == null ? null : args.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof Map<?, ?> || value instanceof String) {
            List<Object> single = new ArrayList<>();
            single.add(value);
            return single;
        }
        throw new IllegalArgumentException("参数 " + key + " 需要是数组，实际为: " + value.getClass().getSimpleName());
    }

    /**
     * 读取对象数组属性，逐项转换为 {@code Map<String, Object>}。
     */
    public static List<Map<String, Object>> mapList(Map<String, Object> args, String key) {
        List<Object> raw = listVal(args, key);
        if (raw == null) {
            return null;
        }
        List<Map<String, Object>> result = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> converted = new LinkedHashMap<>();
                map.forEach((k, v) -> converted.put(String.valueOf(k), v));
                result.add(converted);
            } else {
                throw new IllegalArgumentException("参数 " + key + " 的数组元素需要是对象，实际为: " + item);
            }
        }
        return result;
    }
}
