package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.datasource.provider.jdbc.JdbcMetadata;
import com.data.job.DatasourceInfo;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DBType;
import com.data.metadata.TableMeta;
import com.data.metadata.util.ConnectionPoolManager;
import com.data.metadata.util.DBUtils;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DuckDB 数据源 Provider。
 *
 * <p>承载 DuckDB 特有的行为：本地 catalog/schema 解析、{@code TABLE} 与 {@code BASE TABLE} 两种表类型，
 * 以及 Quack 远程协议（挂载 {@code quack} catalog、通过 {@code quack.query} 读取远端元数据）。
 * 同时作为 {@link DuckLakeDataSourceProvider} 的基类复用其元数据能力。
 */
@DataSourceRegister("duckdb")
public class DuckDbDataSourceProvider extends JdbcDataSourceProvider {

    public static final String DUCKLAKE_CATALOG = "ducklake";
    public static final String QUACK_CATALOG = "quack";

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.DUCKDB);
    }

    @Override
    protected List<String> urlPrefixes() {
        // 本地 DuckDB 与 Quack 远程协议共用 DuckDB 驱动
        return List.of("jdbc:duckdb:", "quack:");
    }

    @Override
    public String resolveJdbcUrl(String url) {
        // Quack 远程连接底层仍使用 DuckDB 驱动，实际的 ATTACH 在获取连接后完成
        if (url != null && url.startsWith("quack:")) {
            return "jdbc:duckdb:";
        }
        return url;
    }

    @Override
    public boolean requiresCredentials() {
        // DuckDB / Quack / DuckLake 本地连接不使用账号密码，
        // 且 DuckDB 会拒绝以不同 user 配置打开同一数据库文件
        return false;
    }

    @Override
    public Map<String, String> dataSourceProperties() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("jdbc_stream_results", "true");
        return props;
    }

    @Override
    public Connection openConnection(DatasourceInfo info) throws SQLException {
        if (info.getUrl() != null && info.getUrl().startsWith("quack:")) {
            Connection conn = ConnectionPoolManager.getConnection(info);
            setupQuackConnection(conn, info);
            return conn;
        }
        return super.openConnection(info);
    }

    @Override
    public boolean isQuackConnection(Connection conn) {
        try (ResultSet rs = conn.getMetaData().getCatalogs()) {
            while (rs.next()) {
                if (QUACK_CATALOG.equals(rs.getString("TABLE_CAT"))) {
                    return true;
                }
            }
        } catch (SQLException ignored) {
            // ignore
        }
        return false;
    }

    @Override
    public String wrapQuery(String sql) {
        return DBUtils.wrapQuackQuery(sql);
    }

    @Override
    public List<String> getSchemas(Connection conn) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        return new ArrayList<>(getDistinctSchemas(metaData, resolveDuckCatalog(metaData)));
    }

    @Override
    public List<TableMeta> getTables(Connection conn, String schema, Integer limit, String search) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        String catalog = resolveDuckCatalog(metaData);
        // Quack 远程目录不会向本地 JDBC 元数据暴露表信息，改由 quack.query 在远端查询
        if (QUACK_CATALOG.equals(catalog)) {
            return getQuackTableList(conn, schema, limit, search);
        }
        return JdbcMetadata.readTableList(conn, catalog, schema, new String[] { "TABLE", "BASE TABLE" }, limit, search, true);
    }

    @Override
    public TableMeta getTableMeta(Connection conn, String schema, String table) throws SQLException {
        String catalog = resolveDuckCatalog(conn.getMetaData());
        TableMeta tableMeta = QUACK_CATALOG.equals(catalog)
            ? getQuackTableMetaData(conn, schema, table)
            : JdbcMetadata.readTableMeta(conn, catalog, schema, table);
        tableMeta.setDbType(typeName());
        return tableMeta;
    }

    @Override
    public TableMeta getTableDetail(Connection conn, String schema, String table) throws SQLException {
        String catalog = resolveDuckCatalog(conn.getMetaData());
        if (QUACK_CATALOG.equals(catalog)) {
            TableMeta tableMeta = getQuackTableMetaData(conn, schema, table);
            tableMeta.setDbType(typeName());
            return tableMeta;
        }
        TableMeta tableMeta = JdbcMetadata.readTableMeta(conn, catalog, schema, table);
        tableMeta.setDbType(typeName());
        tableMeta.setCatalog(catalog);
        tableMeta.setComment(JdbcMetadata.getTableComment(conn, catalog, schema, table));
        tableMeta.setIndexes(JdbcMetadata.getTableIndexes(conn, catalog, schema, table));
        return tableMeta;
    }

    @Override
    public boolean isDuckLakeConnection(Connection conn) {
        try (ResultSet rs = conn.getMetaData().getCatalogs()) {
            while (rs.next()) {
                if (DUCKLAKE_CATALOG.equals(rs.getString("TABLE_CAT"))) {
                    return true;
                }
            }
        } catch (SQLException ignored) {
            // ignore
        }
        return false;
    }

    @Override
    public boolean tableExists(Connection conn, String schema, String table) throws SQLException {
        if (isQuackConnection(conn)) {
            return quackTableExists(conn, schema, table);
        }
        if (isDuckLakeConnection(conn)) {
            return tableExists(conn, DUCKLAKE_CATALOG, schema, table);
        }
        return tableExists(conn, null, schema, table);
    }

    // ------------------------------------------------------------------ DuckDB 内部能力

    /**
     * 解析当前 DuckDB 连接的目标 catalog：优先已挂载的 ducklake / quack，否则取连接默认 catalog。
     */
    protected static String resolveDuckCatalog(DatabaseMetaData metaData) throws SQLException {
        try (ResultSet rs = metaData.getCatalogs()) {
            while (rs.next()) {
                String cat = rs.getString("TABLE_CAT");
                if (DUCKLAKE_CATALOG.equals(cat)) {
                    return DUCKLAKE_CATALOG;
                }
                if (QUACK_CATALOG.equals(cat)) {
                    return QUACK_CATALOG;
                }
            }
        }
        try {
            return metaData.getConnection().getCatalog();
        } catch (Exception e) {
            return "memory";
        }
    }

    private static Set<String> getDistinctSchemas(DatabaseMetaData metaData, String catalog) throws SQLException {
        Set<String> schemas = new LinkedHashSet<>();
        try (ResultSet rs = metaData.getSchemas(catalog, null)) {
            while (rs.next()) {
                schemas.add(rs.getString("TABLE_SCHEM"));
            }
        }
        return schemas;
    }

    private static List<TableMeta> getQuackTableList(Connection conn, String schema, Integer limit, String search) throws SQLException {
        StringBuilder remoteSql = new StringBuilder(
            "SELECT table_schema, table_name FROM information_schema.tables WHERE table_type = 'BASE TABLE'"
        );
        if (schema != null && !schema.isEmpty()) {
            remoteSql.append(" AND table_schema = '").append(JdbcMetadata.escapeSqlString(schema)).append("'");
        }
        if (search != null && !search.isEmpty()) {
            remoteSql.append(" AND table_name ILIKE '%").append(JdbcMetadata.escapeSqlString(search)).append("%'");
        }
        remoteSql.append(" ORDER BY table_schema, table_name");
        if (limit != null) {
            remoteSql.append(" LIMIT ").append(limit);
        }

        List<TableMeta> tables = new ArrayList<>();
        try (
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT table_schema, table_name FROM quack.query($q$" + remoteSql + "$q$)")
        ) {
            while (rs.next()) {
                TableMeta tableMeta = new TableMeta(rs.getString("table_name"));
                tableMeta.setSchema(rs.getString("table_schema"));
                tableMeta.setCatalog(QUACK_CATALOG);
                tables.add(tableMeta);
            }
        }
        return tables;
    }

    private static TableMeta getQuackTableMetaData(Connection conn, String schema, String table) throws SQLException {
        StringBuilder remoteSql = new StringBuilder(
            "SELECT column_name, data_type, is_nullable, column_default, numeric_precision, numeric_scale, character_maximum_length " +
            "FROM information_schema.columns WHERE table_name = '" + JdbcMetadata.escapeSqlString(table) + "'"
        );
        if (schema != null && !schema.isEmpty()) {
            remoteSql.append(" AND table_schema = '").append(JdbcMetadata.escapeSqlString(schema)).append("'");
        }
        remoteSql.append(" ORDER BY ordinal_position");

        List<ColumnMeta> columns = new ArrayList<>();
        try (
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM quack.query($q$" + remoteSql + "$q$)")
        ) {
            while (rs.next()) {
                String name = rs.getString("column_name");
                String type = rs.getString("data_type");
                boolean nullable = !"NO".equalsIgnoreCase(rs.getString("is_nullable"));
                String defaultValue = rs.getString("column_default");

                int length = rs.getInt("character_maximum_length");
                if (rs.wasNull()) {
                    length = -1;
                }
                int precision = rs.getInt("numeric_precision");
                if (rs.wasNull()) {
                    precision = -1;
                }
                int scale = rs.getInt("numeric_scale");
                if (rs.wasNull()) {
                    scale = -1;
                }

                ColumnMeta column = new ColumnMeta(name, type, length, precision, scale);
                column.setNullable(nullable);
                column.setDefaultValue(defaultValue);
                columns.add(column);
            }
        }
        if (columns.isEmpty()) {
            throw new SQLException("Table " + table + " not found.");
        }
        TableMeta tableMeta = new TableMeta(table, columns);
        tableMeta.setDbType(DBType.DUCKDB.toString());
        tableMeta.setCatalog(QUACK_CATALOG);
        if (schema != null) {
            tableMeta.setSchema(schema);
        }
        return tableMeta;
    }

    private static void setupQuackConnection(Connection conn, DatasourceInfo datasourceInfo) throws SQLException {
        String quackUri = datasourceInfo.getUrl();
        Map<String, String> extraParams = datasourceInfo.getExtraParams();

        Statement stmt = conn.createStatement();
        try { stmt.execute("INSTALL quack"); } catch (Exception ignored) {}
        try { stmt.execute("LOAD quack"); } catch (Exception ignored) {}
        try { stmt.close(); } catch (Exception ignored) {}

        stmt = conn.createStatement();
        try { stmt.execute(String.format("DETACH %s;", QUACK_CATALOG)); } catch (Exception ignored) {}
        try { stmt.close(); } catch (Exception ignored) {}

        StringBuilder attachSql = new StringBuilder(
            String.format("ATTACH '%s' AS %s", JdbcMetadata.escapeSqlString(quackUri), QUACK_CATALOG)
        );
        List<String> options = new ArrayList<>();
        if (extraParams != null) {
            String token = extraParams.get("quack.token");
            if (token != null && !token.trim().isEmpty()) {
                options.add(String.format("TOKEN '%s'", JdbcMetadata.escapeSqlString(token)));
            }
            String disableSsl = extraParams.get("quack.disable_ssl");
            if ("true".equalsIgnoreCase(disableSsl)) {
                options.add("DISABLE_SSL true");
            }
        }
        if (!options.isEmpty()) {
            attachSql.append(" (").append(String.join(", ", options)).append(")");
        }
        attachSql.append(";");

        stmt = conn.createStatement();
        stmt.execute(attachSql.toString());
        try { stmt.close(); } catch (Exception ignored) {}
    }

    /**
     * Quack 远程表存在性判断（远端 information_schema 查询）。
     */
    private static boolean quackTableExists(Connection conn, String schema, String table) throws SQLException {
        StringBuilder remoteSql = new StringBuilder("SELECT 1 FROM information_schema.tables WHERE table_name = '")
            .append(JdbcMetadata.escapeSqlString(table))
            .append("'");
        if (schema != null && !schema.isEmpty()) {
            remoteSql.append(" AND table_schema = '").append(JdbcMetadata.escapeSqlString(schema)).append("'");
        }
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(DBUtils.wrapQuackQuery(remoteSql.toString()))) {
            return rs.next();
        }
    }
}
