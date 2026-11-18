package com.data.metadata.impl;

import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Apache Polaris（Iceberg）类型与 SQL 生成器。
 *
 * <p>Iceberg 表由 DuckDB 的 {@code iceberg} 扩展读写，因此 DDL/DML 使用 DuckDB 方言，但存在以下限制：</p>
 * <ul>
 *   <li>不支持主键、唯一约束，建表语句不能带 {@code PRIMARY KEY}；</li>
 *   <li>不支持 {@code CREATE TABLE ... COMMENT='...'}，表注释需要在建表后单独设置；</li>
 *   <li>不支持 {@code INSERT OR REPLACE} / {@code ON CONFLICT}，UPDATE 由
 *       {@code PolarisTableWriter} 按前镜像生成 {@code UPDATE ... WHERE} 语句；</li>
 *   <li>类型需收敛到 Iceberg 支持的类型集合（boolean/int/long/float/double/decimal/string/binary/date/time/timestamp/uuid）。</li>
 * </ul>
 */
public class PolarisConverter extends DuckLakeConverter {

    /** Iceberg 的 decimal 最大精度。 */
    private static final int MAX_DECIMAL_PRECISION = 38;

    @Override
    public ColumnMeta toTargetColumnType(ColumnMeta columnMeta) {
        ColumnMeta column = super.toTargetColumnType(columnMeta);
        String originalBase = DatabaseConverter.stripTypeParameters(
            columnMeta.getType() == null ? "" : columnMeta.getType().toUpperCase(Locale.ROOT)
        );
        // 优先按源类型归一化：DuckLake 映射表未覆盖的无符号整数（UINTEGER 等）会被退化为 VARCHAR，
        // 先按源类型识别可避免类型丢失；其次再按 DuckLake 映射结果归一化（如 NUMBER -> BIGINT）。
        String type = normalizeType(originalBase, columnMeta);
        if (type == null) {
            String mappedBase = DatabaseConverter.stripTypeParameters(
                column.getType() == null ? "" : column.getType().toUpperCase(Locale.ROOT)
            );
            type = normalizeType(mappedBase, column);
        }
        if (type == null) {
            type = "VARCHAR";
        }

        ColumnMeta target = new ColumnMeta(columnMeta.getName(), type);
        target.setNullable(columnMeta.isNullable());
        target.setDefaultValue(columnMeta.getDefaultValue());
        target.setComment(columnMeta.getComment());
        // Iceberg 没有主键概念，显式清除，避免后续 DML 误用主键匹配
        target.setPrimaryKey(false);
        if ("DECIMAL".equals(type)) {
            int precision = column.getPrecision();
            int scale = column.getScale();
            if (precision <= 0) {
                precision = 18;
                scale = Math.max(scale, 3);
            }
            precision = Math.min(precision, MAX_DECIMAL_PRECISION);
            scale = Math.max(0, Math.min(scale, precision));
            target.setPrecision(precision);
            target.setScale(scale);
        } else {
            target.setLength(columnMeta.getLength());
        }
        return target;
    }

    private String normalizeType(String baseType, ColumnMeta column) {
        switch (baseType) {
            case "CHAR":
            case "TEXT":
            case "JSON":
            case "VARCHAR":
                return "VARCHAR";
            case "BIT":
                return column.getLength() == 1 ? "BOOLEAN" : "BLOB";
            case "TINYINT":
            case "SMALLINT":
            case "INT":
            case "INTEGER":
            case "UTINYINT":
            case "USMALLINT":
                return "INTEGER";
            case "UINTEGER":
            case "BIGINT":
                return "BIGINT";
            case "FLOAT":
                return "FLOAT";
            case "DOUBLE":
                return "DOUBLE";
            case "DECIMAL":
                return "DECIMAL";
            case "BLOB":
                return "BLOB";
            case "DATE":
                return "DATE";
            case "TIME":
                return "TIME";
            case "TIMESTAMP":
                return "TIMESTAMP";
            case "UUID":
                return "UUID";
            case "BOOLEAN":
                return "BOOLEAN";
            default:
                return null;
        }
    }

