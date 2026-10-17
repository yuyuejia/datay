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
 * SAP HANA 数据源 Provider，复用 {@link JdbcDataSourceProvider} 的 JDBC 标准能力。
 *
 * <p>HANA 使用 schema 组织表，表元数据以 {@code (null, schema, table)} 组合读取；
 * 连通性测试使用 {@code SELECT 1 FROM DUMMY}（HANA 没有无 FROM 的查询）。
 */
@DataSourceRegister("hana")
public class HanaDataSourceProvider extends JdbcDataSourceProvider {

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.HANA);
    }

    @Override
    public Map<String, String> dataSourceProperties() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("connectTimeout", "10000");
        props.put("communicationTimeout", "30000");
        return props;
    }

    @Override
    public String getTestQuery() {
        return "SELECT 1 FROM DUMMY";
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
