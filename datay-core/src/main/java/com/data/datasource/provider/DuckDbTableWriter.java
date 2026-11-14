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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * DuckDB 原生表的专用写入策略。
 *
 * <ul>
 *   <li>INSERT / APPEND / OVERWRITE：使用 {@link org.duckdb.DuckDBAppender}（见 {@link DuckDbAppenderWriter}）；</li>
 *   <li>UPDATE / DELETE（有主键）：使用集合式 {@code INSERT ... ON CONFLICT DO UPDATE} /
 *       {@code DELETE ... USING}，避免 DuckDB JDBC {@code executeBatch} 逐行规划执行。</li>
 * </ul>
 *
 * <p>DuckLake 只复用其中的 Appender 路径（由 {@link DuckLakeDataSourceProvider#supportsTableWrite}
 * 限定为 INSERT / APPEND / OVERWRITE）；Quack 仍走通用 JDBC 写入。</p>
 */
final class DuckDbTableWriter {

    static final DuckDbTableWriter INSTANCE = new DuckDbTableWriter();

    private static final String DB_TYPE = "DUCKDB";
    /** 集合写入单条 SQL 的最大行数，避免 SQL 与参数数量过大。 */
    private static final int SET_MAX_ROWS = 1_000;
    /** 集合写入单条 SQL 的最大参数数（实测 4 万参数可用，这里留出余量）。 */
    private static final int SET_MAX_PARAMS = 32_000;
    /** 列类型白名单，仅用于构造 CAST 表达式，防止异常类型名拼进 SQL。 */
    private static final Pattern TYPE_PATTERN = Pattern.compile("[A-Za-z0-9_(),\\s\\[\\]<>'\"]+");

    private DuckDbTableWriter() {}

    boolean supports(TableMeta table, String eventType) {
        if (table == null || eventType == null) {
            return false;
        }
        String type = eventType.toUpperCase(Locale.ROOT);
        if (isAppenderEvent(type)) {
            return true;
        }
        if ("UPDATE".equals(type) || "DELETE".equals(type)) {
            return !getPrimaryKeyColumns(table).isEmpty();
        }
        return false;
    }

    String write(Connection connection, TableMeta table, JSONArray records, String eventType,
                 Map<String, String> columnMapping) throws SQLException {
        String type = eventType.toUpperCase(Locale.ROOT);
        if (isAppenderEvent(type)) {
            // 返回 null 表示 Appender 不可用且尚未写入任何行，调用方回退到通用 JDBC 写入
            return DuckDbAppenderWriter.append(connection, table, records, columnMapping) ? "DuckDBAppender" : null;
        }
        if (getPrimaryKeyColumns(table).isEmpty()) {
            throw new SQLException("DuckDB 集合写入要求目标表存在主键，表：" + table.getSchema() + "." + table.getTable());
        }
        writeSetBased(connection, table, records, type, columnMapping);
        return "DuckDB 集合写入";
    }

    private boolean isAppenderEvent(String eventType) {
        return "INSERT".equals(eventType) || "APPEND".equals(eventType) || "OVERWRITE".equals(eventType);
    }

    // ------------------------------------------------------------------ Appender 写入

    // ------------------------------------------------------------------ 集合式写入

    private void writeSetBased(Connection connection, TableMeta table, JSONArray records, String eventType,
                               Map<String, String> columnMapping) throws SQLException {
        List<ColumnMeta> primaryKeys = getPrimaryKeyColumns(table);
        if ("UPDATE".equals(eventType)) {
            // 主键变更记录单独按事件顺序处理；普通更新仍然攒批走集合 upsert。
            // 这样既能正确处理「同一事务内链式改键」，也避免退回逐行 INSERT OR REPLACE。
            List<JSONObject> pending = new ArrayList<>();
            for (Object o : records) {
                JSONObject record = (JSONObject) o;
                if (primaryKeyChanged(table, record)) {
                    flushPendingUpsert(connection, table, primaryKeys, pending, columnMapping);
                    pending.clear();

                    JSONObject before = record.getJSONObject("__before");
                    if (before != null) {
                        JSONArray beforeImages = new JSONArray();
                        beforeImages.add(before);
                        deleteByPrimaryKey(connection, table, primaryKeys, beforeImages);
                    }
                    JSONArray afterImages = new JSONArray();
                    afterImages.add(record);
                    upsertByPrimaryKey(connection, table, primaryKeys, afterImages, columnMapping);
                } else {
                    pending.add(record);
                }
            }
            flushPendingUpsert(connection, table, primaryKeys, pending, columnMapping);
        } else {
            deleteByPrimaryKey(connection, table, primaryKeys, records);
        }
    }

    private void flushPendingUpsert(Connection connection, TableMeta table, List<ColumnMeta> primaryKeys,
                                    List<JSONObject> pending, Map<String, String> columnMapping) throws SQLException {
        if (pending.isEmpty()) {
            return;
        }
        JSONArray records = new JSONArray();
        records.addAll(pending);
        upsertByPrimaryKey(connection, table, primaryKeys, records, columnMapping);
    }

    /**
     * 集合 upsert：{@code INSERT INTO ... SELECT ... FROM (VALUES ...) ON CONFLICT (pk) DO UPDATE}。
     */
    private void upsertByPrimaryKey(Connection connection, TableMeta table, List<ColumnMeta> primaryKeys,
                                    JSONArray records, Map<String, String> columnMapping) throws SQLException {
        JSONArray deduplicated = deduplicateByPrimaryKey(records, primaryKeys);
        if (deduplicated == null || deduplicated.isEmpty()) {
            return;
        }
        List<ColumnMeta> columns = table.columns();
        List<ColumnMeta> updateColumns = new ArrayList<>();
        for (ColumnMeta column : columns) {
            if (!column.isPrimaryKey()) {
                updateColumns.add(column);
            }
        }

        String tableName = qualifiedTableName(table);
        String columnList = joinQuotedColumns(columns);
        String conflictColumns = joinQuotedColumns(primaryKeys);
        int chunkRows = resolveSetRows(columns.size());
        int offset = 0;
        while (offset < deduplicated.size()) {
            int rows = Math.min(chunkRows, deduplicated.size() - offset);
            StringBuilder sql = new StringBuilder("INSERT INTO ")
                .append(tableName)
                .append(" (")
                .append(columnList)
                .append(") SELECT ");
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                ColumnMeta column = columns.get(i);
                sql.append(castExpression("v." + quoteIdentifier(column.getName()), column));
            }
            sql.append(" FROM (VALUES ")
                .append(valuesPlaceholders(rows, columns.size()))
                .append(") AS v(")
                .append(columnList)
                .append(") ON CONFLICT (")
                .append(conflictColumns)
                .append(')');
            if (updateColumns.isEmpty()) {
                sql.append(" DO NOTHING");
            } else {
                sql.append(" DO UPDATE SET ");
                for (int i = 0; i < updateColumns.size(); i++) {
                    if (i > 0) {
                        sql.append(", ");
                    }
                    String name = quoteIdentifier(updateColumns.get(i).getName());
                    sql.append(name).append(" = excluded.").append(name);
                }
            }

            try (PreparedStatement targetStmt = connection.prepareStatement(sql.toString())) {
                int parameterIndex = 1;
                for (int r = 0; r < rows; r++) {
                    JSONObject record = (JSONObject) deduplicated.get(offset + r);
                    for (ColumnMeta column : columns) {
                        String sourceColumn = column.getName();
                        if (columnMapping != null) {
                            sourceColumn = columnMapping.getOrDefault(sourceColumn, sourceColumn);
                        }
                        DatabaseConverter.bindParameter(
                            DB_TYPE,
                            targetStmt,
                            parameterIndex++,
                            column,
                            record.get(sourceColumn)
                        );
                    }
                }
                targetStmt.executeUpdate();
            }
            offset += rows;
        }
    }

    /**
     * 集合删除：{@code DELETE FROM ... USING (VALUES ...) WHERE t.pk = v.pk}，支持复合主键。
     */
    private void deleteByPrimaryKey(Connection connection, TableMeta table, List<ColumnMeta> primaryKeys,
                                    JSONArray records) throws SQLException {
        if (records == null || records.isEmpty()) {
            return;
        }
        JSONArray deduplicated = deduplicateByPrimaryKey(records, primaryKeys);
        if (deduplicated.isEmpty()) {
            return;
        }
        String tableName = qualifiedTableName(table);
        String primaryKeyList = joinQuotedColumns(primaryKeys);
        int chunkRows = resolveSetRows(primaryKeys.size());
        int offset = 0;
        while (offset < deduplicated.size()) {
            int rows = Math.min(chunkRows, deduplicated.size() - offset);
            StringBuilder sql = new StringBuilder("DELETE FROM ")
                .append(tableName)
                .append(" AS t USING (VALUES ")
                .append(valuesPlaceholders(rows, primaryKeys.size()))
                .append(") AS v(")
                .append(primaryKeyList)
                .append(") WHERE ");
            for (int i = 0; i < primaryKeys.size(); i++) {
                if (i > 0) {
                    sql.append(" AND ");
                }
                ColumnMeta column = primaryKeys.get(i);
                String quoted = quoteIdentifier(column.getName());
                sql.append("t.").append(quoted).append(" = ");
                sql.append(castExpression("v." + quoted, column));
            }

            try (PreparedStatement targetStmt = connection.prepareStatement(sql.toString())) {
                int parameterIndex = 1;
                for (int r = 0; r < rows; r++) {
                    JSONObject record = (JSONObject) deduplicated.get(offset + r);
                    for (ColumnMeta column : primaryKeys) {
                        DatabaseConverter.bindParameter(
                            DB_TYPE,
                            targetStmt,
                            parameterIndex++,
                            column,
                            record.get(column.getName())
                        );
                    }
                }
                targetStmt.executeUpdate();
            }
            offset += rows;
        }
    }

    // ------------------------------------------------------------------ 主键与去重

    /**
     * 按主键去重，保留最后一次出现的记录（CDC 同事务多次更新同一行时，后写生效）。
     * DuckDB 的 {@code ON CONFLICT} 在同一语句内遇到重复主键时保留第一条，必须先自行去重。
     */
    private JSONArray deduplicateByPrimaryKey(JSONArray records, List<ColumnMeta> primaryKeys) {
        if (records == null || records.size() <= 1 || primaryKeys.isEmpty()) {
            return records;
        }
        Map<List<String>, JSONObject> deduplicated = new LinkedHashMap<>();
        for (Object o : records) {
            JSONObject record = (JSONObject) o;
            List<String> key = new ArrayList<>(primaryKeys.size());
            for (ColumnMeta column : primaryKeys) {
                key.add(primaryKeyToken(record.get(column.getName())));
            }
            // 先删后放，保证顺序按最后一次出现的位置排列
            deduplicated.remove(key);
            deduplicated.put(key, record);
        }
        JSONArray result = new JSONArray();
        result.addAll(deduplicated.values());
        return result;
    }

    private String primaryKeyToken(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[]) {
            return java.util.Arrays.toString((byte[]) value);
        }
        return value.toString();
    }

    /**
     * UPDATE 事件的主键是否发生变化。CDC 的 UPDATE 事件会把变更前镜像放在 {@code __before} 中。
     */
    private boolean primaryKeyChanged(TableMeta table, JSONObject record) {
        JSONObject before = record.getJSONObject("__before");
        if (before == null) {
            return false;
        }
        for (ColumnMeta column : getPrimaryKeyColumns(table)) {
            String oldValue = stringValue(before.get(column.getName()));
            String newValue = stringValue(record.get(column.getName()));
            if (!java.util.Objects.equals(oldValue, newValue)) {
                return true;
            }
        }
        return false;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private List<ColumnMeta> getPrimaryKeyColumns(TableMeta table) {
        List<ColumnMeta> primaryKeys = new ArrayList<>();
        for (ColumnMeta column : table.columns()) {
            if (column.isPrimaryKey()) {
                primaryKeys.add(column);
            }
        }
        return primaryKeys;
    }

    // ------------------------------------------------------------------ SQL 辅助

    private String qualifiedTableName(TableMeta table) {
        if (table.getSchema() == null || table.getSchema().isBlank()) {
            return table.getTable();
        }
        return table.getSchema() + "." + table.getTable();
    }

    private int resolveSetRows(int boundColumns) {
        int byParams = SET_MAX_PARAMS / Math.max(1, boundColumns);
        return Math.max(1, Math.min(SET_MAX_ROWS, byParams));
    }

    private String valuesPlaceholders(int rows, int columns) {
        StringBuilder sql = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            if (r > 0) {
                sql.append(',');
            }
            sql.append('(');
            for (int c = 0; c < columns; c++) {
                if (c > 0) {
                    sql.append(',');
                }
                sql.append('?');
            }
            sql.append(')');
        }
        return sql.toString();
    }

    private String joinQuotedColumns(List<ColumnMeta> columns) {
        List<String> names = new ArrayList<>(columns.size());
        for (ColumnMeta column : columns) {
            names.add(quoteIdentifier(column.getName()));
        }
        return String.join(", ", names);
    }

    private String quoteIdentifier(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    /**
     * VALUES 中的参数默认是无类型的，显式 CAST 到目标列类型，避免 DuckDB 推断成 VARCHAR
     * 后与数值/时间列比较或赋值时出现隐式转换问题。
     */
    private String castExpression(String expression, ColumnMeta column) {
        String type = column.getType();
        if (type == null || type.isBlank() || !TYPE_PATTERN.matcher(type).matches()) {
            return expression;
        }
        // DuckDB 的 DECIMAL 不带精度时会使用默认 scale，导致小数位丢失；
        // 这里按列元数据补全 DECIMAL(p,s)。
        String upper = type.toUpperCase(Locale.ROOT);
        if ((upper.equals("DECIMAL") || upper.equals("NUMERIC")) && !type.contains("(") && column.getPrecision() > 0) {
            type = type + "(" + column.getPrecision() + "," + Math.max(column.getScale(), 0) + ")";
        }
        return "CAST(" + expression + " AS " + type + ")";
    }
}
