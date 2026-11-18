package com.data.datasource.provider;

import com.alibaba.fastjson2.JSONArray;
import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.datasource.provider.jdbc.JdbcMetadata;
import com.data.job.DatasourceInfo;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DBType;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.util.ConnectionPoolManager;
import com.data.metadata.util.DBUtils;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * DuckDB 数据源 Provider。
 *
 * <p>承载 DuckDB 特有的行为：本地 catalog/schema 解析、{@code TABLE} 与 {@code BASE TABLE} 两种表类型，
 * 以及 Quack 远程协议（挂载 {@code quack} catalog、通过 {@code quack.query} 读取远端元数据）。
 * 同时作为 {@link DuckLakeDataSourceProvider} 与 {@link PolarisDataSourceProvider} 的基类复用其元数据能力。
 */
@DataSourceRegister("duckdb")
public class DuckDbDataSourceProvider extends JdbcDataSourceProvider {

    public static final String DUCKLAKE_CATALOG = "ducklake";
    public static final String QUACK_CATALOG = "quack";
    public static final String POLARIS_CATALOG = "polaris";

    @Override
    public String normalizeTargetTableName(DatasourceInfo info, String tableName) {
        // DuckDB 自动建表 DDL 使用小写表名，DML 需保持同一名称，避免重启后 tableExists 判断不一致
        return tableName == null ? null : tableName.toLowerCase(Locale.ROOT);
    }

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

    /**
     * 普通 DuckDB 使用 {@link DuckDbTableWriter}（Appender / 集合写入）；
     * Quack 仍走通用 JDBC 写入。DuckLake 在本类中排除，由 {@link DuckLakeDataSourceProvider}
     * 单独覆写为仅 INSERT / APPEND / OVERWRITE 使用 Appender。
     */
    @Override
    public boolean supportsTableWrite(DatasourceInfo info, TableMeta table, String eventType) {
        if (!isNativeDuckDb(info)) {
            return false;
        }
        return DuckDbTableWriter.INSTANCE.supports(table, eventType);
    }

    @Override
    public String writeTable(DatasourceInfo info, Connection connection, TableMeta table, JSONArray records,
                             String eventType, Map<String, String> columnMapping) throws SQLException {
        return DuckDbTableWriter.INSTANCE.write(connection, table, records, eventType, columnMapping);
    }

    private boolean isNativeDuckDb(DatasourceInfo info) {
        if (info == null || info.getUrl() == null) {
            return false;
        }
        String url = info.getUrl();
        return !url.startsWith("quack:") && !url.startsWith("ducklake:") && !url.startsWith("polaris:");
    }

    @Override
    public String targetCatalog(DatasourceInfo info) {
        if (info == null || info.getUrl() == null) {
            return null;
        }
        if (info.getUrl().startsWith("quack:")) {
            return QUACK_CATALOG;
        }
        if (info.getUrl().startsWith("ducklake:")) {
            return DUCKLAKE_CATALOG;
        }
        if (info.getUrl().startsWith("polaris:")) {
            return POLARIS_CATALOG;
        }
        return null;
    }

    /**
     * DuckLake / Polaris 需要显式 catalog 限定；Quack 的远程查询由 Quack 自身包装，表名仍用 schema.table。
     */
    @Override
    public String qualifiedTableName(DatasourceInfo info, String schema, String table) {
        if (info != null && info.getUrl() != null) {
            if (info.getUrl().startsWith("ducklake:")) {
                return DUCKLAKE_CATALOG + "." + schema + "." + table;
            }
            if (info.getUrl().startsWith("polaris:")) {
                return POLARIS_CATALOG + "." + schema + "." + table;
            }
        }
        return schema == null || schema.isBlank() ? table : schema + "." + table;
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

    /**
     * 列出 DuckDB 中可见的 catalog。
     *
     * <p>普通 DuckDB 会返回主库以及通过 {@code ATTACH} 挂载的所有库（过滤系统库 system/temp）；
     * DuckLake / Polaris 连接分别只暴露 {@code ducklake} / {@code polaris} catalog；
     * Quack 连接只暴露 {@code quack} catalog，保持原有行为。
     */
    @Override
    public List<String> getCatalogs(Connection conn) throws SQLException {
        if (isQuackConnection(conn)) {
            return List.of(QUACK_CATALOG);
        }
        if (isPolarisConnection(conn)) {
            // Polaris 底层同样是本地 scratch DuckDB，只向用户暴露远端 catalog
            return List.of(POLARIS_CATALOG);
        }
        Set<String> catalogs = new LinkedHashSet<>();
        String duckLakeScratchCatalog = null;
        if (isDuckLakeConnection(conn)) {
            catalogs.add(DUCKLAKE_CATALOG);
            // DuckLake 使用独立本地 DuckDB 文件作为 scratch 库，不应展示给用户
            duckLakeScratchCatalog = conn.getCatalog();
        }
        for (String catalog : JdbcMetadata.getCatalogs(conn)) {
            if (catalog == null || catalog.isBlank()) {
                continue;
            }
            if ("system".equalsIgnoreCase(catalog) || "temp".equalsIgnoreCase(catalog)) {
                continue;
            }
            if (catalog.startsWith("__ducklake_metadata_")) {
                // DuckLake 内部元数据 catalog，不对用户展示
                continue;
            }
            if (duckLakeScratchCatalog != null && duckLakeScratchCatalog.equals(catalog)) {
                continue;
            }
            catalogs.add(catalog);
        }
        return new ArrayList<>(catalogs);
    }

    @Override
    public List<String> getSchemas(Connection conn) throws SQLException {
        return getSchemas(conn, null);
    }

    @Override
    public List<String> getSchemas(Connection conn, String catalog) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        String targetCatalog = resolveDuckCatalog(conn, catalog);
        if (POLARIS_CATALOG.equals(targetCatalog)) {
            return getPolarisSchemas(conn);
        }
        return new ArrayList<>(getDistinctSchemas(metaData, targetCatalog));
    }

