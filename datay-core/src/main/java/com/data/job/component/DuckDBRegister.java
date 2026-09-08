package com.data.job.component;

import com.data.job.DatasourceInfo;
import com.data.job.DuckDBEngine;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.util.DBUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * DuckDB数据源注册组件
 * 用于将外部数据源通过DuckDB插件机制attach到DuckDB中
 * 使用DatasourceInfo作为数据源信息参数
 */
public class DuckDBRegister extends FlowComponent {

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

        try (
            Connection duckdbConn = DuckDBEngine.getInstance().getConnection(getContext().getJobInstanceCode());
            Statement stmt = duckdbConn.createStatement()
        ) {
            // 对于ducklake类型，不需要安装和加载插件
            // 1. 安装对应的数据库插件
            String installSql = "INSTALL " + datasourceType + ";";
            this.logInfo("安装插件: " + installSql);
            stmt.execute(installSql);

            // 2. 加载插件
            String loadSql = "LOAD " + datasourceType + ";";
            this.logInfo("加载插件: " + loadSql);
            stmt.execute(loadSql);

            // 3. 创建连接密码（ducklake需要特殊处理）
            if ("ducklake".equals(datasourceType)) {
                Map<String, String> extraParams = datasource.getExtraParams();
                String store = extraParams != null ? extraParams.get("s3.data_path") : null;
                boolean isS3Path = store != null && store.startsWith("s3://");
                if (isS3Path) {
                    String s3SecretSql = buildS3Secret();
                    stmt.execute(s3SecretSql);
                }
            } else {
                String secretSql = buildSecretSql(datasourceType);
                stmt.execute(secretSql);
            }


            // 4. 构建连接字符串
            String attachSql = buildAttachSql(datasourceType);

            // 5. 执行ATTACH命令
            this.logInfo("执行ATTACH: " + attachSql);
            stmt.execute(attachSql);

            this.logInfo("数据源注册完成: " + datasourceType + " -> " + alias);
        }
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
        // 其他数据库类型的secret构建逻辑保持不变
        StringBuilder secretSql = new StringBuilder();
        DatasourceInfo connInfo = DBUtils.parseConnectionUrl(datasource);
        secretSql.append("CREATE SECRET ").append(alias).append("_secret (");
        secretSql.append("TYPE ").append(datasourceType).append(",");
        secretSql.append("HOST '").append(connInfo.getHostname()).append("',");
        secretSql.append("PORT ").append(connInfo.getPort()).append(",");
        secretSql.append("DATABASE ").append(connInfo.getDbschema()).append(",");
        secretSql.append("USER '").append(connInfo.getUsername()).append("',");
        secretSql.append("PASSWORD '").append(connInfo.getPassword()).append("');");
        return secretSql.toString();
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
                keyId, secret, endpoint, urlStyle, useSsl
        );
    }

    /**
     * 从URL中提取数据库类型
     */
    private String getDatabaseTypeFromUrl() {
        if (datasource == null || datasource.getUrl() == null) {
            return null;
        }

        String url = datasource.getUrl().toLowerCase();

        if (url.contains("mysql")) {
            return "mysql";
        } else if (url.contains("postgresql") || url.contains("postgres")) {
            return "postgres";
        } else if (url.contains("sqlite")) {
            return "sqlite";
        } else if (url.contains("sqlserver") || url.contains("mssql")) {
            return "sqlserver";
        } else if (url.contains("oracle")) {
            return "oracle";
        } else if (url.contains("duckdb")) {
            return "duckdb";
        } else if (url.contains("ducklake")) {
            return "ducklake";
        }

        return null;
    }

    /**
     * 构建数据库连接字符串
     */
    private String buildAttachSql(String datasourceType) {
        StringBuilder connStr = new StringBuilder();
        connStr.append("ATTACH '");
        // 从URL中解析连接参数
        DatasourceInfo connInfo = DBUtils.parseConnectionUrl(datasource);

        switch (datasourceType.toLowerCase()) {
            case "ducklake":
                Map<String, String> extraParams = datasource.getExtraParams();
                String store = extraParams != null ? extraParams.get("s3.data_path") : null;
                if (store == null || store.trim().isEmpty()) {
                    throw new IllegalArgumentException("DuckLake数据源配置中缺少s3.data_path参数");
                }
                connStr.append(datasource.getUrl()).append("' AS ").append(alias);
                connStr.append(" (data_path '").append(store).append("');");
                break;
            case "mysql":
                if (datasource.getDbschema() != null && !datasource.getDbschema().isEmpty()) {
                    connStr.append(" database=").append(datasource.getDbschema());
                }
                connStr.append("' AS ").append(alias).append(" (TYPE ").append(datasourceType).append(", SECRET ").append(alias).append("_secret);");
                break;
            case "postgres":
            case "postgresql":
                connStr.append("host=").append(connInfo.getHostname());
                if (connInfo.getPort() != null && !connInfo.getPort().isEmpty()) {
                    connStr.append(" port=").append(connInfo.getPort());
                } else {
                    connStr.append(" port=5432");
                }
                if (connInfo.getDbschema() != null && !connInfo.getDbschema().isEmpty()) {
                    connStr.append(" database=").append(connInfo.getDbschema());
                }
                if (connInfo.getUsername() != null && !connInfo.getUsername().isEmpty()) {
                    connStr.append(" user=").append(connInfo.getUsername());
                }
                if (connInfo.getPassword() != null && !connInfo.getPassword().isEmpty()) {
                    connStr.append(" password=").append(connInfo.getPassword());
                }
                connStr.append("' AS ").append(alias).append(" (TYPE ").append(datasourceType).append(", SCHEMA '").append(connInfo.getDbschema()).append("');");
                break;
            case "sqlite":
                // SQLite使用文件路径
                if (connInfo.getDbschema() != null && !connInfo.getDbschema().isEmpty()) {
                    connStr.append("file=").append(connInfo.getDbschema());
                }
                connStr.append("' AS ").append(alias).append(" (TYPE ").append(datasourceType).append(");");
                break;
            case "sqlserver":
                connStr.append("server=").append(connInfo.getHostname());
                if (connInfo.getPort() != null && !connInfo.getPort().isEmpty()) {
                    connStr.append(",").append(connInfo.getPort());
                }
                if (connInfo.getDbschema() != null && !connInfo.getDbschema().isEmpty()) {
                    connStr.append(" database=").append(connInfo.getDbschema());
                }
                if (connInfo.getUsername() != null && !connInfo.getUsername().isEmpty()) {
                    connStr.append(" user=").append(connInfo.getUsername());
                }
                if (connInfo.getPassword() != null && !connInfo.getPassword().isEmpty()) {
                    connStr.append(" password=").append(connInfo.getPassword());
                }
                connStr.append("' AS ").append(alias).append(" (TYPE ").append(datasourceType).append(");");
                break;
            default:
                // 通用连接字符串构建
                if (connInfo.getHostname() != null && !connInfo.getHostname().isEmpty()) {
                    connStr.append("host=").append(connInfo.getHostname());
                }
                if (connInfo.getPort() != null && !connInfo.getPort().isEmpty()) {
                    connStr.append(" port=").append(connInfo.getPort());
                }
                if (connInfo.getDbschema() != null && !connInfo.getDbschema().isEmpty()) {
                    connStr.append(" database=").append(connInfo.getDbschema());
                }
                if (connInfo.getUsername() != null && !connInfo.getUsername().isEmpty()) {
                    connStr.append(" user=").append(connInfo.getUsername());
                }
                if (connInfo.getPassword() != null && !connInfo.getPassword().isEmpty()) {
                    connStr.append(" password=").append(connInfo.getPassword());
                }
                connStr.append("' AS ").append(alias).append(" (TYPE ").append(datasourceType).append(");");
                break;
        }

        return connStr.toString();
    }

    // 自动注入方法
    public void setDatasource(DatasourceInfo datasource) {
        this.datasource = datasource;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }
}