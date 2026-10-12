package com.data.datasource.provider;

import com.data.datasource.DataSourceConnection;
import com.data.datasource.DataSourceProvider;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.datasource.provider.jdbc.JdbcDataSourceConnection;
import com.data.datasource.provider.jdbc.JdbcMetadata;
import com.data.job.DatasourceInfo;
import com.data.metadata.DBType;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.TypeConverter;
import com.data.metadata.util.ConnectionPoolManager;
import com.data.metadata.util.DriverDownloader;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JDBC 数据源 Provider 基类，收敛所有关系型数据库共用的标准能力。
 *
 * <p>各具体数据源（MySQL、Oracle、PostgreSQL、DuckDB、达梦等）继承本类并实现
 * {@link #descriptor()} 声明自身类型元数据，即可复用：
 * <ul>
 *   <li>基于 {@link JdbcDataSourceConnection} 的连接与查询能力；</li>
 *   <li>按类型解析方言（DDL / 写 SQL 生成）；</li>
 *   <li>标准的库表元数据读取（{@link JdbcMetadata}），以及连通性测试查询。</li>
 * </ul>
 *
 * <p>扩展新数据源时，只需新增一个 Provider 类并实现 {@link #descriptor()}（可直接用
 * {@link #descriptorOf(DBType)} 复用 {@link DBType} 元数据，也可完全自定义），
 * 标注 {@code @DataSourceRegister} 即可被自动发现，无需改动任何中心化分支逻辑。
 *
 * <p>数据库特有的行为（如 Oracle 的 {@code SELECT 1 FROM DUAL}、MySQL 用 catalog 代替 schema、
 * DuckDB/Quack/DuckLake 的特殊连接与元数据）由各子类覆写相应方法实现。
 */
public abstract class JdbcDataSourceProvider implements DataSourceProvider {

    /**
     * 该数据源的描述元数据（类型标识、显示名、驱动、URL 模板等）。
     */
    protected abstract DataSourceTypeDescriptor descriptor();

    /**
     * 该 Provider 负责的数据源类型标识（如 {@code MYSQL}）。
     */
    public String typeName() {
        return descriptor().getType();
    }

    @Override
    public String providerId() {
        return typeName().toLowerCase();
    }

    @Override
    public List<DataSourceTypeDescriptor> descriptors() {
        return List.of(descriptor());
    }

    @Override
    public boolean supports(String type) {
        return type != null && typeName().equalsIgnoreCase(type);
    }

    @Override
    public boolean supportsUrl(String url) {
        if (url == null) {
            return false;
        }
        for (String prefix : urlPrefixes()) {
            if (prefix != null && !prefix.isEmpty() && url.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 该数据源 JDBC URL 的前缀集合，用于按连接地址识别数据源类型。
     * 默认由 {@link DataSourceTypeDescriptor#getJdbcUrlTemplate()} 推导（如 {@code jdbc:mysql:}）。
     */
    protected List<String> urlPrefixes() {
        String prefix = deriveUrlPrefix(descriptor().getJdbcUrlTemplate());
        return prefix == null ? List.of() : List.of(prefix);
    }

    @Override
    public DataSourceConnection connect(DatasourceInfo info) throws Exception {
        return new JdbcDataSourceConnection(this, info);
    }

    @Override
    public TypeConverter typeConverter(String type) {
        return DatabaseConverter.getConverter(type);
    }

    // ------------------------------------------------------------------ JDBC 标准能力

    /**
     * 打开与该数据源的物理连接。默认使用连接池；需要额外初始化（Quack / DuckLake）的子类覆写。
     */
    public Connection openConnection(DatasourceInfo info) throws SQLException {
        return ConnectionPoolManager.getConnection(info);
    }

    // ------------------------------------------------------------------ 连接池配置（数据库特定）

    /**
     * 解析用于建立连接池的 JDBC URL。默认原样返回；需要转换的子类覆写
     * （如 Quack 复用 DuckDB 驱动、DuckLake 生成本地挂载文件）。
     */
    public String resolveJdbcUrl(String url) {
        return url;
    }

    /**
     * 建立连接是否使用账号密码。默认使用；本地 DuckDB / DuckLake 等无账号数据的类型覆写为 {@code false}，
     * 避免以不同 user 配置打开同一 DuckDB 文件导致 {@code different configuration} 错误。
     */
    public boolean requiresCredentials() {
        return true;
    }

    /**
     * 连接池底层数据源的驱动属性（数据库特定性能参数）。默认无。
     */
    public Map<String, String> dataSourceProperties() {
        return Collections.emptyMap();
    }

    /**
     * 驱动类名（按版本）。默认取 {@link #descriptor()} 声明，驱动类名不随版本变化。
     */
    public String driverClassName(String version) {
        return descriptor().getDriverClassName();
    }

    /**
     * 驱动 jar 所在目录。默认按 {@link #descriptor()} 类型推导的 {@link DBType} 计算
     * （{@code drivers/<type>/default}）。返回 {@code null} 表示无需自定义类加载。
     */
    public File driverDirectory(String version) {
        DBType dbType = resolveDbType();
        return dbType == null ? null : DriverDownloader.getDriverDir(dbType, version);
    }

    /**
     * 若该类型支持 Maven 下载且本地缺失，则按需下载驱动。默认按 {@link #descriptor()} 类型推导。
     */
    public void ensureDriverAvailable(String version) {
        DBType dbType = resolveDbType();
        if (dbType == null || !dbType.isDownloadable() || DriverDownloader.isDriverInstalled(dbType, version)) {
            return;
        }
        try {
            DriverDownloader.downloadDriver(dbType, version);
        } catch (Exception e) {
            System.err.println(
                "Failed to auto-download driver for " + dbType + " version " + version + ": " + e.getMessage()
            );
        }
    }

    /**
     * 由 {@link #descriptor()} 的类型标识解析对应的 {@link DBType}，未登记时返回 {@code null}。
     */
    protected DBType resolveDbType() {
        return DBType.fromString(descriptor().getType());
    }

    /**
     * 连通性测试查询。默认 {@code SELECT 1}；Oracle 等需覆写。
     */
    public String getTestQuery() {
        return "SELECT 1";
    }

    /**
     * 该连接是否为 Quack 远程挂载连接。默认否，DuckDB 覆写。
     */
    public boolean isQuackConnection(Connection conn) {
        return false;
    }

    /**
     * 该连接是否为 DuckLake 挂载连接。默认否，DuckDB 覆写。
     */
    public boolean isDuckLakeConnection(Connection conn) {
        return false;
    }

    /**
     * 执行前对语句的方言包装。默认原样返回，Quack 覆写为 {@code quack.query(...)} 包装。
     */
    public String wrapQuery(String sql) {
        return sql;
    }

    /**
     * 列出 schema。默认读取标准 {@code DatabaseMetaData.getSchemas()}。
     */
    public List<String> getSchemas(Connection conn) throws SQLException {
        return JdbcMetadata.readSchemas(conn);
    }

    /**
     * 列出表。默认以 schema 作为 schema 参数读取普通表。
     */
    public List<TableMeta> getTables(Connection conn, String schema, Integer limit, String search) throws SQLException {
        return JdbcMetadata.readTableList(conn, null, schema, new String[] { "TABLE" }, limit, search, false);
    }

    /**
     * 读取表结构。默认以 {@code (schema, null, table)} 组合读取。
     */
    public TableMeta getTableMeta(Connection conn, String schema, String table) throws SQLException {
        TableMeta tableMeta = JdbcMetadata.readTableMeta(conn, schema, null, table);
        tableMeta.setDbType(typeName());
        return tableMeta;
    }

    /**
     * 读取表详细信息（结构 + 注释 + 索引）。默认以 {@code (schema, null, table)} 组合读取。
     */
    public TableMeta getTableDetail(Connection conn, String schema, String table) throws SQLException {
        TableMeta tableMeta = JdbcMetadata.readTableMeta(conn, schema, null, table);
        tableMeta.setDbType(typeName());
        tableMeta.setCatalog(schema);
        tableMeta.setComment(JdbcMetadata.getTableComment(conn, schema, null, table));
        tableMeta.setIndexes(JdbcMetadata.getTableIndexes(conn, schema, null, table));
        return tableMeta;
    }

    /**
     * 判断表是否存在。默认以 {@code (schema, null, table)} 组合查询。
     */
    public boolean tableExists(Connection conn, String schema, String table) throws SQLException {
        return tableExists(conn, schema, null, table);
    }

    /**
     * 以指定 catalog/schema 组合判断表是否存在。
     */
    protected static boolean tableExists(Connection conn, String catalog, String schema, String table) {
        try (ResultSet rs = conn.getMetaData().getTables(catalog, schema, table, null)) {
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException("表存在性检查失败", e);
        }
    }

    /**
     * 由 {@link DBType} 元数据构建类型描述，供内置 JDBC Provider 复用。
     */
    public static DataSourceTypeDescriptor descriptorOf(DBType dbType) {
        return new DataSourceTypeDescriptor()
            .setType(dbType.name())
            .setDisplayName(dbType.getDisplayName())
            .setJdbc(true)
            .setSupportedVersions(dbType.getSupportedVersions())
            .setDriverClassName(dbType.getDriverClassName())
            .setJdbcUrlTemplate(dbType.getJdbcUrlTemplate())
            .setDownloadable(dbType.isDownloadable());
    }

    /**
     * 由 JDBC URL 模板推导前缀：取前两个冒号，如 {@code jdbc:mysql://... -> jdbc:mysql:}、{@code ducklake:... -> ducklake:}。
     */
    public static String deriveUrlPrefix(String jdbcUrlTemplate) {
        if (jdbcUrlTemplate == null) {
            return null;
        }
        int first = jdbcUrlTemplate.indexOf(':');
        if (first < 0) {
            return null;
        }
        int second = jdbcUrlTemplate.indexOf(':', first + 1);
        return second < 0 ? jdbcUrlTemplate.substring(0, first + 1) : jdbcUrlTemplate.substring(0, second + 1);
    }
}
