package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.datasource.provider.jdbc.JdbcMetadata;
import com.data.metadata.DBType;
import com.data.metadata.TableMeta;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * SQLite 数据源 Provider。
 *
 * <p>SQLite 为嵌入式文件数据库：URL 形如 {@code jdbc:sqlite:/path/to.db}（或 {@code jdbc:sqlite::memory:}），
 * 不使用账号密码。SQLite 无 schema 概念（统一为 {@code main}），表元数据以
 * {@code (null, null, table)} 组合读取。
 */
@DataSourceRegister("sqlite")
public class SqliteDataSourceProvider extends JdbcDataSourceProvider {

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.SQLITE);
    }

    @Override
    public boolean requiresCredentials() {
        return false;
    }

    @Override
    public List<String> getSchemas(Connection conn) {
        return List.of("main");
    }

    @Override
    public List<TableMeta> getTables(Connection conn, String schema, Integer limit, String search) throws SQLException {
        return JdbcMetadata.readTableList(conn, null, null, new String[] { "TABLE" }, limit, search, false);
    }

    @Override
    public TableMeta getTableMeta(Connection conn, String schema, String table) throws SQLException {
        TableMeta tableMeta = JdbcMetadata.readTableMeta(conn, null, null, table);
        tableMeta.setDbType(typeName());
        return tableMeta;
    }

    @Override
    public TableMeta getTableDetail(Connection conn, String schema, String table) throws SQLException {
        TableMeta tableMeta = JdbcMetadata.readTableMeta(conn, null, null, table);
        tableMeta.setDbType(typeName());
        tableMeta.setComment(JdbcMetadata.getTableComment(conn, null, null, table));
        tableMeta.setIndexes(JdbcMetadata.getTableIndexes(conn, null, null, table));
        return tableMeta;
    }

    @Override
    public boolean tableExists(Connection conn, String schema, String table) throws SQLException {
        return tableExists(conn, null, null, table);
    }
}
