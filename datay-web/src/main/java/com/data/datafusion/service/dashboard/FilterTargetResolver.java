package com.data.datafusion.service.dashboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v1 筛选目标的统一解析。
 *
 * <p>筛选器统一由服务端执行：pushdown 目标按声明下推，直接字段目标也视为服务端目标。
 * 前端不再执行本地过滤；mode 字段保留用于兼容历史结构，但不再决定过滤位置。
 */
public final class FilterTargetResolver {

    public static final String MODE_SERVER = "server";
    public static final String MODE_CLIENT = "client";

    private FilterTargetResolver() {}

    public static List<Map<String, Object>> targets(Map<String, Object> filter) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (filter == null || !(filter.get("targets") instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> target = new LinkedHashMap<>((Map<String, Object>) map);
                result.add(target);
            }
        }
        return result;
    }

    public static List<Map<String, Object>> targetsFor(Map<String, Object> filter, String datasetId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> target : targets(filter)) {
            if (datasetId != null && datasetId.equals(asString(target.get("datasetId")))) {
                result.add(target);
            }
        }
        return result;
    }

    public static boolean isServerTarget(Map<String, Object> target) {
        return !pushdown(target).isEmpty() || field(target) != null;
    }

    /**
     * @deprecated 看板运行时不再执行本地过滤，保留该方法仅用于兼容旧调用方。
     */
    @Deprecated
    public static boolean isClientTarget(Map<String, Object> target) {
        return false;
    }

    public static String field(Map<String, Object> target) {
        String field = asString(target.get("field"));
        return field == null || field.isBlank() ? null : field.trim();
    }

    public static Map<String, Object> pushdown(Map<String, Object> target) {
        if (target.get("pushdown") instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = new LinkedHashMap<>((Map<String, Object>) map);
            return cast;
        }
        return new LinkedHashMap<>();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