    @Override
    public List<TableMeta> getTables(Connection conn, String schema, Integer limit, String search) throws SQLException {
        return getTables(conn, null, schema, limit, search);
    }

    @Override
    public List<TableMeta> getTables(Connection conn, String catalog, String schema, Integer limit, String search) throws SQLException {
        String targetCatalog = resolveDuckCatalog(conn, catalog);
        // Quack / Polaris 远端目录不会向本地 JDBC 元数据暴露表信息，改为在远端查询
        if (QUACK_CATALOG.equals(targetCatalog)) {
            return getQuackTableList(conn, schema, limit, search);
        }
        if (POLARIS_CATALOG.equals(targetCatalog)) {
            return getPolarisTableList(conn, schema, limit, search);
        }
        return JdbcMetadata.readTableList(conn, targetCatalog, schema, new String[] { "TABLE", "BASE TABLE" }, limit, search, true);
    }

    @Override
    public TableMeta getTableMeta(Connection conn, String schema, String table) throws SQLException {
        return getTableMeta(conn, null, schema, table);
    }

    @Override
    public TableMeta getTableMeta(Connection conn, String catalog, String schema, String table) throws SQLException {
        String targetCatalog = resolveDuckCatalog(conn, catalog);
        TableMeta tableMeta;
        if (QUACK_CATALOG.equals(targetCatalog)) {
            tableMeta = getQuackTableMetaData(conn, schema, table);
        } else if (POLARIS_CATALOG.equals(targetCatalog)) {
            tableMeta = getPolarisTableMetaData(conn, schema, table);
        } else {
            tableMeta = JdbcMetadata.readTableMeta(conn, targetCatalog, schema, table);
        }
        tableMeta.setDbType(POLARIS_CATALOG.equals(targetCatalog) ? DBType.POLARIS.toString() : typeName());
        tableMeta.setCatalog(targetCatalog);
        applyTemporalScale(tableMeta);
        return tableMeta;
    }

    @Override
    public TableMeta getTableDetail(Connection conn, String schema, String table) throws SQLException {
        return getTableDetail(conn, null, schema, table);
    }

    @Override
    public TableMeta getTableDetail(Connection conn, String catalog, String schema, String table) throws SQLException {
        String targetCatalog = resolveDuckCatalog(conn, catalog);
        if (QUACK_CATALOG.equals(targetCatalog) || POLARIS_CATALOG.equals(targetCatalog)) {
            TableMeta tableMeta = QUACK_CATALOG.equals(targetCatalog)
                ? getQuackTableMetaData(conn, schema, table)
                : getPolarisTableMetaData(conn, schema, table);
            tableMeta.setDbType(POLARIS_CATALOG.equals(targetCatalog) ? DBType.POLARIS.toString() : typeName());
            tableMeta.setCatalog(targetCatalog);
            // Iceberg 无索引，也不提供列注释
            applyTemporalScale(tableMeta);
            return tableMeta;
        }
        TableMeta tableMeta = JdbcMetadata.readTableMeta(conn, targetCatalog, schema, table);
        tableMeta.setDbType(typeName());
        tableMeta.setCatalog(targetCatalog);
        tableMeta.setComment(JdbcMetadata.getTableComment(conn, targetCatalog, schema, table));
        tableMeta.setIndexes(JdbcMetadata.getTableIndexes(conn, targetCatalog, schema, table));
        applyTemporalScale(tableMeta);
        return tableMeta;
    }

