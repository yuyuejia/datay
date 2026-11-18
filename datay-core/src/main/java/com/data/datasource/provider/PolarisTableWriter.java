package com.data.datasource.provider;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Apache Polaris（Iceberg）写入策略。
 *
 * <p>DuckDB-Iceberg 的限制决定了写入方式：</p>
 * <ul>
 *   <li>不支持 DuckDB Appender，INSERT / APPEND / OVERWRITE 使用多值 INSERT 分块写入；</li>
 *   <li>不支持 {@code INSERT OR REPLACE} / {@code ON CONFLICT}，也没有主键；</li>
 *   <li>UPDATE / DELETE 使用 CDC 的 {@code __before} 前镜像做全列匹配（{@code IS NOT DISTINCT FROM}），
 *       等价于「用新行替换旧行」，保证没有主键时也能得到正确的删除/更新语义。</li>
 * </ul>
 */
final class PolarisTableWriter {

    static final PolarisTableWriter INSTANCE = new PolarisTableWriter();

    private static final String DB_TYPE = "POLARIS";
    private static final String DEFAULT_CATALOG = "polaris";
    /** 单条 INSERT 的最大行数。 */
    private static final int INSERT_MAX_ROWS = 1_000;
    /** 单条 INSERT 的最大参数数。 */
    private static final int INSERT_MAX_PARAMS = 32_000;
    /** UPDATE / DELETE 的批大小。 */
    private static final int MUTATION_BATCH_SIZE = 500;

    private PolarisTableWriter() {}

    boolean supports(String eventType) {
        if (eventType == null) {
            return false;
        }
        switch (eventType.toUpperCase(Locale.ROOT)) {
            case "INSERT":
            case "APPEND":
            case "OVERWRITE":
            case "UPDATE":
            case "DELETE":
                return true;
            default:
                return false;
        }
    }

    String write(Connection connection, TableMeta table, JSONArray records, String eventType,
                 Map<String, String> columnMapping) throws SQLException {
        String type = eventType == null ? "" : eventType.toUpperCase(Locale.ROOT);
        if (records == null || records.isEmpty()) {
            return "Polaris 批量写入";
        }
        switch (type) {
            case "INSERT":
            case "APPEND":
            case "OVERWRITE":
                insert(connection, table, records, columnMapping);
                return "Polaris 批量 INSERT";
            case "UPDATE":
                update(connection, table, records, columnMapping);
                return "Polaris UPDATE（按变更前镜像匹配）";
            case "DELETE":
                delete(connection, table, records, columnMapping);
                return "Polaris DELETE（按变更前镜像匹配）";
            default:
                throw new SQLException("不支持的 Polaris 写入模式: " + eventType);
        }
    }

    // ------------------------------------------------------------------ INSERT

    private void insert(Connection connection, TableMeta table, JSONArray records,
                        Map<String, String> columnMapping) throws SQLException {
        List<ColumnMeta> columns = table.columns();
        if (columns.isEmpty()) {
            throw new SQLException("目标表没有可写入的列: " + table.getTable());
        }
        String sql = buildInsertSQL(table, columns);
        int chunkRows = resolveRows(columns.size());
        int offset = 0;
        while (offset < records.size()) {
            int rows = Math.min(chunkRows, records.size() - offset);
            StringBuilder chunkSql = new StringBuilder(sql);
            // 第一行占位符已包含在 sql 中，后续行追加
            for (int i = 1; i < rows; i++) {
                chunkSql.append(", (");
                for (int c = 0; c < columns.size(); c++) {
                    if (c > 0) {
                        chunkSql.append(", ");
                    }
                    chunkSql.append('?');
                }
                chunkSql.append(')');
            }
            try (PreparedStatement statement = connection.prepareStatement(chunkSql.toString())) {
                int parameterIndex = 1;
                for (int r = 0; r < rows; r++) {
                    JSONObject record = (JSONObject) records.get(offset + r);
                    for (ColumnMeta column : columns) {
                        DatabaseConverter.bindParameter(
                            DB_TYPE,
                            statement,
                            parameterIndex++,
                            column,
                            value(record, column, columnMapping)
                        );
                    }
                }
                statement.executeUpdate();
            }
            offset += rows;
        }
    }

    private String buildInsertSQL(TableMeta table, List<ColumnMeta> columns) {
        List<String> names = new ArrayList<>(columns.size());
        List<String> placeholders = new ArrayList<>(columns.size());
        for (ColumnMeta column : columns) {
            names.add(quote(column.getName()));
            placeholders.add("?");
        }
        return "INSERT INTO " + qualifiedTableName(table) + " (" + String.join(", ", names) + ") VALUES (" +
            String.join(", ", placeholders) + ")";
    }

