package com.data.metadata;

import com.data.metadata.impl.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分层元数据转换器
 * 转换流程：源物理类型 -> 标准逻辑类型 -> 目标物理类型
 */
public class DatabaseConverter {

    // 转换器注册表（线程安全）
    private static final Map<String, TypeConverter> converters = new HashMap<>();

    static {
        // 注册默认转换器
        converters.put("common", new LogicConverter());
        converters.put("mysql", new MysqlConverter());
        converters.put("duckdb", new DuckDBConverter());
        converters.put("ducklake", new DuckLakeConverter());
        converters.put("oracle", new OracleConverter());
        converters.put("postgresql", new PostgresqlConverter());
        converters.put("doris", new DorisConverter());
        converters.put("avro", new AVROConverter());
        converters.put("dm", new DmConverter());
    }

    // 注册数据库类型转换器
    public static synchronized void register(String dbType, TypeConverter converter) {
        converters.put(dbType.toLowerCase(), converter);
    }

    /**
     * 去掉类型名中的精度/长度限定，便于按基础类型查表。
     * 例如 {@code DECIMAL(18,2) -> DECIMAL}、{@code VARCHAR(64) -> VARCHAR}。
     */
    public static String stripTypeParameters(String type) {
        if (type == null) {
            return null;
        }
        int index = type.indexOf('(');
        return index > 0 ? type.substring(0, index).trim() : type;
    }

    /**
     * 解析类型名中的精度与小数位，例如 {@code DECIMAL(18,2) -> [18, 2]}、{@code NUMERIC(10) -> [10, 0]}。
     *
     * <p>部分 JDBC 驱动（如 DuckDB）只在 {@code TYPE_NAME} 中携带精度信息，而
     * {@link ColumnMeta#getPrecision()} 为空，需要从类型名兜底解析，避免生成裸 {@code DECIMAL}
     * 导致精度/小数位丢失。
     *
     * @return 长度为 2 的数组 {@code [precision, scale]}；无法解析时返回 {@code null}
     */
    public static int[] parseTypePrecisionScale(String type) {
        if (type == null) {
            return null;
        }
        int open = type.indexOf('(');
        int close = type.indexOf(')');
        if (open < 0 || close <= open) {
            return null;
        }
        String inner = type.substring(open + 1, close).trim();
        String[] parts = inner.split(",");
        try {
            int precision = Integer.parseInt(parts[0].trim());
            int scale = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            return new int[] { precision, scale };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 核心转换方法
    public static ColumnMeta convert(String sourceDB, String targetDB, ColumnMeta sourceColumnMeta) {
        //        TypeConverter sourceConverter = converters.get(sourceDB.toLowerCase());
        TypeConverter targetConverter = converters.get(targetDB.toLowerCase());

        if (targetConverter == null) {
            throw new IllegalArgumentException("未注册的数据库类型转换器");
        }
        ColumnMeta targetColumnMeta = targetConverter.toTargetColumnType(sourceColumnMeta);

        // 两级转换
        //        ColumnMeta ColumnMeta = sourceConverter.toLogicalType(sourceColumnMeta);
        return targetColumnMeta;
    }

    public static TableMeta convert(String sourceDB, String targetDB, TableMeta sourceTableMeta) {
        if (sourceDB != null && sourceDB.equalsIgnoreCase(targetDB)) return new TableMeta(sourceTableMeta.getTable(), sourceTableMeta.columns());
        List<ColumnMeta> columns = new ArrayList<>();
        for (ColumnMeta col : sourceTableMeta.columns()) {
            ColumnMeta ColumnMeta = convert(sourceDB, targetDB, col);
            columns.add(ColumnMeta);
        }
        return new TableMeta(sourceTableMeta.getTable(), columns);
    }

    // 新增DDL生成入口方法
    public static String generateTableDDL(String dbType, TableMeta table) {
        TypeConverter converter = converters.get(dbType.toLowerCase());
        return converter.generateTableDDL(table);
    }

    // 新增：根据写入模式生成相应的SQL语句
    public static String generateWriteSQL(String dbType, TableMeta tableMeta, String writeMode) {
        TypeConverter converter = converters.get(dbType.toLowerCase());
        if (converter == null) {
            throw new IllegalArgumentException("未注册的数据库类型转换器: " + dbType);
        }

        switch (writeMode.toLowerCase()) {
            case "overwrite":
            case "insert":
            case "append":
                return converter.generateInsertSQL(tableMeta);
            case "update":
                return converter.generateUpsertSQL(tableMeta);
            case "delete":
                return converter.generateDeleteSQL(tableMeta);
            default:
                throw new IllegalArgumentException("不支持的写入模式: " + writeMode);
        }
    }

    public static String generateInsertSQL(TableMeta tableMeta) {
        TypeConverter converter = converters.get(tableMeta.getDbType().toLowerCase());
        if (converter == null) {
            throw new IllegalArgumentException("未注册的数据库类型转换器: " + tableMeta.getDbType());
        }
        return converter.generateInsertSQL(tableMeta);
    }

    // 新增DDL生成入口方法
    public static String generateSchemaDDL(TableMeta table) {
        return "CREATE SCHEMA " + table.getSchema();
    }

    public static String generateDropTable(String dbType, String table) {
        return "DROP TABLE IF EXISTS " + table;
    }

    // 新增：获取该数据库支持的物理字段类型列表
    public static List<String> getSupportedTypes(String dbType) {
        TypeConverter converter = converters.get(dbType.toLowerCase());
        if (converter == null) {
            throw new IllegalArgumentException("未注册的数据库类型转换器: " + dbType);
        }
        return converter.getSupportedTypes();
    }

    // 新增：获取带引号的列名（根据数据库类型）
    private static String getQuotedColumnName(String dbType, String columnName) {
        switch (dbType.toLowerCase()) {
            case "mysql":
                return "`" + columnName + "`";
            case "postgresql":
                return "\"" + columnName + "\"";
            case "oracle":
                return "\"" + columnName.toUpperCase() + "\"";
            case "dm":
                return "\"" + columnName.toUpperCase() + "\"";
            default:
                return columnName;
        }
    }

    // 新增：获取带引号的表名（根据数据库类型）
    private static String getQuotedTableName(String dbType, String schema, String tableName) {
        String quotedTableName = getQuotedColumnName(dbType, tableName);

        if (schema != null && !schema.trim().isEmpty()) {
            String quotedSchema = getQuotedColumnName(dbType, schema);
            return quotedSchema + "." + quotedTableName;
        }

        return quotedTableName;
    }
}