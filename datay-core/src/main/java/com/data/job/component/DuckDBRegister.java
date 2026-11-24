package com.data.job.component;

import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.DuckDBEngine;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.util.DBUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;

/**
 * DuckDB数据源注册组件
 * 用于将外部数据源通过DuckDB插件机制attach到DuckDB中
 * 使用DatasourceInfo作为数据源信息参数
 */
@ComponentRegister(
    value = "DuckDBRegister",
    name = "注册表",
    group = "DuckDB 组件",
    desc = "将外部数据源通过 DuckDB 插件机制 attach 到 DuckDB 中，便于统一查询。",
    order = 10
)
public class DuckDBRegister extends FlowComponent {

    private static final String TYPE_DUCKDB = "duckdb";
    private static final String TYPE_DUCKLAKE = "ducklake";
    private static final String TYPE_MYSQL = "mysql";
    private static final String TYPE_POSTGRES = "postgres";
    private static final String TYPE_SQLITE = "sqlite";

    /** 需要创建 DuckDB SECRET 才能挂载的外部数据库类型。 */
    private static final Set<String> SECRET_TYPES = Set.of(TYPE_MYSQL, TYPE_POSTGRES);

    /** DuckDB 没有对应扩展、无法直接 ATTACH 的数据库类型。 */
    private static final Set<String> UNSUPPORTED_TYPES = Set.of("sqlserver", "oracle");

    // 数据源信息（包含URL、用户名、密码等）
    private DatasourceInfo datasource;

    // 数据源别名（在DuckDB中的名称）
    private String alias;

     public DuckDBRegister() {
        setType(ComponentType.SOURCE);  // 设置为Source类型
    }

    @Override
    public void execute(FlowFile flowFile) {
        try {
            // 注册数据源到DuckDB
            registerDataSource();

        } catch (Exception e) {
            throw new RuntimeException("数据源注册失败: " + e.getMessage(), e);
        }
    }

    /**
     * 注册数据源到DuckDB
     */
    private void registerDataSource() throws SQLException {
        if (datasource == null) {
            throw new IllegalArgumentException("数据源信息不能为空");
        }

        if (alias == null || alias.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源别名不能为空");
        }

        String datasourceType = getDatabaseTypeFromUrl();
        if (datasourceType == null) {
            throw new IllegalArgumentException("无法从URL中识别数据库类型: " + datasource.getUrl());
        }
        if (UNSUPPORTED_TYPES.contains(datasourceType)) {
            throw new IllegalArgumentException(
                "DuckDB 未提供内置的 " + datasourceType + " 扩展，无法直接 ATTACH；"
                    + "请改用 JDBC 数据源，或通过 DuckDB odbc 扩展访问"
            );
        }

        try (
            Connection duckdbConn = DuckDBEngine.getInstance().getConnection(getContext().getJobInstanceCode());
            Statement stmt = duckdbConn.createStatement()
        ) {
            // 1. 安装并加载对应扩展（DuckDB 数据库文件使用内置引擎，无需扩展）
            installAndLoadExtension(stmt, datasourceType);

            // 2. 创建连接密钥（DuckLake 的 S3 配置单独处理）
            if (TYPE_DUCKLAKE.equals(datasourceType)) {
                setupDuckLakeSecret(stmt);
            } else if (SECRET_TYPES.contains(datasourceType)) {
                String secretSql = buildSecretSql(datasourceType);
                this.logInfo("创建连接密钥: " + secretName());
                stmt.execute(secretSql);
            }

            // 3. 构建并执行 ATTACH 命令
            String attachSql = buildAttachSql(datasourceType);
            this.logInfo("执行ATTACH: " + attachSql);
            stmt.execute(attachSql);

            this.logInfo("数据源注册完成: " + datasourceType + " -> " + alias);
        }
    }

    /**
     * 安装并加载 DuckDB 扩展。DuckDB 数据库文件由内置引擎处理，不需要扩展；
     * 其余类型先尝试 INSTALL，失败时继续 LOAD，以兼容扩展已内置或离线环境。
     */
    private void installAndLoadExtension(Statement stmt, String datasourceType) throws SQLException {
        if (TYPE_DUCKDB.equals(datasourceType)) {
            this.logInfo("DuckDB 数据库使用内置引擎，无需安装扩展");
            return;
        }

        String installSql = "INSTALL " + datasourceType + ";";
        this.logInfo("安装插件: " + installSql);
        try {
            stmt.execute(installSql);
        } catch (SQLException e) {
            this.logInfo("安装插件失败，尝试直接加载: " + e.getMessage());
        }

        String loadSql = "LOAD " + datasourceType + ";";
        this.logInfo("加载插件: " + loadSql);
        stmt.execute(loadSql);
    }