    // ------------------------------------------------------------------ UPDATE

    /**
     * UPDATE = 「用 after 镜像替换 before 镜像」。SET 覆盖所有列，WHERE 使用 before 镜像全列匹配，
     * 既能处理任意列变更，也能处理没有主键的 Iceberg 表。
     */
    private void update(Connection connection, TableMeta table, JSONArray records,
                        Map<String, String> columnMapping) throws SQLException {
        List<ColumnMeta> columns = table.columns();
        if (columns.isEmpty()) {
            throw new SQLException("目标表没有可更新的列: " + table.getTable());
        }
        List<JSONObject> withBefore = new ArrayList<>();
        JSONArray withoutBefore = new JSONArray();
        for (Object o : records) {
            JSONObject record = (JSONObject) o;
            if (record.getJSONObject("__before") == null) {
                withoutBefore.add(record);
            } else {
                withBefore.add(record);
            }
        }

        // 没有变更前镜像时无法定位旧行，退化为追加 after 镜像（与通用 JDBC 写入的兜底一致）
        if (!withoutBefore.isEmpty()) {
            insert(connection, table, withoutBefore, columnMapping);
        }
        if (withBefore.isEmpty()) {
            return;
        }

        StringBuilder sql = new StringBuilder("UPDATE ")
            .append(qualifiedTableName(table))
            .append(" SET ");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(quote(columns.get(i).getName())).append(" = ?");
        }
        sql.append(" WHERE ");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(" AND ");
            }
            sql.append(quote(columns.get(i).getName())).append(" IS NOT DISTINCT FROM ?");
        }

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int pending = 0;
            for (JSONObject record : withBefore) {
                JSONObject before = record.getJSONObject("__before");
                int parameterIndex = 1;
                for (ColumnMeta column : columns) {
                    DatabaseConverter.bindParameter(
                        DB_TYPE,
                        statement,
                        parameterIndex++,
                        column,
                        value(record, column, columnMapping)
                    );
                }
                for (ColumnMeta column : columns) {
                    DatabaseConverter.bindParameter(
                        DB_TYPE,
                        statement,
                        parameterIndex++,
                        column,
                        value(before, column, columnMapping)
                    );
                }
                statement.addBatch();
                if (++pending >= MUTATION_BATCH_SIZE) {
                    statement.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) {
                statement.executeBatch();
            }
        }
    }

    // ------------------------------------------------------------------ DELETE

    private void delete(Connection connection, TableMeta table, JSONArray records,
                        Map<String, String> columnMapping) throws SQLException {
        List<ColumnMeta> columns = table.columns();
        if (columns.isEmpty()) {
            throw new SQLException("目标表没有可删除的列: " + table.getTable());
        }
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(qualifiedTableName(table)).append(" WHERE ");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(" AND ");
            }
            sql.append(quote(columns.get(i).getName())).append(" IS NOT DISTINCT FROM ?");
        }

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int pending = 0;
            for (Object o : records) {
                JSONObject record = (JSONObject) o;
                // DELETE 事件通常同时携带 after 行与 __before；优先使用变更前镜像
                JSONObject before = record.getJSONObject("__before");
                JSONObject source = before != null ? before : record;
                int parameterIndex = 1;
                for (ColumnMeta column : columns) {
                    DatabaseConverter.bindParameter(
                        DB_TYPE,
                        statement,
                        parameterIndex++,
                        column,
                        value(source, column, columnMapping)
                    );
                }
                statement.addBatch();
                if (++pending >= MUTATION_BATCH_SIZE) {
                    statement.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) {
                statement.executeBatch();
            }
        }
    }

    // ------------------------------------------------------------------ 辅助

    private Object value(JSONObject record, ColumnMeta column, Map<String, String> columnMapping) {
        if (record == null) {
            return null;
        }
        String sourceColumn = column.getName();
        if (columnMapping != null) {
            sourceColumn = columnMapping.getOrDefault(sourceColumn, sourceColumn);
        }
        return record.get(sourceColumn);
    }

    private int resolveRows(int columnCount) {
        int byParams = INSERT_MAX_PARAMS / Math.max(1, columnCount);
        return Math.max(1, Math.min(INSERT_MAX_ROWS, byParams));
    }

    private String qualifiedTableName(TableMeta table) {
        StringBuilder sb = new StringBuilder();
        String catalog = table.getCatalog();
        if (catalog == null || catalog.isBlank()) {
            catalog = DEFAULT_CATALOG;
        }
        sb.append(quote(catalog)).append('.');
        if (table.getSchema() != null && !table.getSchema().isBlank()) {
            sb.append(quote(table.getSchema())).append('.');
        }
        sb.append(quote(table.getTable()));
        return sb.toString();
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
