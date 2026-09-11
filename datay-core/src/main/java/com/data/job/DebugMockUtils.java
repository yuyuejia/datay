package com.data.job;

import com.alibaba.fastjson2.JSONObject;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 调试模式下为实时输入组件生成模拟数据时使用的工具方法。
 */
public final class DebugMockUtils {

    private DebugMockUtils() {}

    /**
     * 根据表结构生成一行模拟数据，字段名取自表结构，值根据字段类型生成。
     */
    public static JSONObject sampleRow(TableMeta tableMeta) {
        JSONObject row = new JSONObject();
        if (tableMeta == null || tableMeta.columns() == null) {
            return row;
        }
        for (ColumnMeta column : tableMeta.columns()) {
            row.put(column.getName(), sampleValue(column.getType()));
        }
        return row;
    }

    /**
     * 根据字段类型生成一个模拟值。
     */
    public static Object sampleValue(String type) {
        if (type == null) {
            return "sample";
        }
        String normalized = type.toUpperCase();
        if (normalized.contains("BIGINT")) {
            return 1L;
        }
        if (normalized.contains("INT")) {
            return 1;
        }
        if (normalized.contains("DECIMAL") || normalized.contains("NUMERIC") || normalized.contains("NUMBER")) {
            return new BigDecimal("0.00");
        }
        if (normalized.contains("FLOAT") || normalized.contains("DOUBLE") || normalized.contains("REAL")) {
            return 0.0;
        }
        if (normalized.contains("BOOL")) {
            return true;
        }
        if (normalized.contains("DATE") || normalized.contains("TIME")) {
            return new Timestamp(System.currentTimeMillis());
        }
        return "sample";
    }

    /**
     * 当无法读取真实表结构时使用的占位表结构。
     */
    public static TableMeta placeholderTableMeta(String schema, String table) {
        TableMeta tableMeta = new TableMeta();
        tableMeta.setSchema(schema == null || schema.trim().isEmpty() ? "test_db" : schema);
        tableMeta.setTable(table == null || table.trim().isEmpty() ? "test_table" : table);
        tableMeta.setDbType("mysql");
        tableMeta.addColumn(new ColumnMeta("id", "BIGINT"));
        tableMeta.addColumn(new ColumnMeta("name", "VARCHAR"));
        tableMeta.addColumn(new ColumnMeta("update_time", "TIMESTAMP"));
        return tableMeta;
    }

    /**
     * 判断表名是否匹配过滤模式（正则）。模式为空时匹配所有。
     */
    public static boolean matchesPattern(String pattern, String value) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        try {
            return value.matches(pattern);
        } catch (Exception e) {
            // 非法正则时退化为包含匹配
            return value.contains(pattern);
        }
    }

    /**
     * 生成一个通用的模拟 JSON 数据对象。
     */
    public static JSONObject genericRow() {
        JSONObject row = new JSONObject();
        row.put("id", 1);
        row.put("name", "debug");
        row.put("event_time", new Timestamp(System.currentTimeMillis()));
        return row;
    }
}