    @Override
    public String generateTableDDL(TableMeta table) {
        StringBuilder ddl = new StringBuilder("CREATE TABLE ").append(qualifiedTableName(table)).append(" (\n");
        List<ColumnMeta> columns = table.columns();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                ddl.append(",\n");
            }
            ddl.append("  ").append(generateColumnDDL(columns.get(i)));
        }
        ddl.append("\n)");
        return ddl.toString();
    }

    @Override
    public String generateColumnDDL(ColumnMeta column) {
        String type = column.getType() == null ? "VARCHAR" : column.getType().toUpperCase(Locale.ROOT);
        if ("DECIMAL".equals(DatabaseConverter.stripTypeParameters(type))) {
            int precision = column.getPrecision() > 0 ? Math.min(column.getPrecision(), MAX_DECIMAL_PRECISION) : 18;
            int scale = Math.max(0, Math.min(column.getScale(), precision));
            type = "DECIMAL(" + precision + "," + scale + ")";
        }
        return quote(column.getName()) + " " + type;
    }

    @Override
    public String generateInsertSQL(TableMeta table) {
        List<String> names = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        for (ColumnMeta column : table.columns()) {
            names.add(quote(column.getName()));
            placeholders.add("?");
        }
        return "INSERT INTO " + qualifiedTableName(table) + " (" + String.join(", ", names) + ") VALUES (" +
            String.join(", ", placeholders) + ")";
    }

    /**
     * Iceberg 不支持 {@code INSERT OR REPLACE} / {@code ON CONFLICT}，UPDATE 语义由
     * {@code PolarisTableWriter} 按变更前镜像生成。
     */
    @Override
    public String generateUpsertSQL(TableMeta table) {
        return generateInsertSQL(table);
    }

    @Override
    public String generateReplaceSQL(TableMeta table) {
        return generateInsertSQL(table);
    }

    /**
     * Iceberg 没有主键：有主键元数据时按主键匹配，否则用全部列做前镜像匹配。
     * 使用 {@code IS NOT DISTINCT FROM} 以正确处理 NULL 值。
     */
    @Override
    public String generateDeleteSQL(TableMeta table) {
        List<ColumnMeta> primaryKeys = new ArrayList<>();
        for (ColumnMeta column : table.columns()) {
            if (column.isPrimaryKey()) {
                primaryKeys.add(column);
            }
        }
        List<ColumnMeta> conditions = primaryKeys.isEmpty() ? table.columns() : primaryKeys;
        List<String> where = new ArrayList<>();
        for (ColumnMeta column : conditions) {
            where.add(quote(column.getName()) + " IS NOT DISTINCT FROM ?");
        }
        return "DELETE FROM " + qualifiedTableName(table) + " WHERE " + String.join(" AND ", where);
    }

    @Override
    public List<String> getSupportedTypes() {
        return List.of(
            "BOOLEAN",
            "INTEGER",
            "BIGINT",
            "FLOAT",
            "DOUBLE",
            "DECIMAL",
            "VARCHAR",
            "BLOB",
            "DATE",
            "TIME",
            "TIMESTAMP",
            "UUID"
        );
    }

    private String qualifiedTableName(TableMeta table) {
        StringBuilder sb = new StringBuilder();
        if (table.getCatalog() != null && !table.getCatalog().isBlank()) {
            sb.append(quote(table.getCatalog())).append('.');
        }
        if (table.getSchema() != null && !table.getSchema().isBlank()) {
            sb.append(quote(table.getSchema())).append('.');
        }
        // 与 DuckDB Provider 的 normalizeTargetTableName 保持一致：统一使用小写表名，
        // 避免同步建表与后续 DML 使用不同大小写导致表找不到
        sb.append(quote(table.getTable().toLowerCase(Locale.ROOT)));
        return sb.toString();
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
