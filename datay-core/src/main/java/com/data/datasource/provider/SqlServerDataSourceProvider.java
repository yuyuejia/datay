package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.datasource.provider.jdbc.JdbcMetadata;
import com.data.job.DatasourceInfo;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DBType;
import com.data.metadata.TableMeta;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Microsoft SQL Server 数据源 Provider，复用 {@link JdbcDataSourceProvider} 的 JDBC 标准能力。
 *
 * <p>SQL Server 的 catalog 是「数据库」、schema 是 {@code dbo} 等，二者不能混用：
 * JDBC 元数据接口必须按 {@code (null, schema, table)} 读取，否则会把 schema 当成数据库名，
 * 报 {@code Database 'dbo' does not exist}。因此这里覆写默认的元数据读取方法。
 *
 * <p>同时承载 SQL Server 方言：标识符使用方括号引用，以及
 * {@code DATE/TIME/TIMESTAMP/DATETIMEOFFSET} 的驱动类型读取。
 */
@DataSourceRegister("sqlserver")
public class SqlServerDataSourceProvider extends JdbcDataSourceProvider {

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.SQLSERVER);
    }

    @Override
    public TableMeta getTableMeta(Connection conn, String schema, String table) throws SQLException {
        TableMeta tableMeta = JdbcMetadata.readTableMeta(conn, null, schema, table);
        tableMeta.setDbType(typeName());
        return tableMeta;
    }

    @Override
    public TableMeta getTableDetail(Connection conn, String schema, String table) throws SQLException {
        TableMeta tableMeta = JdbcMetadata.readTableMeta(conn, null, schema, table);
        tableMeta.setDbType(typeName());
        tableMeta.setComment(JdbcMetadata.getTableComment(conn, null, schema, table));
        tableMeta.setIndexes(JdbcMetadata.getTableIndexes(conn, null, schema, table));
        return tableMeta;
    }

    @Override
    public boolean tableExists(Connection conn, String schema, String table) throws SQLException {
        return tableExists(conn, null, schema, table);
    }

    /**
     * SQL Server 标识符使用方括号引用，避免保留字、空格或特殊字符导致 DDL/DML 失败。
     */
    @Override
    public String qualifiedTableName(DatasourceInfo info, String schema, String table) {
        String quotedTable = "[" + (table == null ? "" : table.replace("]", "]]")) + "]";
        if (schema == null || schema.isBlank()) {
            return quotedTable;
        }
        return "[" + schema.replace("]", "]]") + "]." + quotedTable;
    }

    /**
     * SQL Server 的 DATE/TIME/TIMESTAMP 是本地墙上时间，优先读取为 {@code java.time} 类型，
     * 避免按 UTC 还原导致时区偏移；{@code DATETIMEOFFSET} 在旧驱动中返回专有类型，
     * 统一读取为 {@link OffsetDateTime}。
     */
    @Override
    public Object readSourceValue(ResultSet rs, int index, int sqlType, String typeName, ColumnMeta column)
        throws SQLException {
        if (sqlType == Types.DATE) {
            Object value = readTimeValue(rs, index, LocalDate.class);
            if (value != null) {
                return value;
            }
        }
        if (sqlType == Types.TIME) {
            Object value = readTimeValue(rs, index, LocalTime.class);
            if (value != null) {
                return value;
            }
        }
        if (sqlType == Types.TIMESTAMP) {
            Object value = readTimeValue(rs, index, LocalDateTime.class);
            if (value != null) {
                return value;
            }
        }
        if (sqlType == Types.TIMESTAMP_WITH_TIMEZONE || "datetimeoffset".equalsIgnoreCase(typeName)) {
            Object value = readTimeValue(rs, index, OffsetDateTime.class);
            if (value != null) {
                return value;
            }
        }
        return rs.getObject(index);
    }

    private <T> Object readTimeValue(ResultSet rs, int index, Class<T> type) throws SQLException {
        try {
            return rs.getObject(index, type);
        } catch (SQLException | AbstractMethodError | UnsupportedOperationException ignored) {
            // 旧版驱动不支持 java.time 时退回通用读取
            return null;
        }
    }
}
