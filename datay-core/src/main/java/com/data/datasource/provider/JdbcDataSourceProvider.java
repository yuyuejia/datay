package com.data.datasource.provider;

import com.alibaba.fastjson2.JSONArray;
import com.data.datasource.DataSourceConnection;
import com.data.datasource.DataSourceProvider;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.datasource.provider.jdbc.JdbcDataSourceConnection;
import com.data.datasource.provider.jdbc.JdbcMetadata;
import com.data.job.DatasourceInfo;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DBType;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.TypeConverter;
import com.data.metadata.util.ConnectionPoolManager;
import com.data.metadata.util.DriverStore;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
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
 *   <li>数据库特定的表写入能力（{@link #supportsTableWrite(DatasourceInfo, TableMeta, String)} /
 *       {@link #writeTable(DatasourceInfo, Connection, TableMeta, JSONArray, String, Map)}）、catalog/表名限定与清表方式；</li>
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

    /**
     * 写入前校验目标表是否满足当前数据源的写入约束。默认无校验。
     *
     * <p>例如 TDengine 超级表不能直接执行普通 INSERT，需要提前给出明确错误。</p>
     */
    public void validateWriteTarget(DatasourceInfo info, Connection connection, TableMeta table, String eventType)
        throws SQLException {}

    /**
     * 自动建表前的目标命名空间准备。默认无操作。
     *
     * <p>部分数据源要求目标 schema / namespace 预先存在（如 Iceberg REST Catalog），
     * 或需要切换到正确的 database，由对应 Provider 覆写。</p>
     */
    public void prepareTableCreate(DatasourceInfo info, Connection connection, String catalog, String schema)
        throws SQLException {}

    /**
     * 当前数据源是否对该事件类型提供专用表写入能力。
     *
     * <p>默认 {@code false}，由 ETL Sink 使用通用逐行 JDBC 写入；数据库特有的高效写入方式
     * （如 DuckDB Appender / 集合写入）由对应 Provider 覆写。</p>
     */
    public boolean supportsTableWrite(DatasourceInfo info, TableMeta table, String eventType) {
        return false;
    }

    /**
     * 执行数据库特有的表写入。仅当 {@link #supportsTableWrite(DatasourceInfo, TableMeta, String)}
     * 返回 {@code true} 时由 ETL Sink 调用，默认实现直接拒绝。
     *
     * <p>返回 {@code null} 表示专用写入方式本次不可用且尚未写入任何数据（例如 Appender 创建失败），
     * 调用方应回退到通用 JDBC 写入；若已开始写入后失败，必须抛出异常而不是返回 {@code null}。</p>
     *
     * @param info          目标数据源配置
     * @param connection    目标库连接
     * @param table         目标表元数据
     * @param records       待写入记录
     * @param eventType     INSERT / UPDATE / DELETE / APPEND / OVERWRITE 等写入模式
     * @param columnMapping 目标字段 -> 源字段映射，可为 {@code null}
     * @return 写入方式描述，用于调用方日志；{@code null} 表示回退通用 JDBC 写入
     */
    public String writeTable(DatasourceInfo info, Connection connection, TableMeta table, JSONArray records,
                             String eventType, Map<String, String> columnMapping) throws SQLException {
        throw new UnsupportedOperationException("当前数据源未提供专用表写入能力: " + typeName());
    }

    /**
     * 规范化写入目标表名。默认原样返回；需要遵循数据库命名习惯的数据源覆写
     * （例如 MySQL 默认使用小写表名，保证自动建表 DDL 与后续 DML 使用同一名称）。
     */
    public String normalizeTargetTableName(DatasourceInfo info, String tableName) {
        return tableName;
    }

    /**
     * 新建表 / 写入表时需要限定的 catalog。默认 {@code null}，表示使用 schema.table；
     * Quack、DuckLake 等需要显式 catalog 的数据源覆写。
     */
    public String targetCatalog(DatasourceInfo info) {
        return null;
    }

    /**
     * 生成 DDL / 写入使用的表名。默认 {@code schema.table}；需要带 catalog 的数据源覆写。
     */
    public String qualifiedTableName(DatasourceInfo info, String schema, String table) {
        return schema == null || schema.isBlank() ? table : schema + "." + table;
    }

    /**
     * 读取源端结果集中的列值。默认直接使用 {@link ResultSet#getObject(int)}；
     * 数据库驱动返回专有类型或存在时区差异时由对应 Provider 覆写
     * （例如 SQL Server 的 {@code DATE/TIME/TIMESTAMP/DATETIMEOFFSET}）。
     *
     * <p>该钩子用于全量快照、CDC 等需要按源库驱动特性读取值的场景，避免在通用组件中
     * 按数据库类型写分支。</p>
     *
     * @param rs       结果集
     * @param index    列下标（1 开始）
     * @param sqlType  JDBC 类型
     * @param typeName 数据库类型名
     * @param column   列元数据，可为 {@code null}
     */
    public Object readSourceValue(ResultSet rs, int index, int sqlType, String typeName, ColumnMeta column)
        throws SQLException {
        return rs.getObject(index);
    }

    /**
     * 归一化从源表读取到的列值，用于全量同步写入 FlowFile / 目标库。默认原样返回；
     * 驱动返回值需要转成通用结构（如 ClickHouse 数组、Map、无符号整数）的数据源覆写。
     *
     * @param column 列元数据，可为 {@code null}
     * @param value  JDBC 驱动返回的原始值
     */
    public Object normalizeReadValue(ColumnMeta column, Object value) {
        return value;
    }

    /**
     * overwrite 模式清空表时是否使用 {@code TRUNCATE}。默认 {@code false}（使用 DELETE）；
     * Doris、StarRocks 等 OLAP 引擎覆写为 {@code true}。
     */
    public boolean useTruncateForOverwrite() {
        return false;
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
        return dbType == null ? null : DriverStore.getDriverDir(dbType);
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
     * 执行前对语句做数据源方言准备。
     *
     * <p>默认原样返回；需要远端执行包装等特殊处理的数据源由对应 Provider 覆写，
     * 调用方无需感知具体是哪种数据库。
     */
    public String prepareStatement(Connection conn, String sql) {
        return sql;
    }

    /**
     * 给查询 SQL 追加行数限制，用于调试模式采样，避免从源库读取全量数据。
     *
     * <p>默认使用通用的 {@code LIMIT n} 语法；不支持该语法的数据库由对应 Provider 覆写
     * （如 Oracle 使用 {@code ROWNUM}，SQL Server / HANA 使用 {@code TOP}）。</p>
     *
     * @param sql   原始查询
     * @param limit 最大返回行数，{@code <= 0} 时原样返回
     * @return 追加行数限制后的查询
     */
    public String applyRowLimit(String sql, int limit) {
        if (sql == null || limit <= 0) {
            return sql;
        }
        return sql + " LIMIT " + limit;
    }

    /**
     * 使用 {@code SELECT TOP n * FROM (sql) t} 包装查询以限制行数。
     */
    protected String wrapWithTopRowLimit(String sql, int limit) {
        if (sql == null || limit <= 0) {
            return sql;
        }
        return "SELECT TOP " + limit + " * FROM (" + sql + ") t";
    }

    /**
     * 使用 Oracle 的 {@code ROWNUM} 包装查询以限制行数。
     */
    protected String wrapWithRownumRowLimit(String sql, int limit) {
        if (sql == null || limit <= 0) {
            return sql;
        }
        return "SELECT * FROM (" + sql + ") t WHERE ROWNUM <= " + limit;
    }

    /**
     * 列出 catalog。默认无 catalog 概念，返回空列表；DuckDB 等支持挂载库的数据源可覆写。
     */
    public List<String> getCatalogs(Connection conn) throws SQLException {
        return Collections.emptyList();
    }

    /**
     * 列出 schema。默认读取标准 {@code DatabaseMetaData.getSchemas()}。
     */
    public List<String> getSchemas(Connection conn) throws SQLException {
        return JdbcMetadata.readSchemas(conn);
    }

    /**
     * 列出指定 catalog 下的 schema。默认忽略 catalog。
     */
    public List<String> getSchemas(Connection conn, String catalog) throws SQLException {
        return getSchemas(conn);
    }

    /**
     * 列出表。默认以 schema 作为 schema 参数读取普通表和视图。
     */
    public List<TableMeta> getTables(Connection conn, String schema, Integer limit, String search) throws SQLException {
        return JdbcMetadata.readTableList(conn, null, schema, new String[] { "TABLE", "VIEW" }, limit, search, false);
    }

    /**
     * 列出指定 catalog + schema 下的表。默认忽略 catalog。
     */
    public List<TableMeta> getTables(Connection conn, String catalog, String schema, Integer limit, String search) throws SQLException {
        return getTables(conn, schema, limit, search);
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
     * 读取指定 catalog 下的表结构。默认忽略 catalog。
     */
    public TableMeta getTableMeta(Connection conn, String catalog, String schema, String table) throws SQLException {
        return getTableMeta(conn, schema, table);
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
     * 读取指定 catalog 下表的详细信息。默认忽略 catalog。
     */
    public TableMeta getTableDetail(Connection conn, String catalog, String schema, String table) throws SQLException {
        return getTableDetail(conn, schema, table);
    }

    /**
     * 获取表健康信息。默认不支持，支持 Iceberg 等表格式的数据源可覆写。
     */
    public Map<String, Object> getTableHealth(Connection conn, String catalog, String schema, String table)
        throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supported", false);
        result.put("message", "当前数据源不支持表健康监控");
        return result;
    }

    /**
     * 获取表可用的运维操作。默认不支持。
     */
    public List<Map<String, Object>> getTableMaintenanceActions(
        Connection conn,
        String catalog,
        String schema,
        String table
    ) throws SQLException {
        return List.of();
    }

    /**
     * 执行表运维操作。默认不支持。
     */
    public Map<String, Object> executeTableMaintenance(
        Connection conn,
        String catalog,
        String schema,
        String table,
        String action,
        Map<String, Object> params
    ) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", "当前数据源不支持表运维操作");
        return result;
    }

    /**
     * 获取 Catalog 级运维操作。默认不支持。
     */
    public List<Map<String, Object>> getCatalogMaintenanceActions(Connection conn) throws SQLException {
        return List.of();
    }

    /**
     * 执行 Catalog 级运维操作。默认不支持。
     */
    public Map<String, Object> executeCatalogMaintenance(
        Connection conn,
        String action,
        Map<String, Object> params
    ) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", "当前数据源不支持 Catalog 级运维操作");
        return result;
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
            .setDriverPreinstalled(dbType.isDriverPreinstalled());
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
