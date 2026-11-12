package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.datasource.provider.jdbc.JdbcMetadata;
import com.data.job.DatasourceInfo;
import com.data.metadata.DBType;
import com.data.metadata.TableMeta;
import com.data.metadata.util.ConnectionPoolManager;
import com.data.metadata.util.DriverDownloader;
import com.data.metadata.util.TdengineJdbcSupport;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * TDengine 数据源 Provider，复用 {@link JdbcDataSourceProvider} 的 JDBC 标准能力。
 *
 * <p>TDengine 没有 schema 概念，database 即 JDBC catalog，因此元数据统一按
 * {@code (catalog=database, schema=null)} 组合读取。三种连接协议（WebSocket / REST /
 * 原生）由 URL 自动选择驱动类，默认 WebSocket。
 *
 * <p>TDengine 的超级表（STable）不能直接执行普通 {@code INSERT INTO}，写入前会做校验并
 * 给出明确提示，需要落超级表时请使用 {@code SqlSink} 的
 * {@code INSERT INTO ... USING ... TAGS ...} 语法。
 */
@DataSourceRegister("tdengine")
public class TdengineDataSourceProvider extends JdbcDataSourceProvider {

    private static final String DEFAULT_VERSION = "default";

    private static final Set<String> WRITE_EVENT_TYPES = Set.of(
        "INSERT",
        "UPDATE",
        "REPLACE",
        "APPEND",
        "OVERWRITE",
        "DELETE"
    );

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.TDENGINE);
    }

    @Override
    protected List<String> urlPrefixes() {
        return TdengineJdbcSupport.urlPrefixes();
    }

    @Override
    public boolean supportsUrl(String url) {
        return TdengineJdbcSupport.isTdengineUrl(url);
    }

    @Override
    public String getTestQuery() {
        return "SELECT 1";
    }

    @Override
    public Connection openConnection(DatasourceInfo info) throws SQLException {
        // TDengine 驱动不随平台打包，连接前确保本地驱动目录已就绪。
        ensureDriverAvailable(DEFAULT_VERSION);
        if (!DriverDownloader.isDriverInstalled(DBType.TDENGINE, DEFAULT_VERSION)) {
            throw new SQLException(
                "TDengine JDBC 驱动未安装，请先在数据源管理页面下载 taos-jdbcdriver，或检查 Maven 仓库网络配置"
            );
        }
        String driverClass = TdengineJdbcSupport.driverClassName(info == null ? null : info.getUrl());
        File driverDir = driverDirectory(DEFAULT_VERSION);
        return ConnectionPoolManager.getConnection(
            info,
            driverClass,
            driverDir == null ? null : driverDir.getAbsolutePath(),
            getTestQuery()
        );
    }

    // ------------------------------------------------------------------ 元数据（catalog 型）

    @Override
    public List<String> getSchemas(Connection conn) throws SQLException {
        List<String> schemas = JdbcMetadata.readSchemas(conn);
        if (!schemas.isEmpty()) {
            return schemas;
        }
        List<String> catalogs = JdbcMetadata.getCatalogs(conn);
        if (!catalogs.isEmpty()) {
            return catalogs;
        }
        try {
            String catalog = conn.getCatalog();
            if (catalog != null && !catalog.isBlank()) {
                return List.of(catalog);
            }
        } catch (SQLException ignored) {
            // ignore
        }
        return readDatabases(conn);
    }

    @Override
    public List<TableMeta> getTables(Connection conn, String schema, Integer limit, String search) throws SQLException {
        return getTables(conn, null, schema, limit, search);
    }

    @Override
    public List<TableMeta> getTables(
        Connection conn,
        String catalog,
        String schema,
        Integer limit,
        String search
    ) throws SQLException {
        String database = hasText(catalog) ? catalog : schema;
        String[] types = new String[] { "TABLE", "VIEW" };
        List<TableMeta> tables = JdbcMetadata.readTableList(conn, database, null, types, limit, search, false);
        if (tables.isEmpty() && hasText(database)) {
            // 兜底：部分驱动版本把 database 暴露在 schema 字段
            tables = JdbcMetadata.readTableList(conn, null, database, types, limit, search, false);
        }
        for (TableMeta table : tables) {
            // TDengine 的 database 统一作为 schema 暴露给上层，避免 catalog + schema 重复限定
            table.setSchema(database);
            table.setCatalog(null);
            table.setDbType(typeName());
        }
        return tables;
    }

    @Override
    public TableMeta getTableMeta(Connection conn, String catalog, String schema, String table) throws SQLException {
        String database = hasText(catalog) ? catalog : schema;
        TableMeta tableMeta = JdbcMetadata.readTableMeta(conn, database, null, table);
        tableMeta.setSchema(database);
        tableMeta.setCatalog(null);
        tableMeta.setDbType(typeName());
        return tableMeta;
    }

    @Override
    public TableMeta getTableMeta(Connection conn, String schema, String table) throws SQLException {
        return getTableMeta(conn, null, schema, table);
    }

    @Override
    public TableMeta getTableDetail(Connection conn, String catalog, String schema, String table) throws SQLException {
        String database = hasText(catalog) ? catalog : schema;
        TableMeta tableMeta = JdbcMetadata.readTableMeta(conn, database, null, table);
        tableMeta.setSchema(database);
        tableMeta.setCatalog(null);
        tableMeta.setDbType(typeName());
        tableMeta.setComment(JdbcMetadata.getTableComment(conn, database, null, table));
        tableMeta.setIndexes(JdbcMetadata.getTableIndexes(conn, database, null, table));
        return tableMeta;
    }

    @Override
    public TableMeta getTableDetail(Connection conn, String schema, String table) throws SQLException {
        return getTableDetail(conn, null, schema, table);
    }

    // ------------------------------------------------------------------ 写入前校验

    /**
     * TDengine 超级表不能直接写入普通数据，写入前给出明确错误，避免驱动返回难以理解的 SQL 异常。
     */
    @Override
    public void validateWriteTarget(DatasourceInfo info, Connection connection, TableMeta table, String eventType)
        throws SQLException {
        if (table == null || !isWriteEvent(eventType)) {
            return;
        }
        if ("DELETE".equalsIgnoreCase(eventType.trim())) {
            throw new SQLException(
                "TDengine 不支持通用 ETL 的按行 DELETE（无主键，无法安全定位数据）。" +
                "请使用 SqlSink 按时间列或标签列自定义删除条件"
            );
        }
        String database = hasText(table.getSchema()) ? table.getSchema() : safeCatalog(connection);
        if (isStable(connection, database, table.getTable())) {
            throw new SQLException(
                "TDengine 超级表不支持普通 INSERT/UPDATE 写入：" +
                qualifiedName(database, table.getTable()) +
                "。请改用 SqlSink，并配置 INSERT INTO 子表 USING 超级表 TAGS (...) VALUES (...) 语法"
            );
        }
    }

    private boolean isWriteEvent(String eventType) {
        return eventType != null && WRITE_EVENT_TYPES.contains(eventType.trim().toUpperCase(Locale.ROOT));
    }

    private boolean isStable(Connection conn, String database, String table) throws SQLException {
        if (hasText(database)) {
            String sql = "SELECT stable_name FROM information_schema.ins_stables WHERE db_name = ? AND stable_name = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, database);
                ps.setString(2, table);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException ignored) {
                // 不同版本的 information_schema 字段可能不同，回退到 SHOW STABLES
            }
        }
        String prefix = hasText(database) ? quote(database) + "." : "";
        String showSql = "SHOW " + prefix + "STABLES LIKE '" + escapeLiteral(table) + "'";
        try (Statement statement = conn.createStatement(); ResultSet rs = statement.executeQuery(showSql)) {
            while (rs.next()) {
                String stableName = rs.getString(1);
                if (stableName != null && stableName.equalsIgnoreCase(table)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> readDatabases(Connection conn) {
        Set<String> databases = new LinkedHashSet<>();
        try (Statement statement = conn.createStatement(); ResultSet rs = statement.executeQuery("SHOW DATABASES")) {
            while (rs.next()) {
                String database = rs.getString(1);
                if (database != null && !database.isBlank()) {
                    databases.add(database);
                }
            }
        } catch (SQLException ignored) {
            // 驱动不支持 SHOW DATABASES 时保持空列表
        }
        return new ArrayList<>(databases);
    }

    private String safeCatalog(Connection conn) {
        try {
            return conn.getCatalog();
        } catch (SQLException e) {
            return null;
        }
    }

    private String qualifiedName(String database, String table) {
        return hasText(database) ? quote(database) + "." + quote(table) : quote(table);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private static String escapeLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}
