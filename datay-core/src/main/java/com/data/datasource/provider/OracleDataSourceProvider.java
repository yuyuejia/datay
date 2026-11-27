package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.datasource.provider.jdbc.JdbcMetadata;
import com.data.metadata.DBType;
import com.data.metadata.TableMeta;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Oracle 数据源 Provider，复用 {@link JdbcDataSourceProvider} 的 JDBC 标准能力。
 *
 * <p>Oracle 的 schema 即用户，表元数据以 {@code (null, schema, table)} 组合读取；
 * 连通性测试使用 {@code SELECT 1 FROM DUAL}。
 */
@DataSourceRegister("oracle")
public class OracleDataSourceProvider extends JdbcDataSourceProvider {

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.ORACLE);
    }

    @Override
    public Map<String, String> dataSourceProperties() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("oracle.jdbc.ReadTimeout", "30000");
        props.put("oracle.net.CONNECT_TIMEOUT", "10000");
        // Oracle JDBC 默认不返回 REMARKS，开启后 DatabaseMetaData 才能读取表/字段注释
        props.put("oracle.jdbc.remarksReporting", "true");
        return props;
    }

    @Override
    public String getTestQuery() {
        return "SELECT 1 FROM DUAL";
    }

    /**
     * Oracle 的 {@code TIMESTAMP WITH [LOCAL] TIME ZONE} 在旧驱动下返回
     * {@code oracle.sql.TIMESTAMPTZ}/{@code TIMESTAMPLTZ} 专有类型，默认 toString 是对象地址。
     * 这里统一读取为 {@link OffsetDateTime}（或退回 {@link java.sql.Timestamp}），让查询预览可读。
     */
    @Override
    public Object readQueryValue(ResultSet rs, int index, int sqlType, String typeName) throws SQLException {
        if (isTimestampWithTimeZone(sqlType, typeName)) {
            try {
                return rs.getObject(index, OffsetDateTime.class);
            } catch (SQLException | AbstractMethodError | UnsupportedOperationException ignored) {
                // 旧版驱动不支持 java.time，退回标准时间读取
            }
            try {
                return rs.getTimestamp(index);
            } catch (SQLException ignored) {
                // 继续走通用读取
            }
        }
        return super.readQueryValue(rs, index, sqlType, typeName);
    }

    private static boolean isTimestampWithTimeZone(int sqlType, String typeName) {
        if (sqlType == Types.TIMESTAMP_WITH_TIMEZONE) {
            return true;
        }
        if (typeName == null) {
            return false;
        }
        String normalized = typeName.toUpperCase(Locale.ROOT);
        return normalized.contains("TIMESTAMP WITH TIME ZONE") || normalized.contains("TIMESTAMP WITH LOCAL TIME ZONE");
    }

    /**
     * Oracle 未加引号的标识符会折叠为大写，而 JDBC 元数据查询是大小写敏感的。
     * 表搜索框输入小写关键字时，先按原样查询，再补一次大写查询并合并结果，
     * 兼容默认大写对象与显式小写引号对象。
     */
    @Override
    public List<TableMeta> getTables(Connection conn, String schema, Integer limit, String search) throws SQLException {
        if (search == null || search.isEmpty()) {
            return super.getTables(conn, schema, limit, search);
        }
        Map<String, TableMeta> merged = new LinkedHashMap<>();
        for (TableMeta table : super.getTables(conn, schema, limit, search)) {
            merged.putIfAbsent(tableKey(table), table);
        }
        String upperSearch = search.toUpperCase(Locale.ROOT);
        if (!upperSearch.equals(search)) {
            for (TableMeta table : super.getTables(conn, schema, limit, upperSearch)) {
                merged.putIfAbsent(tableKey(table), table);
            }
        }
        List<TableMeta> tables = new ArrayList<>(merged.values());
        if (limit != null && limit > 0 && tables.size() > limit) {
            return new ArrayList<>(tables.subList(0, limit));
        }
        return tables;
    }

    private static String tableKey(TableMeta table) {
        return (table.getSchema() == null ? "" : table.getSchema()) + "." + table.getTable();
    }

    /**
     * Oracle 11g 不支持 {@code FETCH FIRST} / {@code LIMIT}，使用 {@code ROWNUM} 包装以兼容各版本。
     */
    @Override
    public String applyRowLimit(String sql, int limit) {
        return wrapWithRownumRowLimit(sql, limit);
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
}
