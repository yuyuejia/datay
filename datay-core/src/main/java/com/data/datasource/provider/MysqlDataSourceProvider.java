package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.datasource.provider.jdbc.JdbcMetadata;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DBType;
import com.data.metadata.TableMeta;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MySQL 数据源 Provider，复用 {@link JdbcDataSourceProvider} 的 JDBC 标准能力。
 *
 * <p>MySQL 以 catalog 作为「数据库」概念：schema 清单取自 catalog，表清单以 schema 参数作为 catalog。
 */
@DataSourceRegister("mysql")
public class MysqlDataSourceProvider extends JdbcDataSourceProvider {

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.MYSQL);
    }

    @Override
    public Map<String, String> dataSourceProperties() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("cachePrepStmts", "true");
        props.put("prepStmtCacheSize", "250");
        props.put("prepStmtCacheSqlLimit", "2048");
        props.put("useServerPrepStmts", "true");
        props.put("useLocalSessionState", "true");
        props.put("rewriteBatchedStatements", "true");
        props.put("cacheResultSetMetadata", "true");
        props.put("cacheServerConfiguration", "true");
        props.put("elideSetAutoCommits", "true");
        props.put("maintainTimeStats", "false");
        return props;
    }

    @Override
    public List<String> getSchemas(Connection conn) throws SQLException {
        return JdbcMetadata.getCatalogs(conn);
    }

    @Override
    public List<TableMeta> getTables(Connection conn, String schema, Integer limit, String search) throws SQLException {
        return JdbcMetadata.readTableList(conn, schema, null, new String[] { "TABLE" }, limit, search, false);
    }

    @Override
    public TableMeta getTableMeta(Connection conn, String schema, String table) throws SQLException {
        TableMeta tableMeta = JdbcMetadata.readTableMeta(conn, schema, null, table);
        tableMeta.setDbType(typeName());
        normalizeTinyInt1Columns(conn, schema, table, tableMeta);
        return tableMeta;
    }

    @Override
    public TableMeta getTableDetail(Connection conn, String schema, String table) throws SQLException {
        TableMeta tableMeta = JdbcMetadata.readTableMeta(conn, schema, null, table);
        tableMeta.setDbType(typeName());
        tableMeta.setComment(JdbcMetadata.getTableComment(conn, schema, null, table));
        tableMeta.setIndexes(JdbcMetadata.getTableIndexes(conn, schema, null, table));
        normalizeTinyInt1Columns(conn, schema, table, tableMeta);
        return tableMeta;
    }

    /**
     * MySQL Connector/J 默认会把 {@code TINYINT(1)} 元数据报告为 {@code BIT(1)}。
     * 数据迁移需要把它统一成 {@code TINYINT(1)}（0/1 整数语义），同时保留真正的
     * {@code BIT(1)} 列。这里借助 information_schema 的 COLUMN_TYPE 做精确还原。
     */
    private static void normalizeTinyInt1Columns(Connection conn, String schema, String table, TableMeta tableMeta) {
        if (schema == null || table == null || tableMeta == null) {
            return;
        }
        Map<String, String> columnTypes = new HashMap<>();
        try (
            PreparedStatement statement = conn.prepareStatement(
                "select COLUMN_NAME, COLUMN_TYPE from information_schema.columns where TABLE_SCHEMA = ? and TABLE_NAME = ?"
            )
        ) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    columnTypes.put(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT), rs.getString("COLUMN_TYPE"));
                }
            }
        } catch (SQLException ignored) {
            // 元数据增强失败时保持 JDBC 原始结果，不影响迁移主流程
            return;
        }
        for (ColumnMeta column : tableMeta.columns()) {
            if (column.getName() == null || !"BIT".equalsIgnoreCase(column.getType())) {
                continue;
            }
            String columnType = columnTypes.get(column.getName().toLowerCase(Locale.ROOT));
            if (columnType != null && columnType.toLowerCase(Locale.ROOT).startsWith("tinyint(1)")) {
                column.setType("TINYINT");
                column.setLength(1);
            }
        }
    }
}
