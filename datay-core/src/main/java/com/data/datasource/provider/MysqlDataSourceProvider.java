package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.datasource.provider.jdbc.JdbcMetadata;
import com.data.metadata.DBType;
import com.data.metadata.TableMeta;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
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
}
