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
        converters.put("mysql", new MysqlConverter());
        converters.put("duckdb", new DuckDBConverter());
        converters.put("ducklake", new DuckLakeConverter());
        converters.put("oracle", new OracleConverter());
        converters.put("postgresql", new PostgresqlConverter());
        converters.put("doris", new DorisConverter());
        converters.put("avro", new AVROConverter());
    }

    // 注册数据库类型转换器
    public static synchronized void register(String dbType, TypeConverter converter) {
        converters.put(dbType.toLowerCase(), converter);
    }

    // 核心转换方法
    public static ColumnMeta convert(String sourceDB, String targetDB, ColumnMeta sourceColumnMeta) {
        //        TypeConverter sourceConverter = converters.get(sourceDB.toLowerCase());
        TypeConverter targetConverter = converters.get(targetDB.toLowerCase());

        if (targetConverter == null) {
            throw new IllegalArgumentException("未注册的数据库类型转换器");
        }
        ColumnMeta targetColumnMeta = targetConverter.toPhysicalType(sourceColumnMeta);

        // 两级转换
        //        ColumnMeta ColumnMeta = sourceConverter.toLogicalType(sourceColumnMeta);
        return targetColumnMeta;
    }

    public static TableMeta convert(String sourceDB, String targetDB, TableMeta sourceTableMeta) {
        if (sourceDB.equals(targetDB)) return new TableMeta(sourceTableMeta.getTable(), sourceTableMeta.columns());
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

    // 新增：获取带引号的列名（根据数据库类型）
    private static String getQuotedColumnName(String dbType, String columnName) {
        switch (dbType.toLowerCase()) {
            case "mysql":
                return "`" + columnName + "`";
            case "postgresql":
                return "\"" + columnName + "\"";
            case "oracle":
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