    /**
     * DuckDB JDBC 不返回 TIMESTAMP/TIME 的小数秒精度，按微秒（6 位）补齐，
     * 避免迁移到 MySQL 时生成 DATETIME/TIME 丢失毫秒甚至发生进位。
     */
    private static void applyTemporalScale(TableMeta tableMeta) {
        if (tableMeta == null) {
            return;
        }
        for (ColumnMeta column : tableMeta.columns()) {
            String type = column.getType() == null ? "" : column.getType().toUpperCase(java.util.Locale.ROOT);
            if (
                column.getScale() <= 0 &&
                ("TIME".equals(type) || "DATETIME".equals(type) || type.startsWith("TIMESTAMP"))
            ) {
                column.setScale(6);
            }
        }
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

    /**
     * 该连接是否挂载了 Polaris（Iceberg REST）catalog。
     */
    public boolean isPolarisConnection(Connection conn) {
        try (ResultSet rs = conn.getMetaData().getCatalogs()) {
            while (rs.next()) {
                if (POLARIS_CATALOG.equals(rs.getString("TABLE_CAT"))) {
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
        if (isPolarisConnection(conn)) {
            return polarisTableExists(conn, schema, table);
        }
        return tableExists(conn, null, schema, table);
    }

    // ------------------------------------------------------------------ DuckDB 内部能力

    /**
     * 解析当前 DuckDB 连接的目标 catalog：优先已挂载的 ducklake / quack / polaris，否则取连接默认 catalog。
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
                if (POLARIS_CATALOG.equals(cat)) {
                    return POLARIS_CATALOG;
                }
            }
        }
        try {
            return metaData.getConnection().getCatalog();
        } catch (Exception e) {
            return "memory";
        }
    }

    /**
     * 解析目标 catalog：显式传入时优先使用，否则回退到连接默认 catalog。
     */
    private static String resolveDuckCatalog(Connection conn, String catalog) throws SQLException {
        if (catalog != null && !catalog.isBlank()) {
            return catalog;
        }
        return resolveDuckCatalog(conn.getMetaData());
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

    // ------------------------------------------------------------------ Polaris（Iceberg REST）元数据

    /** {@code SHOW ALL TABLES} 在 polaris catalog 下的一行。 */
    private static final class PolarisTableRow {

        private String schema;
        private String table;
    }

    /**
     * 读取 Polaris 远端 catalog 的表清单。
     *
     * <p>DuckDB 的 JDBC 元数据接口对挂载的 Iceberg REST Catalog 支持不完整，这里通过
     * {@code SHOW ALL TABLES} 读取（DuckDB 1.5 对已挂载 catalog 的标准做法）。
     * 读取失败时回退到标准 JDBC 元数据。</p>
     */
    private static List<TableMeta> getPolarisTableList(Connection conn, String schema, Integer limit, String search) throws SQLException {
        List<TableMeta> tables = new ArrayList<>();
        try {
            for (PolarisTableRow row : readPolarisTables(conn)) {
                if (row.table == null || !matchesSchema(row.schema, schema)) {
                    continue;
                }
                if (search != null && !search.isEmpty() && !row.table.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                TableMeta tableMeta = new TableMeta(row.table);
                tableMeta.setSchema(row.schema);
                tableMeta.setCatalog(POLARIS_CATALOG);
                tables.add(tableMeta);
                if (limit != null && limit > 0 && tables.size() >= limit) {
                    break;
                }
            }
            if (!tables.isEmpty()) {
                return tables;
            }
        } catch (SQLException ignored) {
            // 回退到 JDBC 元数据
        }
        try {
            return JdbcMetadata.readTableList(
                conn,
                POLARIS_CATALOG,
                schema,
                new String[] { "TABLE", "BASE TABLE" },
                limit,
                search,
                true
            );
        } catch (SQLException e) {
            return tables;
        }
    }

    /**
     * 读取 Polaris 表结构。
     *
     * <p>{@code SHOW ALL TABLES} 对 Iceberg 表只返回占位列（{@code __}/{@code UNKNOWN}），
     * 因此列信息改用 {@code DESCRIBE} 获取。Iceberg catalog 上的 JDBC 元数据接口不完整，
     * 这里不再依赖。</p>
     */
    private static TableMeta getPolarisTableMetaData(Connection conn, String schema, String table) throws SQLException {
        try {
            List<ColumnMeta> columns = readPolarisColumns(conn, schema, table);
            if (!columns.isEmpty()) {
                TableMeta tableMeta = new TableMeta(table, columns);
                tableMeta.setSchema(schema);
                tableMeta.setCatalog(POLARIS_CATALOG);
                tableMeta.setDbType(DBType.POLARIS.toString());
                return tableMeta;
            }
        } catch (SQLException ignored) {
            // 回退到 JDBC 元数据
        }
        TableMeta tableMeta = JdbcMetadata.readTableMeta(conn, POLARIS_CATALOG, schema, table);
        tableMeta.setCatalog(POLARIS_CATALOG);
        return tableMeta;
    }

    private static List<ColumnMeta> readPolarisColumns(Connection conn, String schema, String table) throws SQLException {
        String sql = "DESCRIBE " + quoteIdentifier(POLARIS_CATALOG) + "." + quoteIdentifier(schema) + "." + quoteIdentifier(table);
        List<ColumnMeta> columns = new ArrayList<>();
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int nameIndex = findColumn(meta, "column_name");
            int typeIndex = findColumn(meta, "column_type");
            int nullIndex = findColumn(meta, "null");
            if (nameIndex <= 0 || typeIndex <= 0) {
                return columns;
            }
            while (rs.next()) {
                String type = rs.getString(typeIndex);
                ColumnMeta column = new ColumnMeta(rs.getString(nameIndex), type);
                int[] precisionScale = DatabaseConverter.parseTypePrecisionScale(type);
                if (precisionScale != null) {
                    column.setPrecision(precisionScale[0]);
                    column.setScale(precisionScale[1]);
                }
                String nullable = nullIndex > 0 ? rs.getString(nullIndex) : "YES";
                column.setNullable(!"NO".equalsIgnoreCase(nullable));
                columns.add(column);
            }
        }
        return columns;
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + (identifier == null ? "" : identifier.replace("\"", "\"\"")) + "\"";
    }

    private static List<String> getPolarisSchemas(Connection conn) throws SQLException {
        Set<String> schemas = new LinkedHashSet<>();
        // duckdb_schemas() 能反映 ATTACH 时拿到的全部 namespace，包括还没有表的命名空间
        try (
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                "SELECT schema_name FROM duckdb_schemas() WHERE database_name = '" + POLARIS_CATALOG + "'"
            )
        ) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (name != null && !name.isBlank()) {
                    schemas.add(name);
                }
            }
        } catch (SQLException ignored) {
            // 回退到表清单/JDBC 元数据
        }
        if (!schemas.isEmpty()) {
            return new ArrayList<>(schemas);
        }
        try {
            for (PolarisTableRow row : readPolarisTables(conn)) {
                if (row.schema != null && !row.schema.isBlank()) {
                    schemas.add(row.schema);
                }
            }
        } catch (SQLException ignored) {
            // 回退到 JDBC 元数据
        }
        if (schemas.isEmpty()) {
            try {
                schemas.addAll(getDistinctSchemas(conn.getMetaData(), POLARIS_CATALOG));
            } catch (SQLException ignored) {
                // ignore
            }
        }
        return new ArrayList<>(schemas);
    }

    private static boolean polarisTableExists(Connection conn, String schema, String table) throws SQLException {
        try {
            for (PolarisTableRow row : readPolarisTables(conn)) {
                if (row.table != null && row.table.equalsIgnoreCase(table) && matchesSchema(row.schema, schema)) {
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            return tableExists(conn, POLARIS_CATALOG, schema, table);
        }
    }

    /**
     * 执行 {@code SHOW ALL TABLES} 并读取 polaris catalog 下的表名/列信息。
     *
     * <p>不同 DuckDB 版本对 {@code SHOW} 语句能否出现在子查询中支持不一致，这里直接执行
     * 语句并在结果集上按 {@code database} 列过滤。</p>
     */
    private static List<PolarisTableRow> readPolarisTables(Connection conn) throws SQLException {
        List<PolarisTableRow> rows = new ArrayList<>();
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SHOW ALL TABLES")) {
            ResultSetMetaData meta = rs.getMetaData();
            int databaseIndex = findColumn(meta, "database");
            if (databaseIndex <= 0) {
                return rows;
            }
            int schemaIndex = findColumn(meta, "schema");
            int tableIndex = findColumn(meta, "name", "table_name");
            while (rs.next()) {
                String database = rs.getString(databaseIndex);
                if (database != null && !POLARIS_CATALOG.equalsIgnoreCase(database)) {
                    continue;
                }
                PolarisTableRow row = new PolarisTableRow();
                row.schema = schemaIndex > 0 ? rs.getString(schemaIndex) : null;
                row.table = tableIndex > 0 ? rs.getString(tableIndex) : null;
                rows.add(row);
            }
        }
        return rows;
    }

    private static int findColumn(ResultSetMetaData meta, String... candidates) throws SQLException {
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String label = meta.getColumnLabel(i);
            if (label == null) {
                continue;
            }
            for (String candidate : candidates) {
                if (candidate.equalsIgnoreCase(label)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean matchesSchema(String actual, String expected) {
        return expected == null || expected.isEmpty() || (actual != null && actual.equalsIgnoreCase(expected));
    }
}
