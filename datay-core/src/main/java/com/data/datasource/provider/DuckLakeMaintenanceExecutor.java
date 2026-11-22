package com.data.datasource.provider;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DuckLake 常用运维操作：落盘内联数据、合并/重写数据文件、快照过期、文件清理与 CHECKPOINT。
 *
 * <p>操作均通过 DuckLake 扩展提供的表函数执行。快照/文件清理默认使用 dry-run，
 * 由前端二次确认后再执行真实删除。Catalog 级操作会作用于整个 DuckLake Catalog。</p>
 */
public final class DuckLakeMaintenanceExecutor {

    private static final String DUCKLAKE_CATALOG = "ducklake";
    private static final int RESULT_ROW_LIMIT = 200;

    private static final List<Map<String, Object>> ACTIONS = buildActions();

    public static final DuckLakeMaintenanceExecutor INSTANCE = new DuckLakeMaintenanceExecutor();

    private DuckLakeMaintenanceExecutor() {}

    public static List<Map<String, Object>> actions() {
        return ACTIONS;
    }

    public Map<String, Object> execute(
        Connection conn,
        String catalog,
        String schema,
        String table,
        String action,
        Map<String, Object> params
    ) throws SQLException {
        if (action == null || action.isBlank()) {
            return failure("运维操作不能为空");
        }
        Map<String, Object> actionMeta = findAction(action);
        if (actionMeta == null || Boolean.FALSE.equals(actionMeta.get("available"))) {
            return failure("不支持或当前不可用的运维操作：" + action);
        }
        Map<String, Object> safeParams = params == null ? Map.of() : params;
        String targetTable = table == null ? "" : table;
        String targetSchema = schema == null ? "" : schema;

        return switch (action) {
            case "refresh_metadata" -> refreshMetadata(conn);
            case "flush_inlined_data" -> flushInlinedData(conn, targetSchema, targetTable);
            case "merge_adjacent_files" -> mergeAdjacentFiles(conn, targetSchema, targetTable, safeParams);
            case "rewrite_data_files" -> rewriteDataFiles(conn, targetSchema, targetTable, safeParams);
            case "expire_snapshots" -> expireSnapshots(conn, safeParams);
            case "cleanup_old_files" -> cleanupOldFiles(conn, safeParams);
            case "delete_orphaned_files" -> deleteOrphanedFiles(conn, safeParams);
            case "checkpoint" -> checkpoint(conn);
            default -> failure("不支持的运维操作：" + action);
        };
    }

    // ------------------------------------------------------------------ 具体操作

    private static Map<String, Object> refreshMetadata(Connection conn) throws SQLException {
        query(conn, "SELECT * FROM ducklake_current_snapshot(" + quoteLiteral(DUCKLAKE_CATALOG) + ")");
        return success("元数据已刷新", List.of(), null);
    }

    private static Map<String, Object> flushInlinedData(Connection conn, String schema, String table)
        throws SQLException {
        List<Map<String, Object>> rows = query(
            conn,
            "SELECT * FROM ducklake_flush_inlined_data(" +
            quoteLiteral(DUCKLAKE_CATALOG) +
            ", schema_name => " +
            quoteLiteral(schema) +
            ", table_name => " +
            quoteLiteral(table) +
            ")"
        );
        long flushed = sum(rows, "rows_flushed");
        String message = rows.isEmpty() ? "没有需要落盘的内联数据" : "已落盘 " + flushed + " 行内联数据";
        return success(message, rows, null);
    }

    private static Map<String, Object> mergeAdjacentFiles(
        Connection conn,
        String schema,
        String table,
        Map<String, Object> params
    ) throws SQLException {
        int maxCompactedFiles = intParam(params, "maxCompactedFiles", 10, 1, 10000);
        List<Map<String, Object>> rows = query(
            conn,
            "SELECT * FROM ducklake_merge_adjacent_files(" +
            quoteLiteral(DUCKLAKE_CATALOG) +
            ", " +
            quoteLiteral(table) +
            ", schema => " +
            quoteLiteral(schema) +
            ", max_compacted_files => " +
            maxCompactedFiles +
            ")"
        );
        return success(compactionMessage(rows), rows, null);
    }

