package com.data.metadata.util;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.metadata.AllDataType;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DBType;
import com.data.metadata.TableMeta;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 根据JSON数据推断表结构（列名、列类型），生成TableMeta，供下游自动建表使用。
 *
 * <p>支持JSON数组（多行）与JSON对象（单行），也支持JSON字符串与Map/List等结构。</p>
 */
public final class JsonTableMetaUtils {

    /** VARCHAR类型默认长度 */
    public static final int DEFAULT_VARCHAR_LENGTH = 255;

    private JsonTableMetaUtils() {
    }

    /**
     * 根据JSON数据生成表元数据，默认数据库类型为MYSQL。
     */
    public static TableMeta build(Object jsonData) {
        return build(jsonData, DBType.MYSQL.name());
    }

    /**
     * 根据JSON数据生成表元数据。
     *
     * @param jsonData JSON数组、JSON对象、Map、List或JSON字符串
     * @param dbType   源数据类型（如mysql），用于下游类型转换
     * @return 推断出的表元数据；无有效字段时返回null
     */
    public static TableMeta build(Object jsonData, String dbType) {
        return build(null, jsonData, dbType);
    }

    /**
     * 根据JSON数据生成指定表名的表元数据。
     *
     * @param tableName 目标表名，可为null
     */
    public static TableMeta build(String tableName, Object jsonData, String dbType) {
        if (jsonData instanceof String) {
            jsonData = parse((String) jsonData);
        }
        if (jsonData == null) {
            return null;
        }

        Map<String, ColumnMeta> columns = new LinkedHashMap<>();
        collect(jsonData, columns);

        if (columns.isEmpty()) {
            return null;
        }

        TableMeta tableMeta = new TableMeta();
        tableMeta.setDbType(dbType == null ? DBType.MYSQL.name() : dbType);
        tableMeta.setTable(tableName);
        for (ColumnMeta column : columns.values()) {
            finalizeColumn(column);
            tableMeta.addColumn(column);
        }
        return tableMeta;
    }

    /**
     * 解析JSON字符串为JSONArray或JSONObject，无法解析时返回null。
     */
    public static Object parse(String json) {
        if (json == null) {
            return null;
        }
        String trimmed = json.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("[")) {
            return JSONArray.parseArray(trimmed);
        }
        if (trimmed.startsWith("{")) {
            return JSONObject.parseObject(trimmed);
        }
        return null;
    }

    private static void collect(Object jsonData, Map<String, ColumnMeta> columns) {
        if (jsonData instanceof JSONArray) {
            for (Object item : (JSONArray) jsonData) {
                collect(item, columns);
            }
        } else if (jsonData instanceof List) {
            for (Object item : (List<?>) jsonData) {
                collect(item, columns);
            }
        } else if (jsonData instanceof JSONObject) {
            collectRecord((JSONObject) jsonData, columns);
        } else if (jsonData instanceof Map) {
            collectRecord(new JSONObject((Map<String, Object>) jsonData), columns);
        }
    }

    private static void collectRecord(JSONObject record, Map<String, ColumnMeta> columns) {
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();

            ColumnMeta column = columns.get(name);
            if (column == null) {
                column = new ColumnMeta(name, null);
                columns.put(name, column);
            }
            column.setType(mergeColumnType(column.getType(), inferColumnType(value)));

            if (value instanceof String
                    && AllDataType.VARCHAR.getName().equals(column.getType())
                    && ((String) value).length() > column.getLength()) {
                column.setLength(((String) value).length());
            }
        }
    }

    /**
     * 根据单个JSON值推断列类型。
     */
    public static String inferColumnType(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return AllDataType.BOOLEAN.getName();
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).scale() > 0
                    ? AllDataType.DOUBLE.getName()
                    : AllDataType.BIGINT.getName();
        }
        if (value instanceof Float || value instanceof Double) {
            return AllDataType.DOUBLE.getName();
        }
        if (value instanceof Number) {
            return AllDataType.BIGINT.getName();
        }
        if (value instanceof JSONObject || value instanceof JSONArray
                || value instanceof Map || value instanceof Collection) {
            return AllDataType.TEXT.getName();
        }
        return AllDataType.VARCHAR.getName();
    }

    /**
     * 合并同一列上多个样本值的类型，数值间自动向上兼容。
     */
    public static String mergeColumnType(String current, String candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || current.equals(candidate)) {
            return candidate;
        }

        boolean currentNumeric = isNumericType(current);
        boolean candidateNumeric = isNumericType(candidate);
        if (currentNumeric && candidateNumeric) {
            return AllDataType.DOUBLE.getName().equals(current)
                    || AllDataType.DOUBLE.getName().equals(candidate)
                    ? AllDataType.DOUBLE.getName()
                    : AllDataType.BIGINT.getName();
        }
        if (currentNumeric && AllDataType.BOOLEAN.getName().equals(candidate)) {
            return current;
        }
        if (candidateNumeric && AllDataType.BOOLEAN.getName().equals(current)) {
            return candidate;
        }
        if (AllDataType.TEXT.getName().equals(current) || AllDataType.TEXT.getName().equals(candidate)) {
            return AllDataType.TEXT.getName();
        }
        return AllDataType.VARCHAR.getName();
    }

    private static boolean isNumericType(String type) {
        return AllDataType.BIGINT.getName().equals(type) || AllDataType.DOUBLE.getName().equals(type);
    }

    private static void finalizeColumn(ColumnMeta column) {
        if (column.getType() == null) {
            column.setType(AllDataType.VARCHAR.getName());
        }
        if (AllDataType.VARCHAR.getName().equals(column.getType())) {
            if (column.getLength() <= 0) {
                column.setLength(DEFAULT_VARCHAR_LENGTH);
            } else if (column.getLength() > DEFAULT_VARCHAR_LENGTH) {
                column.setType(AllDataType.TEXT.getName());
            }
        }
        column.setPrecision(-1);
        column.setScale(-1);
    }
}