    //    CREATE SECRET mysql_secret_one (
    //        TYPE mysql,
    //        HOST '127.0.0.1',
    //        PORT 0,
    //        DATABASE mysql,
    //        USER 'mysql',
    //        PASSWORD ''
    //    );
    private String buildSecretSql(String datasourceType) {
        DatasourceInfo connInfo = DBUtils.parseConnectionUrl(datasource);
        String host = firstNonBlank(connInfo.getHostname(), datasource.getHostname());
        String port = firstNonBlank(connInfo.getPort(), datasource.getPort());
        String database = firstNonBlank(connInfo.getDbschema(), datasource.getDbschema());
        String username = firstNonBlank(connInfo.getUsername(), datasource.getUsername());
        String password = firstNonBlank(connInfo.getPassword(), datasource.getPassword());

        StringBuilder secretSql = new StringBuilder();
        secretSql.append("CREATE OR REPLACE SECRET ").append(quoteIdentifier(secretName()));
        secretSql.append(" (TYPE ").append(datasourceType);
        if (host != null) {
            secretSql.append(", HOST ").append(sqlLiteral(host));
        }
        if (port != null && port.matches("\\d+")) {
            secretSql.append(", PORT ").append(port);
        }
        if (database != null) {
            secretSql.append(", DATABASE ").append(sqlLiteral(database));
        }
        if (username != null) {
            secretSql.append(", USER ").append(sqlLiteral(username));
        }
        if (password != null) {
            secretSql.append(", PASSWORD ").append(sqlLiteral(password));
        }
        secretSql.append(")");
        return secretSql.toString();
    }

    private void setupDuckLakeSecret(Statement stmt) throws SQLException {
        Map<String, String> extraParams = datasource.getExtraParams();
        String store = extraParams != null ? extraParams.get("s3.data_path") : null;
        boolean isS3Path = store != null && store.startsWith("s3://");
        if (isS3Path) {
            stmt.execute(buildS3Secret());
        }
    }

    private String buildS3Secret() {
        // 对于ducklake类型，需要构建S3 secret
        Map<String, String> extraParams = datasource.getExtraParams();
        if (extraParams == null || extraParams.isEmpty()) {
            throw new IllegalArgumentException("DuckLake数据源配置中未包含S3配置信息");
        }

        String keyId = extraParams.get("s3.key_id");
        String secret = extraParams.get("s3.secret");
        String endpoint = extraParams.get("s3.endpoint");
        String urlStyle = extraParams.get("s3.url_style");
        String useSsl = extraParams.get("s3.use_ssl");

        if (keyId == null || secret == null || endpoint == null || urlStyle == null || useSsl == null) {
            throw new IllegalArgumentException("DuckLake数据源配置中S3配置不完整");
        }

        return String.format(
                """
                        CREATE OR REPLACE SECRET (
                              TYPE s3,
                              KEY_ID '%s',
                              SECRET '%s',
                              ENDPOINT '%s',
                              url_style '%s',
                              USE_SSL '%s'
                          );""",
                sqlLiteralContent(keyId),
                sqlLiteralContent(secret),
                sqlLiteralContent(endpoint),
                sqlLiteralContent(urlStyle),
                sqlLiteralContent(useSsl)
        );
    }