    private static Map<String, Object> rewriteDataFiles(
        Connection conn,
        String schema,
        String table,
        Map<String, Object> params
    ) throws SQLException {
        double threshold = doubleParam(params, "deleteThreshold", 0.95, 0.0, 1.0);
        List<Map<String, Object>> rows = query(
            conn,
            "SELECT * FROM ducklake_rewrite_data_files(" +
            quoteLiteral(DUCKLAKE_CATALOG) +
            ", " +
            quoteLiteral(table) +
            ", schema => " +
            quoteLiteral(schema) +
            ", delete_threshold => " +
            threshold +
            ")"
        );
        return success(compactionMessage(rows), rows, null);
    }

    private static Map<String, Object> expireSnapshots(Connection conn, Map<String, Object> params)
        throws SQLException {
        boolean dryRun = boolParam(params, "dryRun", true);
        int olderThanDays = intParam(params, "olderThanDays", 7, 1, 36500);
        List<Map<String, Object>> rows = query(
            conn,
            "SELECT * FROM ducklake_expire_snapshots(" +
            quoteLiteral(DUCKLAKE_CATALOG) +
            ", dry_run => " +
            dryRun +
            ", older_than => now() - INTERVAL '" +
            olderThanDays +
            " days')"
        );
        String message = dryRun
            ? "演练完成：预计过期 " + rows.size() + " 个快照"
            : "已过期 " + rows.size() + " 个快照";
        if (rows.isEmpty()) {
            message = dryRun ? "演练完成：没有需要过期的快照" : "没有需要过期的快照";
        }
        return success(message, rows, dryRun);
    }

    private static Map<String, Object> cleanupOldFiles(Connection conn, Map<String, Object> params)
        throws SQLException {
        boolean dryRun = boolParam(params, "dryRun", true);
        int olderThanDays = intParam(params, "olderThanDays", 7, 1, 36500);
        List<Map<String, Object>> rows = query(
            conn,
            "SELECT * FROM ducklake_cleanup_old_files(" +
            quoteLiteral(DUCKLAKE_CATALOG) +
            ", dry_run => " +
            dryRun +
            ", older_than => now() - INTERVAL '" +
            olderThanDays +
            " days')"
        );
        String message = dryRun
            ? "演练完成：预计删除 " + rows.size() + " 个旧文件"
            : "已删除 " + rows.size() + " 个旧文件";
        if (rows.isEmpty()) {
            message = dryRun ? "演练完成：没有可清理的旧文件" : "没有可清理的旧文件";
        }
        return success(message, rows, dryRun);
    }

    private static Map<String, Object> deleteOrphanedFiles(Connection conn, Map<String, Object> params)
        throws SQLException {
        boolean dryRun = boolParam(params, "dryRun", true);
        int olderThanDays = intParam(params, "olderThanDays", 7, 1, 36500);
        List<Map<String, Object>> rows = query(
            conn,
            "SELECT * FROM ducklake_delete_orphaned_files(" +
            quoteLiteral(DUCKLAKE_CATALOG) +
            ", dry_run => " +
            dryRun +
            ", older_than => now() - INTERVAL '" +
            olderThanDays +
            " days')"
        );
        String message = dryRun
            ? "演练完成：预计删除 " + rows.size() + " 个孤儿文件"
            : "已删除 " + rows.size() + " 个孤儿文件";
        if (rows.isEmpty()) {
            message = dryRun ? "演练完成：没有发现孤儿文件" : "没有发现孤儿文件";
        }
        return success(message, rows, dryRun);
    }

