package com.data.datafusion.service;

import com.data.datafusion.service.FileStorageConfigService.FileStorageConfig;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 数据比对 SQL 生成器。
 *
 * <p>负责生成两类 SQL：
 * <ul>
 *   <li>比对 SQL：在 ETL 临时 DuckDB 中生成 dc_compare / dc_result / dc_summary。</li>
 *   <li>导出 SQL：通过 DuckDB COPY 将结果表写成 CSV/Parquet，并写入本地或 MinIO。</li>
 * </ul>
 */
@Component
public class DataCompareSqlBuilder {

    public static final String LOCAL_TABLE = "dc_left";
    public static final String RIGHT_TABLE = "dc_right";
    public static final String COMPARE_TABLE = "dc_compare";
    public static final String RESULT_TABLE = "dc_result";
    public static final String SUMMARY_TABLE = "dc_summary";

    private static final String DIFF_TYPE = "diff_type";
    private static final String PRESENT_COLUMN = "__present";

    /**
     * 解析逗号分隔字段，去空白、去重并保持顺序。
     */
    public List<String> parseColumns(String columns) {
        if (columns == null || columns.trim().isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String column : columns.split(",")) {
            String trimmed = column.trim();
            if (!trimmed.isEmpty()) {
                validateColumn(trimmed);
                result.add(trimmed);
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * 生成比对 SQL。
     *
     * @param keyColumns      主键/关联字段
     * @param compareColumns  比对字段，可为空（只比较主键存在性）
     * @param ignoreCase      是否忽略大小写
     * @param trimWhitespace  是否去除首尾空格
     * @return 可在 DuckDB 中执行的完整 SQL
     */
    public String buildCompareSql(List<String> keyColumns, List<String> compareColumns, boolean ignoreCase, boolean trimWhitespace) {
        return buildCompareSql("", keyColumns, compareColumns, ignoreCase, trimWhitespace);
    }

    /**
     * 生成多表场景下带后缀的比对 SQL。
     */
    public String buildCompareSql(
        String suffix,
        List<String> keyColumns,
        List<String> compareColumns,
        boolean ignoreCase,
        boolean trimWhitespace
    ) {
        String localTable = tableName(LOCAL_TABLE, suffix);
        String rightTable = tableName(RIGHT_TABLE, suffix);
        String compareTable = tableName(COMPARE_TABLE, suffix);
        String resultTable = tableName(RESULT_TABLE, suffix);
        String summaryTable = tableName(SUMMARY_TABLE, suffix);
        if (keyColumns == null || keyColumns.isEmpty()) {
            throw new IllegalArgumentException("主键字段不能为空");
        }
        keyColumns.forEach(DataCompareSqlBuilder::validateColumn);
        if (compareColumns != null) {
            compareColumns.forEach(DataCompareSqlBuilder::validateColumn);
        }

        List<String> keys = new ArrayList<>(keyColumns);
        List<String> compares = compareColumns == null ? new ArrayList<>() : new ArrayList<>(compareColumns);
        compares.removeAll(keys);

        String keyProjection = projection(keys, ignoreCase, trimWhitespace);
        String compareProjection = projection(compares, ignoreCase, trimWhitespace);

        String leftColumns = keyProjection + (compareProjection.isEmpty() ? "" : ", " + compareProjection) + ", 1 AS " + q(PRESENT_COLUMN);
        String joinCondition = keys
            .stream()
            .map(key -> "l." + q(key) + " IS NOT DISTINCT FROM r." + q(key))
            .reduce((a, b) -> a + " AND " + b)
            .orElse("1 = 1");

        StringBuilder valueDiff = new StringBuilder();
        for (String column : compares) {
            if (valueDiff.length() > 0) {
                valueDiff.append(" OR ");
            }
            valueDiff.append("l.").append(q(column)).append(" IS DISTINCT FROM r.").append(q(column));
        }

        StringBuilder select = new StringBuilder();
        select.append("CASE ");
        select.append("WHEN r.").append(q(PRESENT_COLUMN)).append(" IS NULL THEN 'ONLY_LEFT' ");
        select.append("WHEN l.").append(q(PRESENT_COLUMN)).append(" IS NULL THEN 'ONLY_RIGHT' ");
        if (valueDiff.length() > 0) {
            select.append("WHEN ").append(valueDiff).append(" THEN 'VALUE_DIFF' ");
        }
        select.append("ELSE 'SAME' END AS ").append(q(DIFF_TYPE));

        List<String> resultColumns = new ArrayList<>();
        for (String key : keys) {
            select.append(", COALESCE(l.").append(q(key)).append(", r.").append(q(key)).append(") AS ").append(q("key__" + key));
            resultColumns.add(q("key__" + key));
        }
        for (String column : compares) {
            select.append(", l.").append(q(column)).append(" AS ").append(q("left__" + column));
            select.append(", r.").append(q(column)).append(" AS ").append(q("right__" + column));
            resultColumns.add(q("left__" + column));
            resultColumns.add(q("right__" + column));
        }
        resultColumns.add(0, q(DIFF_TYPE));

        String resultSelect = String.join(", ", resultColumns);

        StringBuilder sql = new StringBuilder();
        sql.append("CREATE OR REPLACE TABLE ").append(compareTable).append(" AS\n");
        sql.append("WITH l AS (\n");
        sql.append("  SELECT ").append(leftColumns).append(" FROM ").append(localTable).append("\n");
        sql.append("), r AS (\n");
        sql.append("  SELECT ").append(leftColumns).append(" FROM ").append(rightTable).append("\n");
        sql.append(")\n");
        sql.append("SELECT ").append(select).append("\n");
        sql.append("FROM l FULL OUTER JOIN r ON ").append(joinCondition).append(";\n\n");

        sql.append("CREATE OR REPLACE TABLE ").append(resultTable).append(" AS\n");
        sql.append("SELECT ").append(resultSelect).append("\n");
        sql.append("FROM ").append(compareTable).append("\n");
        sql.append("WHERE ").append(q(DIFF_TYPE)).append(" <> 'SAME';\n\n");

        sql.append("CREATE OR REPLACE TABLE ").append(summaryTable).append(" AS\n");
        sql.append("SELECT\n");
        sql.append("  (SELECT COUNT(*) FROM ").append(localTable).append(") AS left_rows,\n");
        sql.append("  (SELECT COUNT(*) FROM ").append(rightTable).append(") AS right_rows,\n");
        sql.append("  COUNT(*) FILTER (WHERE ").append(q(DIFF_TYPE)).append(" = 'SAME') AS same_rows,\n");
        sql.append("  COUNT(*) FILTER (WHERE ").append(q(DIFF_TYPE)).append(" = 'VALUE_DIFF') AS value_diff_rows,\n");
        sql.append("  COUNT(*) FILTER (WHERE ").append(q(DIFF_TYPE)).append(" = 'ONLY_LEFT') AS only_left_rows,\n");
        sql.append("  COUNT(*) FILTER (WHERE ").append(q(DIFF_TYPE)).append(" = 'ONLY_RIGHT') AS only_right_rows,\n");
        sql.append("  COUNT(*) FILTER (WHERE ").append(q(DIFF_TYPE)).append(" <> 'SAME') AS diff_rows\n");
        sql.append("FROM ").append(compareTable).append(";");
        return sql.toString();
    }

    /**
     * 仅记录数对比：左右输入表只包含一行 row_count，直接从该行生成汇总表，
     * 不拉取源表全量数据，也不生成 dc_compare / dc_result。
     */
    public String buildCountSummarySql(String suffix) {
        String localTable = tableName(LOCAL_TABLE, suffix);
        String rightTable = tableName(RIGHT_TABLE, suffix);
        String summaryTable = tableName(SUMMARY_TABLE, suffix);
        return (
            "CREATE OR REPLACE TABLE " +
            summaryTable +
            " AS\n" +
            "SELECT\n" +
            "  (SELECT row_count FROM " +
            localTable +
            ") AS left_rows,\n" +
            "  (SELECT row_count FROM " +
            rightTable +
            ") AS right_rows,\n" +
            "  ABS((SELECT row_count FROM " +
            localTable +
            ") - (SELECT row_count FROM " +
            rightTable +
            ")) AS diff_rows;"
        );
    }

    public static String tableName(String base, String suffix) {
        return suffix == null || suffix.isBlank() ? base : base + "_" + suffix;
    }

    /**
     * 生成 DuckDB 导出 SQL。
     *
     * @param storage       文件存储配置
     * @param tenantPrefix  租户前缀，如 tenants/default
     * @param resultPath    差异文件逻辑路径
     * @param summaryPath   汇总文件逻辑路径
     * @param format        结果格式：CSV / PARQUET
     * @return 可在 DuckDB 中执行的导出 SQL
     */
    public String buildExportSql(
        FileStorageConfig storage,
        String tenantPrefix,
        String resultPath,
        String summaryPath,
        String format
    ) {
        return buildExportSql(storage, tenantPrefix, resultPath, summaryPath, format, RESULT_TABLE, SUMMARY_TABLE);
    }

    public String buildExportSql(
        FileStorageConfig storage,
        String tenantPrefix,
        String resultPath,
        String summaryPath,
        String format,
        String resultTable,
        String summaryTable
    ) {
        boolean minio = storage != null && "minio".equalsIgnoreCase(storage.type);
        String resultTarget = minio
            ? "s3://" + storage.minioBucketName + "/" + joinPath(tenantPrefix, resultPath)
            : localPath(storage == null ? "./data/files" : storage.localBasePath, tenantPrefix, resultPath);
        String summaryTarget = minio
            ? "s3://" + storage.minioBucketName + "/" + joinPath(tenantPrefix, summaryPath)
            : localPath(storage == null ? "./data/files" : storage.localBasePath, tenantPrefix, summaryPath);

        StringBuilder sql = new StringBuilder();
        if (minio) {
            String endpoint = storage.minioEndpoint == null ? "" : storage.minioEndpoint.replaceFirst("^https?://", "");
            sql.append("CREATE OR REPLACE SECRET dc_export_s3_secret (\n");
            sql.append("  TYPE s3,\n");
            sql.append("  KEY_ID '").append(escape(storage.minioAccessKey)).append("',\n");
            sql.append("  SECRET '").append(escape(storage.minioSecretKey)).append("',\n");
            sql.append("  ENDPOINT '").append(escape(endpoint)).append("',\n");
            sql.append("  url_style 'path',\n");
            sql.append("  USE_SSL ").append(storage.minioEndpoint != null && storage.minioEndpoint.startsWith("https://")).append("\n");
            sql.append(");\n\n");
        }

        String resultFormat = format == null ? "CSV" : format.trim().toUpperCase(Locale.ROOT);
        if ("PARQUET".equals(resultFormat)) {
            sql.append("COPY (SELECT * FROM ").append(resultTable).append(") TO '").append(escape(resultTarget)).append("' (FORMAT PARQUET);\n");
        } else {
            sql
                .append("COPY (SELECT * FROM ")
                .append(resultTable)
                .append(") TO '")
                .append(escape(resultTarget))
                .append("' (FORMAT CSV, HEADER);\n");
        }
        sql
            .append("COPY (SELECT * FROM ")
            .append(summaryTable)
            .append(") TO '")
            .append(escape(summaryTarget))
            .append("' (FORMAT CSV, HEADER);");
        return sql.toString();
    }

    /**
     * 仅导出汇总文件（记录数对比模式）。
     */
    public String buildSummaryExportSql(
        FileStorageConfig storage,
        String tenantPrefix,
        String summaryPath,
        String format,
        String summaryTable
    ) {
        boolean minio = storage != null && "minio".equalsIgnoreCase(storage.type);
        String summaryTarget = minio
            ? "s3://" + storage.minioBucketName + "/" + joinPath(tenantPrefix, summaryPath)
            : localPath(storage == null ? "./data/files" : storage.localBasePath, tenantPrefix, summaryPath);
        StringBuilder sql = new StringBuilder();
        if (minio) {
            String endpoint = storage.minioEndpoint == null ? "" : storage.minioEndpoint.replaceFirst("^https?://", "");
            sql.append("CREATE OR REPLACE SECRET dc_export_s3_secret (\n");
            sql.append("  TYPE s3,\n");
            sql.append("  KEY_ID '").append(escape(storage.minioAccessKey)).append("',\n");
            sql.append("  SECRET '").append(escape(storage.minioSecretKey)).append("',\n");
            sql.append("  ENDPOINT '").append(escape(endpoint)).append("',\n");
            sql.append("  url_style 'path',\n");
            sql.append("  USE_SSL ").append(storage.minioEndpoint != null && storage.minioEndpoint.startsWith("https://")).append("\n");
            sql.append(");\n\n");
        }
        sql
            .append("COPY (SELECT * FROM ")
            .append(summaryTable)
            .append(") TO '")
            .append(escape(summaryTarget))
            .append("' (FORMAT CSV, HEADER);");
        return sql.toString();
    }

    private String projection(List<String> columns, boolean ignoreCase, boolean trimWhitespace) {
        List<String> parts = new ArrayList<>();
        for (String column : columns) {
            String expression = "CAST(" + q(column) + " AS VARCHAR)";
            if (trimWhitespace) {
                expression = "TRIM(" + expression + ")";
            }
            if (ignoreCase) {
                expression = "LOWER(" + expression + ")";
            }
            parts.add(expression + " AS " + q(column));
        }
        return String.join(", ", parts);
    }

    private String localPath(String basePath, String tenantPrefix, String path) {
        Path base = Paths.get(basePath == null || basePath.isBlank() ? "./data/files" : basePath).toAbsolutePath().normalize();
        return base.resolve(joinPath(tenantPrefix, path)).normalize().toString();
    }

    public static String joinPath(String... parts) {
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String normalized = part.replace('\\', '/');
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            if (normalized.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(normalized);
        }
        return result.toString();
    }

    public static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static String q(String identifier) {
        return quote(identifier);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private static void validateColumn(String column) {
        if (column == null || column.trim().isEmpty()) {
            throw new IllegalArgumentException("字段名不能为空");
        }
        if (column.indexOf('\0') >= 0 || column.indexOf('\n') >= 0 || column.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("字段名包含非法字符: " + column);
        }
    }
}
