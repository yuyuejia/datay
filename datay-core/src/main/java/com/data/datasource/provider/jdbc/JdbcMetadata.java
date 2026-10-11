package com.data.datasource.provider.jdbc;

import com.data.metadata.ColumnMeta;
import com.data.metadata.IndexMeta;
import com.data.metadata.TableMeta;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 与具体数据库类型无关的 JDBC 元数据读取原语。
 *
 * <p>从原 {@code DBUtils} 中抽取的通用实现，不含任何 {@code mysql/oracle/duckdb...} 类型判断；
 * 各数据源 Provider 负责按自身特性选择 catalog/schema/table 组合或表类型，再调用本类完成读取。
 */
public final class JdbcMetadata {

    private JdbcMetadata() {}

    public static String escapeSqlString(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    /**
     * 读取 catalog 列表。
     */
    public static List<String> getCatalogs(Connection conn) throws SQLException {
        List<String> catalogs = new ArrayList<>();
        try (ResultSet rs = conn.getMetaData().getCatalogs()) {
            while (rs.next()) {
                catalogs.add(rs.getString("TABLE_CAT"));
            }
        }
        return catalogs;
    }

    /**
     * 读取 schema 列表（标准 {@link DatabaseMetaData#getSchemas()}）。
     */
    public static List<String> readSchemas(Connection conn) throws SQLException {
        List<String> schemas = new ArrayList<>();
        try (ResultSet rs = conn.getMetaData().getSchemas()) {
            while (rs.next()) {
                schemas.add(rs.getString("TABLE_SCHEM"));
            }
        }
        return schemas;
    }

    /**
     * 按指定 catalog/schema 与表类型读取表清单。
     *
     * @param populateCatalog 是否用结果中的 {@code TABLE_CAT} 回填 {@link TableMeta#setCatalog}；
     *                        仅对 DuckDB 这类把 catalog 作为逻辑库的驱动需要
     */
    public static List<TableMeta> readTableList(
        Connection conn,
        String catalog,
        String schema,
        String[] tableTypes,
        Integer limit,
        String search,
        boolean populateCatalog
    ) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        List<TableMeta> tables = new ArrayList<>();
        String tableNamePattern = (search != null && !search.isEmpty()) ? "%" + search + "%" : null;

        try (ResultSet rs = metaData.getTables(catalog, schema, tableNamePattern, tableTypes)) {
            while (rs.next()) {
                if (limit != null && tables.size() >= limit) {
                    break;
                }
                TableMeta tableMeta = new TableMeta(rs.getString("TABLE_NAME"));
                String tableComment = rs.getString("REMARKS");
                if (tableComment != null) {
                    tableMeta.setComment(tableComment);
                }
                if (populateCatalog) {
                    String tableCatalog = rs.getString("TABLE_CAT");
                    if (tableCatalog != null && !tableCatalog.isEmpty()) {
                        tableMeta.setCatalog(tableCatalog);
                    }
                }
                String tableSchema = rs.getString("TABLE_SCHEM");
                if (tableSchema != null && !tableSchema.isEmpty()) {
                    tableMeta.setSchema(tableSchema);
                }
                tables.add(tableMeta);
            }
        }
        return tables;
    }

    /**
     * 读取表结构（字段、主键、注释、默认值等）。
     */
    public static TableMeta readTableMeta(Connection conn, String catalog, String schema, String table) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        List<ColumnMeta> columns = new ArrayList<>();

        Set<String> primaryKeys = new HashSet<>();
        try (ResultSet pkRs = metaData.getPrimaryKeys(catalog, schema, table)) {
            while (pkRs.next()) {
                primaryKeys.add(pkRs.getString("COLUMN_NAME"));
            }
        }

        try (ResultSet rs = metaData.getColumns(catalog, schema, table, null)) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                String type = rs.getString("TYPE_NAME");
                int size = rs.getInt("COLUMN_SIZE");
                // 多数驱动（DM/MySQL/Oracle/PG 等）的 IS_NULLABLE 为字符串 "YES"/"NO"
                String nullableFlag = rs.getString("IS_NULLABLE");
                boolean isNullable =
                    nullableFlag == null ||
                    !("NO".equalsIgnoreCase(nullableFlag) || "0".equals(nullableFlag) || "false".equalsIgnoreCase(nullableFlag));
                String remarks = rs.getString("REMARKS");
                int decimalDigits = rs.getInt("DECIMAL_DIGITS");
                String defaultValue = rs.getString("COLUMN_DEF");

                int precision = -1;
                int scale = -1;
                if (
                    "NUMBER".equalsIgnoreCase(type) ||
                    "DECIMAL".startsWith(type.toUpperCase()) ||
                    "NUMERIC".equalsIgnoreCase(type) ||
                    "FLOAT".equalsIgnoreCase(type) ||
                    "DOUBLE".equalsIgnoreCase(type)
                ) {
                    precision = size;
                    scale = decimalDigits;
                }

                ColumnMeta column = new ColumnMeta(name, type, size, precision, scale);
                column.setNullable(isNullable);
                column.setComment(remarks);
                column.setDefaultValue(defaultValue);
                column.setPrimaryKey(primaryKeys.contains(name));
                columns.add(column);
            }
        }
        if (columns.isEmpty()) {
            throw new SQLException("Table " + table + " not found.");
        }
        TableMeta tableMeta = new TableMeta(table, columns);
        if (schema != null) {
            tableMeta.setSchema(schema);
        } else if (catalog != null) {
            tableMeta.setSchema(catalog);
        }
        return tableMeta;
    }

    /**
     * 读取表注释。
     */
    public static String getTableComment(Connection conn, String catalog, String schema, String table) {
        try (ResultSet rs = conn.getMetaData().getTables(catalog, schema, table, new String[] { "TABLE", "VIEW" })) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (tableName != null && tableName.equalsIgnoreCase(table)) {
                    return rs.getString("REMARKS");
                }
            }
        } catch (SQLException e) {
            // ignore remarks failure
        }
        return null;
    }

    /**
     * 读取表索引信息。
     */
    public static List<IndexMeta> getTableIndexes(Connection conn, String catalog, String schema, String table) throws SQLException {
        Map<String, IndexMeta> indexMap = new LinkedHashMap<>();
        try (ResultSet rs = conn.getMetaData().getIndexInfo(catalog, schema, table, false, false)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                if (indexName == null) {
                    continue;
                }
                String columnName = rs.getString("COLUMN_NAME");
                boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                short indexType = rs.getShort("TYPE");
                IndexMeta indexMeta = indexMap.get(indexName);
                if (indexMeta == null) {
                    indexMeta = new IndexMeta(indexName);
                    indexMeta.setUnique(!nonUnique);
                    indexMeta.setType(resolveIndexType(indexType));
                    indexMap.put(indexName, indexMeta);
                }
                indexMeta.addColumn(columnName);
            }
        } catch (SQLException e) {
            // 部分驱动不支持索引元数据，忽略并返回已有结果
        }
        return new ArrayList<>(indexMap.values());
    }

    private static String resolveIndexType(short indexType) {
        switch (indexType) {
            case DatabaseMetaData.tableIndexClustered:
                return "clustered";
            case DatabaseMetaData.tableIndexHashed:
                return "hashed";
            case DatabaseMetaData.tableIndexStatistic:
                return "statistic";
            case DatabaseMetaData.tableIndexOther:
            default:
                return "other";
        }
    }
}