    /**
     * 从URL中提取数据库类型
     */
    private String getDatabaseTypeFromUrl() {
        if (datasource == null || datasource.getUrl() == null) {
            return null;
        }

        String url = datasource.getUrl().trim().toLowerCase();

        // 先按前缀精确识别，避免数据库名/文件路径中出现关键字时误判
        if (url.startsWith("ducklake:")) {
            return TYPE_DUCKLAKE;
        }
        if (url.startsWith("jdbc:mysql:")) {
            return TYPE_MYSQL;
        }
        if (url.startsWith("jdbc:postgresql:") || url.startsWith("jdbc:postgres:")) {
            return TYPE_POSTGRES;
        }
        if (url.startsWith("jdbc:sqlite:")) {
            return TYPE_SQLITE;
        }
        if (url.startsWith("jdbc:sqlserver:")) {
            return "sqlserver";
        }
        if (url.startsWith("jdbc:oracle:")) {
            return "oracle";
        }
        if (url.startsWith("jdbc:duckdb:")) {
            return TYPE_DUCKDB;
        }

        // 兼容历史配置：URL 中未使用标准前缀时按关键字识别
        if (url.contains("mysql")) {
            return TYPE_MYSQL;
        } else if (url.contains("postgresql") || url.contains("postgres")) {
            return TYPE_POSTGRES;
        } else if (url.contains("sqlite")) {
            return TYPE_SQLITE;
        } else if (url.contains("sqlserver") || url.contains("mssql")) {
            return "sqlserver";
        } else if (url.contains("oracle")) {
            return "oracle";
        } else if (url.contains("ducklake")) {
            return TYPE_DUCKLAKE;
        } else if (url.contains("duckdb")) {
            return TYPE_DUCKDB;
        }

        return null;
    }

    /**
     * 构建数据库连接字符串
     */
    private String buildAttachSql(String datasourceType) {
        String aliasSql = quoteIdentifier(alias);
        DatasourceInfo connInfo = DBUtils.parseConnectionUrl(datasource);

        switch (datasourceType) {
            case TYPE_DUCKLAKE:
                Map<String, String> extraParams = datasource.getExtraParams();
                String store = extraParams != null ? extraParams.get("s3.data_path") : null;
                if (store == null || store.trim().isEmpty()) {
                    throw new IllegalArgumentException("DuckLake数据源配置中缺少s3.data_path参数");
                }
                return "ATTACH " + sqlLiteral(datasource.getUrl()) + " AS " + aliasSql
                    + " (data_path " + sqlLiteral(store) + ");";
            case TYPE_DUCKDB:
                return "ATTACH " + sqlLiteral(duckDbFilePath()) + " AS " + aliasSql + " (TYPE duckdb);";
            case TYPE_MYSQL:
            case TYPE_POSTGRES:
                // 连接信息统一放在 SECRET 中，避免密码出现在日志里
                return "ATTACH '' AS " + aliasSql + " (TYPE " + datasourceType + ", SECRET "
                    + quoteIdentifier(secretName()) + ");";
            case TYPE_SQLITE:
                return "ATTACH " + sqlLiteral(sqliteFilePath(connInfo)) + " AS " + aliasSql + " (TYPE sqlite);";
            default:
                throw new IllegalArgumentException("暂不支持通过 DuckDB 挂载类型: " + datasourceType);
        }
    }

    /**
     * 解析 DuckDB 数据库文件路径，支持 {@code jdbc:duckdb:/path/to/db}。
     */
    private String duckDbFilePath() {
        String url = datasource.getUrl();
        String path = url;
        int index = url.toLowerCase().indexOf("jdbc:duckdb:");
        if (index >= 0) {
            path = url.substring(index + "jdbc:duckdb:".length());
        }
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        path = firstNonBlank(path, datasource.getDbschema());
        if (path == null) {
            throw new IllegalArgumentException(
                "DuckDB 类型需要指定数据库文件路径，例如 jdbc:duckdb:/path/to/database.duckdb");
        }
        return path;
    }

    /**
     * 解析 SQLite 数据库文件路径。
     */
    private String sqliteFilePath(DatasourceInfo connInfo) {
        String path = firstNonBlank(connInfo.getDbschema(), datasource.getDbschema());
        if (path == null) {
            String url = datasource.getUrl();
            int index = url.toLowerCase().indexOf("jdbc:sqlite:");
            if (index >= 0) {
                path = url.substring(index + "jdbc:sqlite:".length());
                int queryIndex = path.indexOf('?');
                if (queryIndex >= 0) {
                    path = path.substring(0, queryIndex);
                }
            }
        }
        if (path == null) {
            throw new IllegalArgumentException(
                "SQLite 类型需要指定数据库文件路径，例如 jdbc:sqlite:/path/to/database.db");
        }
        return path;
    }

    private String secretName() {
        return alias + "_secret";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String sqlLiteral(String value) {
        return "'" + sqlLiteralContent(value) + "'";
    }

    private static String sqlLiteralContent(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private static String quoteIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    // 自动注入方法
    public void setDatasource(DatasourceInfo datasource) {
        this.datasource = datasource;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }
}