    private static Map<String, Object> checkpoint(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("CHECKPOINT");
        }
        return success("DuckLake 完整维护（CHECKPOINT）已完成", List.of(), null);
    }

    // ------------------------------------------------------------------ 操作元数据

    private static List<Map<String, Object>> buildActions() {
        List<Map<String, Object>> actions = new ArrayList<>();
        actions.add(
            action(
                "refresh_metadata",
                "刷新元数据",
                "重新读取当前表的最新 DuckLake 元数据。",
                "TABLE",
                false,
                "刷新元数据不会修改数据，是否继续？",
                List.of()
            )
        );
        actions.add(
            action(
                "flush_inlined_data",
                "落盘内联数据",
                "将内联在 DuckLake 元数据中的行写入 Parquet 文件，降低 Catalog 压力。",
                "TABLE",
                false,
                "该操作会写入新的 Parquet 文件，是否继续？",
                List.of()
            )
        );
        actions.add(
            action(
                "merge_adjacent_files",
                "合并小文件",
                "合并相邻的小 Parquet 文件，减少文件数量，提升查询性能。",
                "TABLE",
                false,
                "合并操作可能读取并重写多个数据文件，是否继续？",
                List.of(
                    param(
                        "maxCompactedFiles",
                        "单次最多合并文件",
                        "number",
                        10,
                        1,
                        10000,
                        1,
                        "限制单次调用处理的数据文件数量，避免内存占用过高。"
                    )
                )
            )
        );
        actions.add(
            action(
                "rewrite_data_files",
                "清理删除标记",
                "重写含有较多删除记录的数据文件，清理 position/equality/inlined 删除标记。",
                "TABLE",
                false,
                "该操作会重写数据文件，可能耗时较长，是否继续？",
                List.of(
                    param(
                        "deleteThreshold",
                        "删除比例阈值",
                        "number",
                        0.95,
                        0.0,
                        1.0,
                        0.05,
                        "文件删除行占比达到该阈值时触发重写，默认 0.95。"
                    )
                )
            )
        );
        actions.add(
            action(
                "expire_snapshots",
                "过期快照",
                "过期整个 DuckLake Catalog 中早于指定天数的快照。过期后将无法时间旅行到这些快照。",
                "CATALOG",
                true,
                "该操作作用于整个 DuckLake Catalog，过期快照后将无法时间旅行。建议先使用演练模式确认影响范围。",
                List.of(
                    param("olderThanDays", "保留最近天数", "number", 7, 1, 36500, 1, "只过期早于该天数的快照。"),
                    param("dryRun", "演练模式", "boolean", true, null, null, null, "开启时只展示将被过期的快照，不实际删除。")
                )
            )
        );
        actions.add(
            action(
                "cleanup_old_files",
                "清理旧文件",
                "删除已调度删除且超过保留期的数据文件，作用于整个 DuckLake Catalog。",
                "CATALOG",
                true,
                "该操作会实际删除旧文件，作用于整个 DuckLake Catalog。建议先使用演练模式确认。",
                List.of(
                    param("olderThanDays", "清理早于天数", "number", 7, 1, 36500, 1, "只清理调度删除时间早于该天数的文件。"),
                    param("dryRun", "演练模式", "boolean", true, null, null, null, "开启时只展示将被删除的文件，不实际删除。")
                )
            )
        );
        actions.add(
            action(
                "delete_orphaned_files",
                "清理孤儿文件",
                "删除 DuckLake Catalog 未跟踪的孤儿 Parquet/Puffin 文件，作用于整个 Catalog。",
                "CATALOG",
                true,
                "孤儿文件删除会实际删除存储上的文件，建议先使用演练模式确认。",
                List.of(
                    param("olderThanDays", "只清理早于天数", "number", 7, 1, 36500, 1, "避免误删仍在写入或查询中的新文件。"),
                    param("dryRun", "演练模式", "boolean", true, null, null, null, "开启时只展示将被删除的文件，不实际删除。")
                )
            )
        );
        actions.add(
            action(
                "checkpoint",
                "一键维护",
                "依次执行落盘内联数据、过期快照、合并文件、重写删除文件、清理旧文件与孤儿文件。",
                "CATALOG",
                true,
                "该操作会对整个 DuckLake Catalog 执行完整维护，可能耗时较长，是否继续？",
                List.of()
            )
        );
        return List.copyOf(actions);
    }

    private static Map<String, Object> findAction(String action) {
        for (Map<String, Object> item : ACTIONS) {
            if (action.equals(item.get("id"))) {
                return item;
            }
        }
        return null;
    }

    private static Map<String, Object> action(
        String id,
        String label,
        String description,
        String scope,
        boolean danger,
        String confirm,
        List<Map<String, Object>> params
    ) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("id", id);
        action.put("label", label);
        action.put("description", description);
        action.put("scope", scope);
        action.put("danger", danger);
        action.put("confirm", confirm);
        action.put("available", true);
        action.put("params", params);
        return action;
    }

    private static Map<String, Object> param(
        String name,
        String label,
        String type,
        Object defaultValue,
        Number min,
        Number max,
        Number step,
        String description
    ) {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("name", name);
        param.put("label", label);
        param.put("type", type);
        param.put("default", defaultValue);
        param.put("min", min);
        param.put("max", max);
        param.put("step", step);
        param.put("description", description);
        return param;
    }

    // ------------------------------------------------------------------ 结果与工具

    private static String compactionMessage(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "没有需要处理的数据文件";
        }
        long processed = sum(rows, "files_processed");
        long created = sum(rows, "files_created");
        return "处理 " + processed + " 个数据文件，生成 " + created + " 个文件";
    }

    private static long sum(List<Map<String, Object>> rows, String key) {
        long total = 0L;
        for (Map<String, Object> row : rows) {
            Object value = row.get(key);
            if (value instanceof Number number) {
                total += number.longValue();
            }
        }
        return total;
    }

    private static Map<String, Object> success(
        String message,
        List<Map<String, Object>> rows,
        Boolean dryRun
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", message);
        result.put("rowCount", rows.size());
        boolean truncated = rows.size() > RESULT_ROW_LIMIT;
        result.put("rowsTruncated", truncated);
        result.put("rows", truncated ? new ArrayList<>(rows.subList(0, RESULT_ROW_LIMIT)) : rows);
        if (dryRun != null) {
            result.put("dryRun", dryRun);
        }
        return result;
    }

    private static Map<String, Object> failure(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", message);
        result.put("rows", List.of());
        return result;
    }

    private static List<Map<String, Object>> query(Connection conn, String sql) throws SQLException {
        try (
            Statement statement = conn.createStatement();
            ResultSet rs = statement.executeQuery(sql)
        ) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnLabel(i), normalizeValue(rs.getObject(i)));
                }
                rows.add(row);
            }
            return rows;
        }
    }

    /**
     * 将 DuckDB 的 STRUCT / MAP / 时间类型转换为 Jackson 可序列化的 JSON 值。
     */
    private static Object normalizeValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
            }
            return normalized;
        }
        if (value instanceof java.sql.Array sqlArray) {
            try {
                return normalizeValue(sqlArray.getArray());
            } catch (SQLException e) {
                return String.valueOf(value);
            }
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : iterable) {
                normalized.add(normalizeValue(item));
            }
            return normalized;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> normalized = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                normalized.add(normalizeValue(java.lang.reflect.Array.get(value, i)));
            }
            return normalized;
        }
        if (value instanceof java.time.temporal.TemporalAccessor) {
            return value.toString();
        }
        return String.valueOf(value);
    }

    private static boolean boolParam(Map<String, Object> params, String name, boolean defaultValue) {
        Object value = params.get(name);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return defaultValue;
    }

    private static int intParam(
        Map<String, Object> params,
        String name,
        int defaultValue,
        int min,
        int max
    ) {
        Object value = params.get(name);
        int parsed = defaultValue;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else if (value != null) {
            try {
                parsed = Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                parsed = defaultValue;
            }
        }
        return Math.max(min, Math.min(max, parsed));
    }

    private static double doubleParam(
        Map<String, Object> params,
        String name,
        double defaultValue,
        double min,
        double max
    ) {
        Object value = params.get(name);
        double parsed = defaultValue;
        if (value instanceof Number number) {
            parsed = number.doubleValue();
        } else if (value != null) {
            try {
                parsed = Double.parseDouble(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                parsed = defaultValue;
            }
        }
        return Math.max(min, Math.min(max, parsed));
    }

    private static String quoteLiteral(String value) {
        return "'" + String.valueOf(value == null ? "" : value).replace("'", "''") + "'";
    }
}
