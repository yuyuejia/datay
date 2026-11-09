package com.data.metadata;

import com.data.metadata.impl.*;
import com.data.metadata.util.DBUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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
        converters.put("tidb", new TidbConverter());
        converters.put("duckdb", new DuckDBConverter());
        converters.put("ducklake", new DuckLakeConverter());
        converters.put("oracle", new OracleConverter());
        converters.put("postgresql", new PostgresqlConverter());
        converters.put("doris", new DorisConverter());
        converters.put("avro", new AVROConverter());
        converters.put("dm", new DmConverter());
        converters.put("hana", new HanaConverter());
        converters.put("sqlserver", new SqlServerConverter());
        // 协议兼容别名：MariaDB/StarRocks/OceanBase/PolarDB 复用 MySQL 方言；GaussDB 复用 PostgreSQL 方言
        converters.put("mariadb", converters.get("mysql"));
        converters.put("starrocks", converters.get("mysql"));
        converters.put("oceanbase", converters.get("mysql"));
        converters.put("polardb", converters.get("mysql"));
        converters.put("gaussdb", converters.get("postgresql"));
        converters.put("sqlite", new SqliteConverter());
    }

    // 注册数据库类型转换器
    public static synchronized void register(String dbType, TypeConverter converter) {
        converters.put(dbType.toLowerCase(), converter);
    }

    /**
     * 按数据库类型获取已注册的方言转换器，不存在时返回 {@code null}。
     */
    public static TypeConverter getConverter(String dbType) {
        return dbType == null ? null : converters.get(dbType.toLowerCase());
    }

    /**
     * 通用的逻辑类型转换器（{@code common}），用于无专属方言的场景。
     */
    public static TypeConverter getCommonConverter() {
        return converters.get("common");
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
        TypeConverter converter = dbType == null ? null : converters.get(dbType.toLowerCase());
        if (converter == null || isGenericConverter(dbType)) {
            // 通用 JDBC 等未登记方言的数据源没有专用写入 SQL 生成器，
            // 退化为标准 SQL，至少保证 append/insert 以及有主键时的 update/delete 可用。
            return generateGenericWriteSQL(tableMeta, writeMode);
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

    /**
     * 是否为通用逻辑转换器（其 DDL/写 SQL 方法不生成具体语句）。
     */
    private static boolean isGenericConverter(String dbType) {
        return dbType != null && LogicConverter.LOGIC_CONVERTER_KEY.equalsIgnoreCase(dbType);
    }

    /**
     * 为未登记方言的数据源生成标准 SQL。
     */
    private static String generateGenericWriteSQL(TableMeta tableMeta, String writeMode) {
        switch (writeMode.toLowerCase()) {
            case "overwrite":
            case "insert":
            case "append":
                return buildGenericInsertSQL(tableMeta);
            case "update":
                return buildGenericUpdateSQL(tableMeta);
            case "delete":
                return buildGenericDeleteSQL(tableMeta);
            default:
                throw new IllegalArgumentException("不支持的写入模式: " + writeMode);
        }
    }

    private static String buildGenericInsertSQL(TableMeta tableMeta) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(qualifiedTableName(tableMeta)).append(" (");
        List<ColumnMeta> columns = tableMeta.columns();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(columns.get(i).getName());
        }
        sql.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        return sql.append(")").toString();
    }

    private static String buildGenericUpdateSQL(TableMeta tableMeta) {
        List<ColumnMeta> primaryKeys = primaryKeyColumns(tableMeta);
        if (primaryKeys.isEmpty()) {
            throw new IllegalArgumentException("UPDATE 模式需要目标表定义主键: " + tableMeta.getTable());
        }
        List<ColumnMeta> updateColumns = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            if (!primaryKeys.contains(column)) {
                updateColumns.add(column);
            }
        }
        if (updateColumns.isEmpty()) {
            throw new IllegalArgumentException("UPDATE 模式没有可更新的非主键字段: " + tableMeta.getTable());
        }

        StringBuilder sql = new StringBuilder("UPDATE ").append(qualifiedTableName(tableMeta)).append(" SET ");
        for (int i = 0; i < updateColumns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(updateColumns.get(i).getName()).append(" = ?");
        }
        appendWherePrimaryKeys(sql, primaryKeys);
        return sql.toString();
    }

    private static String buildGenericDeleteSQL(TableMeta tableMeta) {
        List<ColumnMeta> primaryKeys = primaryKeyColumns(tableMeta);
        if (primaryKeys.isEmpty()) {
            throw new IllegalArgumentException("DELETE 模式需要目标表定义主键: " + tableMeta.getTable());
        }
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(qualifiedTableName(tableMeta));
        appendWherePrimaryKeys(sql, primaryKeys);
        return sql.toString();
    }

    private static List<ColumnMeta> primaryKeyColumns(TableMeta tableMeta) {
        List<ColumnMeta> primaryKeys = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            if (column.isPrimaryKey()) {
                primaryKeys.add(column);
            }
        }
        return primaryKeys;
    }

    private static void appendWherePrimaryKeys(StringBuilder sql, List<ColumnMeta> primaryKeys) {
        sql.append(" WHERE ");
        for (int i = 0; i < primaryKeys.size(); i++) {
            if (i > 0) {
                sql.append(" AND ");
            }
            sql.append(primaryKeys.get(i).getName()).append(" = ?");
        }
    }

    private static String qualifiedTableName(TableMeta tableMeta) {
        String schema = tableMeta.getSchema();
        if (schema == null || schema.isBlank()) {
            return tableMeta.getTable();
        }
        return schema + "." + tableMeta.getTable();
    }

    /**
     * 按目标数据库方言绑定 SQL 参数。
     *
     * <p>方言未注册时退化为通用值归一化，避免影响非标准数据源。</p>
     */
    public static void bindParameter(String dbType, PreparedStatement statement, int index, ColumnMeta column, Object value)
        throws SQLException {
        TypeConverter converter = dbType == null ? null : converters.get(dbType.toLowerCase());
        if (converter == null) {
            Object normalized = DBUtils.normalizeParameter(column.getType(), value);
            // 通用 JDBC 驱动通常不识别 LocalDateTime 的 ISO 形式（如 2026-11-09T11:00），
            // 统一转成 java.sql.Timestamp，交由驱动的 setTimestamp 正确格式化。
            if (normalized instanceof LocalDateTime && isTimestampColumn(column.getType())) {
                normalized = Timestamp.valueOf((LocalDateTime) normalized);
            }
            // 字符串使用标准 setString：TDengine WS 驱动的 setObject(String) 会走 NCHAR 分支，
            // 后续按 byte[] 处理导致 ClassCastException。
            if (normalized instanceof String) {
                statement.setString(index, (String) normalized);
                return;
            }
            statement.setObject(index, normalized);
            return;
        }
        converter.bindParameter(statement, index, column, value);
    }

    /**
     * 列类型是否为时间戳/日期时间。
     */
    private static boolean isTimestampColumn(String columnType) {
        if (columnType == null) {
            return false;
        }
        String type = columnType.toUpperCase().trim();
        return type.equals("DATETIME") || type.equals("TIMESTAMP") || type.endsWith("TIMESTAMP") || type.startsWith("DATETIME");
    }

    /**
     * 补充源表元数据（由对应数据库方言实现）。
     */
    public static void enrichSourceTableMeta(String dbType, Connection connection, String schema, String table,
        TableMeta tableMeta) throws SQLException {
        TypeConverter converter = dbType == null ? null : converters.get(dbType.toLowerCase());
        if (converter != null) {
            converter.enrichSourceTableMeta(connection, schema, table, tableMeta);
        }
    }

    /**
     * 按目标数据库方言翻译 DDL。
     */
    public static String translateDDL(String dbType, String sql, String sourceSchema, String targetSchema) {
        TypeConverter converter = dbType == null ? null : converters.get(dbType.toLowerCase());
        return converter == null ? sql : converter.translateDDL(sql, sourceSchema, targetSchema);
    }

    /**
     * 按目标数据库方言准备 DDL 执行连接。
     */
    public static void prepareDDLConnection(String dbType, Connection connection, String targetSchema) throws SQLException {
        TypeConverter converter = dbType == null ? null : converters.get(dbType.toLowerCase());
        if (converter != null) {
            converter.prepareDDLConnection(connection, targetSchema);
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