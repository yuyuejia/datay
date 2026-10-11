package com.data.datasource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 与具体数据源无关的查询结果模型。
 *
 * <p>统一表达 JDBC 结果集、Elasticsearch SQL / DSL 等不同来源的查询产物，
 * 上层（REST / MCP）只依赖该模型，不感知底层是 JDBC 还是其它协议。
 */
public class QueryResult {

    private final List<String> columns;
    private final List<Map<String, Object>> rows;
    private final int affectedRows;

    public QueryResult(List<String> columns, List<Map<String, Object>> rows, int affectedRows) {
        this.columns = columns == null ? new ArrayList<>() : columns;
        this.rows = rows == null ? new ArrayList<>() : rows;
        this.affectedRows = affectedRows;
    }

    public static QueryResult empty() {
        return new QueryResult(Collections.emptyList(), Collections.emptyList(), 0);
    }

    public static QueryResult updates(int affectedRows) {
        return new QueryResult(Collections.emptyList(), Collections.emptyList(), affectedRows);
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public int getAffectedRows() {
        return affectedRows;
    }

    /**
     * 转换为 REST / MCP 约定的结果结构：{@code columns} / {@code rows} / {@code affectedRows}。
     */
    public Map<String, Object> toResultMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("affectedRows", affectedRows);
        return result;
    }
}
