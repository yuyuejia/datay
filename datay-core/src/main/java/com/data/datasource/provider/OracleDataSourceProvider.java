package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.datasource.provider.jdbc.JdbcMetadata;
import com.data.metadata.DBType;
import com.data.metadata.TableMeta;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
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
        return props;
    }

    @Override
    public String getTestQuery() {
        return "SELECT 1 FROM DUAL";
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
