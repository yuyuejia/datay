package com.data.datafusion.mcp.harness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 构造 MCP 工具入参 JSON Schema 的小工具集。
 *
 * <p>直接手写 {@code Map} 字面量既啰嗦又容易写错层级，这里把常用片段收敛成静态方法，
 * 让每个工具的 schema 声明保持一行可读。
 */
public final class DatayHarnessSchema {

    private DatayHarnessSchema() {}

    /**
     * 构造一个 object 类型的根 schema。
     *
     * @param properties 属性定义，允许为 null
     * @param required   必填属性名，允许为 null 或空
     */
    public static Map<String, Object> object(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties == null ? new LinkedHashMap<String, Object>() : properties);
        List<String> requiredNames = required == null
            ? List.of()
            : Arrays.stream(required).filter(name -> name != null && !name.isBlank()).toList();
        if (!requiredNames.isEmpty()) {
            schema.put("required", requiredNames);
        }
        return schema;
    }

    /**
     * 依次追加属性，返回同一个 Map，便于链式组织。
     */
    public static Map<String, Object> properties(Object... nameAndSchemaPairs) {
        if (nameAndSchemaPairs == null || nameAndSchemaPairs.length % 2 != 0) {
            throw new IllegalArgumentException("properties 需要成对给出属性名与 schema");
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int i = 0; i < nameAndSchemaPairs.length; i += 2) {
            properties.put(String.valueOf(nameAndSchemaPairs[i]), nameAndSchemaPairs[i + 1]);
        }
        return properties;
    }

    public static Map<String, Object> string(String description) {
        return typed("string", description);
    }

    public static Map<String, Object> integer(String description) {
        return typed("integer", description);
    }

    public static Map<String, Object> number(String description) {
        return typed("number", description);
    }

    public static Map<String, Object> bool(String description) {
        return typed("boolean", description);
    }

    /**
     * 枚举字符串，取值受限时使用，便于模型只给出合法值。
     */
    public static Map<String, Object> enumeration(List<String> values, String description) {
        Map<String, Object> prop = typed("string", description);
        prop.put("enum", new ArrayList<>(values));
        return prop;
    }

    /**
     * 数组属性。
     */
    public static Map<String, Object> array(Map<String, Object> items, String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "array");
        prop.put("items", items == null ? string(null) : items);
        if (description != null) {
            prop.put("description", description);
        }
        return prop;
    }

    /**
     * 任意对象属性，用于透传扩展配置等结构化入参。
     */
    public static Map<String, Object> freeObject(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "object");
        prop.put("additionalProperties", true);
        if (description != null) {
            prop.put("description", description);
        }
        return prop;
    }

    /**
     * 字符串字典属性，用于 {@code extraParams} 这类 {@code Map<String, String>} 入参。
     */
    public static Map<String, Object> stringMap(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "object");
        prop.put("additionalProperties", string(null));
        if (description != null) {
            prop.put("description", description);
        }
        return prop;
    }

    private static Map<String, Object> typed(String type, String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", type);
        if (description != null) {
            prop.put("description", description);
        }
        return prop;
    }
}
