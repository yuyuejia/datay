package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.datasource.provider.jdbc.JdbcMetadata;
import com.data.job.DatasourceInfo;
import com.data.metadata.DBType;
import com.data.metadata.util.ConnectionPoolManager;
import com.data.metadata.util.DBUtils;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * DuckLake 数据源 Provider。
 *
 * <p>在 {@link DuckDbDataSourceProvider} 的元数据能力之上，负责 DuckLake 特有的连接初始化：
 * 每个 DuckLake 使用独立的本地 DuckDB 文件（避免同一元数据重复挂载），并串行执行
 * {@code ATTACH 'ducklake:...'}；同时按需创建 S3 Secret。
 */
@DataSourceRegister("ducklake")
public class DuckLakeDataSourceProvider extends DuckDbDataSourceProvider {

    // 每个 DuckLake 元数据路径对应一把锁，保证同一数据源的 ATTACH 串行执行
    private static final Map<String, ReentrantLock> DUCKLAKE_LOCKS = new ConcurrentHashMap<>();

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.DUCKLAKE);
    }

    @Override
    protected List<String> urlPrefixes() {
        return List.of("ducklake:");
    }

    @Override
    public Connection openConnection(DatasourceInfo info) throws SQLException {
        // 本地 scratch 库无需用户名密码：DuckDB 会拒绝以不同 user 配置打开同一文件
        Connection conn = ConnectionPoolManager.getConnection(DBUtils.resolveDuckLakeJdbcUrl(info), null, null);
        setupDuckLakeConnection(conn, info);
        return conn;
    }

    private static void setupDuckLakeConnection(Connection conn, DatasourceInfo datasourceInfo) throws SQLException {
        Map<String, String> extraParams = datasourceInfo.getExtraParams();
        String ducklakeFilePath = DBUtils.resolveDuckLakeMetadataLocation(datasourceInfo);

        // 同一 DuckLake 的连接可能来自同一连接池的多个连接，ATTACH 为库级操作，需串行处理
        ReentrantLock lock = DUCKLAKE_LOCKS.computeIfAbsent(ducklakeFilePath, key -> new ReentrantLock());
        lock.lock();
        try {
            Statement stmt;

            stmt = conn.createStatement();
            try { stmt.execute("INSTALL ducklake"); } catch (Exception ignored) {}
            try { stmt.execute("LOAD ducklake"); } catch (Exception ignored) {}
            try { stmt.close(); } catch (Exception ignored) {}

            if (extraParams != null) {
                String store = extraParams.get("s3.data_path");
                if (store != null && store.startsWith("s3://")) {
                    String keyId = extraParams.get("s3.key_id");
                    String secret = extraParams.get("s3.secret");
                    String endpoint = extraParams.get("s3.endpoint");
                    String urlStyle = extraParams.get("s3.url_style");
                    String useSsl = extraParams.get("s3.use_ssl");

                    if (keyId != null && secret != null && endpoint != null && urlStyle != null && useSsl != null) {
                        stmt = conn.createStatement();
                        String s3Secret = String.format(
                            "CREATE OR REPLACE SECRET (TYPE s3, KEY_ID '%s', SECRET '%s', ENDPOINT '%s', url_style '%s', USE_SSL '%s');",
                            keyId,
                            secret,
                            endpoint,
                            urlStyle,
                            useSsl
                        );
                        stmt.execute(s3Secret);
                        try { stmt.close(); } catch (Exception ignored) {}
                    }
                }
            }

            // DuckLake 必须以 "ducklake:" 形式挂载，否则会被当成普通 DuckDB 库，暴露 ducklake_* 元数据表
            String attachedType = getAttachedDatabaseType(conn, DUCKLAKE_CATALOG);
            if (!"ducklake".equalsIgnoreCase(attachedType)) {
                // 兼容历史错误挂载：已作为普通 duckdb 库挂载时先卸载再重新按时挂载
                if (attachedType != null) {
                    stmt = conn.createStatement();
                    try { stmt.execute("DETACH " + DUCKLAKE_CATALOG + ";"); } catch (Exception ignored) {}
                    try { stmt.close(); } catch (Exception ignored) {}
                }

                StringBuilder attachSql = new StringBuilder(
                    String.format("ATTACH 'ducklake:%s' AS %s", JdbcMetadata.escapeSqlString(ducklakeFilePath), DUCKLAKE_CATALOG)
                );
                String store = extraParams != null ? extraParams.get("s3.data_path") : null;
                if (store != null && !store.trim().isEmpty()) {
                    attachSql.append(" (data_path '").append(JdbcMetadata.escapeSqlString(store)).append("')");
                }
                attachSql.append(";");

                stmt = conn.createStatement();
                stmt.execute(attachSql.toString());
                try { stmt.close(); } catch (Exception ignored) {}
            }
        } finally {
            lock.unlock();
        }
    }

    private static String getAttachedDatabaseType(Connection conn, String catalog) throws SQLException {
        try (
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                String.format("SELECT type FROM duckdb_databases() WHERE database_name = '%s'", catalog)
            )
        ) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
