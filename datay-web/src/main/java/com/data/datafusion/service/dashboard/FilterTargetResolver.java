package com.data.datafusion.service.dashboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v1 筛选目标的统一解析。
 *
 * <p>服务端只消费 pushdown 或 mode=server 的字段目标；mode=client 的字段目标由前端处理。
 * 这里集中处理 targets 结构，避免各处重复判断。
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
        if (!pushdown(target).isEmpty()) {
            return true;
        }
        String field = field(target);
        return field != null && MODE_SERVER.equalsIgnoreCase(asString(target.get("mode")));
    }

    public static boolean isClientTarget(Map<String, Object> target) {
        return field(target) != null && !MODE_SERVER.equalsIgnoreCase(asString(target.get("mode")));
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
