package com.data.datasource.provider;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSONArray;
import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.job.DatasourceInfo;
import com.data.metadata.DBType;
import com.data.metadata.TableMeta;
import com.data.metadata.util.ConnectionPoolManager;
import com.data.metadata.util.DBUtils;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Apache Polaris 数据源 Provider（Iceberg REST Catalog）。
 *
 * <p>读写都经由 DuckDB：每个 Polaris 数据源使用一个独立的本地 DuckDB scratch 文件，连接建立后：
 * </p>
 * <ol>
 *   <li>加载 {@code httpfs} / {@code iceberg} 扩展；</li>
 *   <li>用 OAuth2 Client ID / Client Secret 创建 Iceberg Secret（可选创建 S3 Secret）；</li>
 *   <li>{@code ATTACH '<warehouse>' AS polaris (TYPE ICEBERG, ENDPOINT '...', SECRET ...)}；</li>
 *   <li>{@code USE polaris}，使未限定 catalog 的 SQL 也落到 Polaris。</li>
 * </ol>
 *
 * <p>连接地址形如 {@code polaris://host:8181/warehouse}；用户名/密码分别为 OAuth2 Client ID /
 * Client Secret，只写入 DuckDB Secret，不用于本地 DuckDB 连接。</p>
 */
@DataSourceRegister("polaris")
public class PolarisDataSourceProvider extends DuckDbDataSourceProvider {

    private static final String ICEBERG_SECRET = "polaris_iceberg_secret";
    private static final String S3_SECRET = "polaris_s3_secret";
    private static final String DEFAULT_PORT = "8181";
    private static final String DEFAULT_DELEGATION_MODE = "vended_credentials";

    /** 每个 scratch 库一把锁：ATTACH / DETACH / CREATE SECRET 均为库级操作，必须串行。 */
    private static final Map<String, ReentrantLock> POLARIS_LOCKS = new ConcurrentHashMap<>();
    /** scratch 库当前挂载时使用的配置指纹，用于配置变化后重新挂载。 */
    private static final Map<String, String> ATTACHED_FINGERPRINTS = new ConcurrentHashMap<>();

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.POLARIS);
    }

    @Override
    protected java.util.List<String> urlPrefixes() {
        return java.util.List.of("polaris:");
    }

    @Override
    public String resolveJdbcUrl(String url) {
        if (url != null && url.startsWith("polaris:")) {
            // 与 openConnection 使用同一份 scratch 文件解析逻辑
            DatasourceInfo info = new DatasourceInfo();
            info.setUrl(url);
            return DBUtils.resolvePolarisJdbcUrl(info);
        }
        return url;
    }

    @Override
    public Connection openConnection(DatasourceInfo info) throws SQLException {
        // 本地 scratch 库无需用户名密码；凭证只用于创建 Iceberg Secret
        Connection conn = ConnectionPoolManager.getConnection(DBUtils.resolvePolarisJdbcUrl(info), null, null);
        try {
            setupPolarisConnection(conn, info);
            return conn;
        } catch (SQLException | RuntimeException e) {
            try {
                conn.close();
            } catch (Exception ignored) {
                // ignore
            }
            throw e;
        }
    }

    @Override
    public boolean supportsTableWrite(DatasourceInfo info, TableMeta table, String eventType) {
        return table != null && PolarisTableWriter.INSTANCE.supports(eventType);
    }

    @Override
    public String writeTable(DatasourceInfo info, Connection connection, TableMeta table, JSONArray records,
                             String eventType, Map<String, String> columnMapping) throws SQLException {
        return PolarisTableWriter.INSTANCE.write(connection, table, records, eventType, columnMapping);
    }

    /**
     * Iceberg 不支持 {@code TRUNCATE}，overwrite 使用 {@code DELETE FROM} 清空。
     */
    @Override
    public boolean useTruncateForOverwrite() {
        return false;
    }

    @Override
    public void prepareTableCreate(DatasourceInfo info, Connection connection, String catalog, String schema)
        throws SQLException {
        if (schema == null || schema.isBlank()) {
            return;
        }
        if (polarisSchemaExists(connection, schema)) {
            return;
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS " + POLARIS_CATALOG + "." + quoteIdentifier(schema));
        } catch (SQLException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            // 部分 Catalog 不支持 IF NOT EXISTS，命名空间已存在时忽略即可
            if (!message.contains("already exist")) {
                throw e;
            }
        }
        // DuckDB 在 ATTACH 时缓存 catalog 的 namespace 列表，新建 namespace 后需要重新挂载才能解析新 schema
        reloadPolarisCatalog(connection, info);
    }

    private static boolean polarisSchemaExists(Connection connection, String schema) throws SQLException {
        String sql = "SELECT COUNT(*) FROM duckdb_schemas() WHERE database_name = '" +
            escape(POLARIS_CATALOG) +
            "' AND schema_name = '" +
            escape(schema) +
            "'";
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /**
     * 重新挂载 Polaris catalog，使新建的 namespace 在 DuckDB 中可见。
     */
    private static void reloadPolarisCatalog(Connection conn, DatasourceInfo info) throws SQLException {
        String scratchUrl = DBUtils.resolvePolarisJdbcUrl(info);
        ReentrantLock lock = POLARIS_LOCKS.computeIfAbsent(scratchUrl, key -> new ReentrantLock());
        lock.lock();
        try {
            if (getAttachedDatabaseType(conn, POLARIS_CATALOG) != null) {
                // Polaris 是当前默认 catalog 时无法 DETACH，先切回本地 scratch 库
                useLocalScratchCatalog(conn);
                detach(conn, POLARIS_CATALOG);
            }
            ATTACHED_FINGERPRINTS.remove(scratchUrl);
            attachPolarisCatalog(conn, info);
        } finally {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------------ 连接初始化

    private static void setupPolarisConnection(Connection conn, DatasourceInfo info) throws SQLException {
        String scratchUrl = DBUtils.resolvePolarisJdbcUrl(info);
        ReentrantLock lock = POLARIS_LOCKS.computeIfAbsent(scratchUrl, key -> new ReentrantLock());
        lock.lock();
        try {
            attachPolarisCatalog(conn, info);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 挂载 Polaris catalog（调用方负责持有 scratch 库对应的锁）。
     */
    private static void attachPolarisCatalog(Connection conn, DatasourceInfo info) throws SQLException {
        Map<String, String> extraParams = info.getExtraParams();
        PolarisAddress address = PolarisAddress.parse(info.getUrl(), extraParams);
        String scratchUrl = DBUtils.resolvePolarisJdbcUrl(info);

        loadExtension(conn, "httpfs");
        loadExtension(conn, "iceberg");
        setupS3Secret(conn, extraParams);

        String fingerprint = fingerprint(info, address);
        String attachedType = getAttachedDatabaseType(conn, POLARIS_CATALOG);
        boolean attached = attachedType != null;
        if (attached && !fingerprint.equals(ATTACHED_FINGERPRINTS.get(scratchUrl))) {
            // 配置/凭证变化或应用重启后无法确认旧 Secret，重新挂载以确保凭证生效
            useLocalScratchCatalog(conn);
            detach(conn, POLARIS_CATALOG);
            attached = false;
        }

        if (!attached) {
            createIcebergSecret(conn, info, address);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(buildAttachSql(address, extraParams, info.getDbschema()));
            }
            ATTACHED_FINGERPRINTS.put(scratchUrl, fingerprint);
        }

        usePolarisCatalog(conn, info.getDbschema());
    }

    /**
     * 未显式限定 catalog 的 SQL 默认落在 Polaris，便于用户在 SQL 编辑器中直接查询；
     * 配置了默认 schema 且 namespace 已存在时一并切换。
     */
    private static void usePolarisCatalog(Connection conn, String schema) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("USE " + POLARIS_CATALOG);
            if (schema != null && !schema.isBlank()) {
                try {
                    stmt.execute("USE " + POLARIS_CATALOG + "." + quoteIdentifier(schema));
                } catch (SQLException ignored) {
                    // namespace 尚未创建时保持 catalog 级默认
                }
            }
        } catch (SQLException ignored) {
            // USE 失败不影响显式 catalog 限定的读写
        }
    }

    private static void loadExtension(Connection conn, String extension) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            try {
                stmt.execute("INSTALL " + extension);
            } catch (SQLException ignored) {
                // 已安装或离线环境：继续尝试 LOAD，由 LOAD 给出最终结果
            }
            try {
                stmt.execute("LOAD " + extension);
            } catch (SQLException e) {
                throw new SQLException(
                    "无法加载 DuckDB " + extension + " 扩展，请确认运行环境可访问 DuckDB 扩展仓库或已预装该扩展: " +
                    e.getMessage(),
                    e
                );
            }
        }
    }

    private static void createIcebergSecret(Connection conn, DatasourceInfo info, PolarisAddress address)
        throws SQLException {
        String clientId = info.getUsername() == null ? "" : info.getUsername().trim();
        String clientSecret = info.getPassword() == null ? "" : info.getPassword().trim();
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            throw new SQLException("Apache Polaris 需要 OAuth2 凭证，请把 Client ID / Client Secret 填入数据源用户名与密码");
        }
        StringBuilder sql = new StringBuilder("CREATE OR REPLACE SECRET ")
            .append(ICEBERG_SECRET)
            .append(" (TYPE ICEBERG, ENDPOINT '")
            .append(escape(address.endpoint))
            .append("', CLIENT_ID '")
            .append(escape(clientId))
            .append("', CLIENT_SECRET '")
            .append(escape(clientSecret))
            .append("'");

        Map<String, String> extraParams = info.getExtraParams();
        String scope = extra(extraParams, "polaris.scope");
        if (scope != null) {
            sql.append(", OAUTH2_SCOPE '").append(escape(scope)).append("'");
        }
        String oauth2ServerUri = extra(extraParams, "polaris.oauth2_server_uri");
        if (oauth2ServerUri != null) {
            sql.append(", OAUTH2_SERVER_URI '").append(escape(oauth2ServerUri)).append("'");
        }
        sql.append(')');
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql.toString());
        }
    }

    private static void setupS3Secret(Connection conn, Map<String, String> extraParams) throws SQLException {
        String keyId = extra(extraParams, "s3.key_id");
        String secret = extra(extraParams, "s3.secret");
        if (keyId == null || secret == null) {
            return;
        }
        StringBuilder sql = new StringBuilder("CREATE OR REPLACE SECRET ")
            .append(S3_SECRET)
            .append(" (TYPE S3, KEY_ID '")
            .append(escape(keyId))
            .append("', SECRET '")
            .append(escape(secret))
            .append("'");
        String endpoint = extra(extraParams, "s3.endpoint");
        String useSsl = extra(extraParams, "s3.use_ssl");
        if (endpoint != null) {
            // DuckDB 的 S3 ENDPOINT 只接受 host[:port]，协议由 USE_SSL 控制
            String normalized = endpoint.trim();
            if (normalized.regionMatches(true, 0, "https://", 0, 8)) {
                normalized = normalized.substring(8);
                if (useSsl == null) {
                    useSsl = "true";
                }
            } else if (normalized.regionMatches(true, 0, "http://", 0, 7)) {
                normalized = normalized.substring(7);
                if (useSsl == null) {
                    useSsl = "false";
                }
            }
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            sql.append(", ENDPOINT '").append(escape(normalized)).append("'");
        }
        String region = extra(extraParams, "s3.region");
        if (region != null) {
            sql.append(", REGION '").append(escape(region)).append("'");
        }
        String urlStyle = extra(extraParams, "s3.url_style");
        if (urlStyle != null) {
            sql.append(", URL_STYLE '").append(escape(urlStyle)).append("'");
        }
        if (useSsl != null) {
            sql.append(", USE_SSL ").append(Boolean.parseBoolean(useSsl));
        }
        sql.append(')');
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql.toString());
        }
    }

    private static String buildAttachSql(PolarisAddress address, Map<String, String> extraParams,
                                         String defaultSchema) {
        String delegationMode = extra(extraParams, "polaris.access_delegation_mode");
        if (delegationMode == null) {
            delegationMode = DEFAULT_DELEGATION_MODE;
        }
        StringBuilder sql = new StringBuilder("ATTACH '")
            .append(escape(address.warehouse))
            .append("' AS ")
            .append(POLARIS_CATALOG)
            .append(" (TYPE ICEBERG, ENDPOINT '")
            .append(escape(address.endpoint))
            .append("', SECRET ")
            .append(ICEBERG_SECRET)
            .append(", ACCESS_DELEGATION_MODE '")
            .append(escape(delegationMode))
            .append("'");
        if (defaultSchema != null && !defaultSchema.isBlank()) {
            sql.append(", DEFAULT_SCHEMA '").append(escape(defaultSchema)).append("'");
        }
        appendBooleanOption(sql, extraParams, "polaris.support_nested_namespaces", "SUPPORT_NESTED_NAMESPACES");
        appendBooleanOption(sql, extraParams, "polaris.stage_create_tables", "STAGE_CREATE_TABLES");
        appendBooleanOption(sql, extraParams, "polaris.disable_multi_table_commit", "DISABLE_MULTI_TABLE_COMMIT");
        appendBooleanOption(
            sql,
            extraParams,
            "polaris.skip_create_table_metadata_updates",
            "SKIP_CREATE_TABLE_METADATA_UPDATES"
        );
        appendBooleanOption(sql, extraParams, "polaris.remove_files_on_delete", "REMOVE_FILES_ON_DELETE");
        sql.append(')');
        return sql.toString();
    }

    /**
     * Polaris 实现差异较大时，可通过额外参数透传 DuckDB 的兼容开关（如部分版本不支持多表提交）。
     */
    private static void appendBooleanOption(StringBuilder sql, Map<String, String> extraParams, String key, String option) {
        String value = extra(extraParams, key);
        if (value != null) {
            sql.append(", ").append(option).append(' ').append(Boolean.parseBoolean(value));
        }
    }

    private static void detach(Connection conn, String catalog) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DETACH " + catalog);
        } catch (SQLException e) {
            throw new SQLException("重新挂载 Polaris catalog 失败（存在未结束的事务？）: " + e.getMessage(), e);
        }
    }

    /**
     * 切换到本地 scratch 库。DuckDB 不允许 DETACH 当前默认数据库。
     */
    private static void useLocalScratchCatalog(Connection conn) throws SQLException {
        String target = null;
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT database_name FROM duckdb_databases()")) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (name == null || name.isBlank()) {
                    continue;
                }
                if (
                    POLARIS_CATALOG.equalsIgnoreCase(name) ||
                    "system".equalsIgnoreCase(name) ||
                    "temp".equalsIgnoreCase(name)
                ) {
                    continue;
                }
                target = name;
                break;
            }
        }
        if (target == null) {
            throw new SQLException("无法切换 DuckDB 默认数据库，Polaris catalog 暂时无法重新挂载");
        }
        try (Statement stmt = conn.createStatement()) {
            // USE 单独的标识符会被当作 schema 解析，这里显式指定本地 scratch 库的 main schema
            stmt.execute("USE " + quoteIdentifier(target) + ".main");
        }
    }

    private static String getAttachedDatabaseType(Connection conn, String catalog) throws SQLException {
        try (
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                "SELECT type FROM duckdb_databases() WHERE database_name = '" + escape(catalog) + "'"
            )
        ) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static String fingerprint(DatasourceInfo info, PolarisAddress address) {
        StringBuilder identity = new StringBuilder();
        identity.append(String.valueOf(info.getUrl())).append('|');
        identity.append(String.valueOf(info.getUsername())).append('|');
        identity.append(String.valueOf(info.getPassword())).append('|');
        identity.append(String.valueOf(info.getDbschema())).append('|');
        identity.append(address.endpoint).append('|').append(address.warehouse);
        Map<String, String> extraParams = info.getExtraParams();
        if (extraParams != null) {
            for (String key : new String[] {
                "polaris.scope",
                "polaris.oauth2_server_uri",
                "polaris.access_delegation_mode",
                "polaris.support_nested_namespaces",
                "polaris.stage_create_tables",
                "polaris.disable_multi_table_commit",
                "polaris.skip_create_table_metadata_updates",
                "polaris.remove_files_on_delete",
                "s3.key_id",
                "s3.endpoint"
            }) {
                identity.append('|').append(String.valueOf(extraParams.get(key)));
            }
        }
        return DigestUtil.sha256Hex(identity.toString());
    }

    private static String extra(Map<String, String> extraParams, String key) {
        if (extraParams == null) {
            return null;
        }
        String value = extraParams.get(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    // ------------------------------------------------------------------ 地址解析

    /**
     * Polaris 连接地址（{@code polaris://host:port/warehouse}）的解析结果。
     */
    static final class PolarisAddress {

        final String host;
        final String port;
        final String warehouse;
        final String endpoint;

        private PolarisAddress(String host, String port, String warehouse, String endpoint) {
            this.host = host;
            this.port = port;
            this.warehouse = warehouse;
            this.endpoint = endpoint;
        }

        static PolarisAddress parse(String url, Map<String, String> extraParams) throws SQLException {
            if (url == null || !url.startsWith("polaris:")) {
                throw new SQLException("无效的 Polaris 连接地址（应以 polaris:// 开头）: " + url);
            }
            String rest = url.substring("polaris:".length());
            if (rest.startsWith("//")) {
                rest = rest.substring(2);
            }
            int slash = rest.indexOf('/');
            String authority = slash >= 0 ? rest.substring(0, slash) : rest;
            String warehouse = slash >= 0 ? rest.substring(slash + 1) : "";
            int query = warehouse.indexOf('?');
            if (query >= 0) {
                warehouse = warehouse.substring(0, query);
            }
            while (warehouse.endsWith("/")) {
                warehouse = warehouse.substring(0, warehouse.length() - 1);
            }

            int colon = authority.lastIndexOf(':');
            String host = colon > 0 ? authority.substring(0, colon) : authority;
            String port = colon > 0 ? authority.substring(colon + 1) : "";
            if (host.isBlank()) {
                throw new SQLException("Polaris 连接地址缺少主机名: " + url);
            }
            if (port.isBlank()) {
                port = DEFAULT_PORT;
            }
            if (warehouse.isBlank()) {
                throw new SQLException(
                    "Polaris 连接地址缺少 Warehouse（Catalog 名称），例如 polaris://host:8181/quickstart_catalog"
                );
            }

            String endpoint;
            String catalogUri = extra(extraParams, "polaris.catalog_uri");
            if (catalogUri != null) {
                endpoint = catalogUri;
                while (endpoint.endsWith("/")) {
                    endpoint = endpoint.substring(0, endpoint.length() - 1);
                }
            } else {
                boolean useSsl = "true".equalsIgnoreCase(extra(extraParams, "polaris.use_ssl"));
                endpoint = (useSsl ? "https" : "http") + "://" + host + ":" + port + "/api/catalog";
            }
            return new PolarisAddress(host, port, warehouse, endpoint);
        }
    }
}
