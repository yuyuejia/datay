package com.data.metadata.util;

import cn.hutool.core.collection.ArrayIter;
import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.func.Func1;
import cn.hutool.db.DbUtil;
import cn.hutool.db.StatementUtil;
import cn.hutool.db.handler.BeanListHandler;
import cn.hutool.db.handler.RsHandler;
import cn.hutool.db.sql.NamedSql;
import cn.hutool.db.sql.SqlBuilder;
import cn.hutool.db.sql.SqlExecutor;
import cn.hutool.crypto.digest.DigestUtil;
import com.data.datasource.DataSourceProvider;
import com.data.datasource.DataSourceProviderFactory;
import com.data.datasource.provider.JdbcDataSourceProvider;
import com.data.datasource.provider.jdbc.JdbcMetadata;
import com.data.job.DatasourceInfo;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DBType;
import com.data.metadata.IndexMeta;
import com.data.metadata.TableMeta;
import com.zaxxer.hikari.pool.HikariProxyConnection;
import com.zaxxer.hikari.pool.ProxyConnection;
import org.duckdb.DuckDBAppender;

import java.lang.reflect.Field;
import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL执行器，全部为静态方法，执行查询或非查询的SQL语句<br>
 * 此方法为JDBC的简单封装，与数据库类型无关
 *
 */
public class DBUtils {

    private static final String URL = "jdbc:mysql://127.0.0.1:3306/?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai";
    private static final String USER = "root";
    private static final String PASSWORD = "1234qwer";

    static {
        try {
            // 手动加载驱动类（可选）
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    //    public static Connection getConnection() throws SQLException {
    //        return DriverManager.getConnection(URL, USER, PASSWORD);
    //    }
    //
    //    /**
    //     * 获取数据库连接
    //     * @return 数据库连接对象
    //     * @throws SQLException 如果获取连接时出现 SQL 异常
    //     */
    //    public static Connection getConnection(DataSourceDTO dataSource) throws SQLException {
    //        Connection conn = DriverManager.getConnection(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
    //        // 设置流式读取参数
    //        //        if (url.contains("mysql:")) {
    //        //            conn.setAutoCommit(false);
    //        //            conn.createStatement().execute("SET net_write_timeout=120");
    //        //        }
    //        return conn;
    //    }
    //
    //    /**
    //     * 获取数据库连接
    //     * @return 数据库连接对象
    //     * @throws SQLException 如果获取连接时出现 SQL 异常
    //     */
    //    public static Connection getConnection(String url, String user, String password) throws SQLException {
    //        Connection conn = DriverManager.getConnection(url, user, password);
    //        // 设置流式读取参数
    //        //        if (url.contains("mysql:")) {
    //        //            conn.setAutoCommit(false);
    //        //            conn.createStatement().execute("SET net_write_timeout=120");
    //        //        }
    //        return conn;
    //    }
    //
    //    public static Connection getConnection(DatasourceInfo datasourceInfo) throws SQLException {
    //        Connection conn = DriverManager.getConnection(datasourceInfo.getUrl(), datasourceInfo.getUsername(), datasourceInfo.getPassword());
    //        return conn;
    //    }

    /**
     * 获取默认数据库连接（使用连接池）
     */
    public static Connection getConnection() throws SQLException {
        return ConnectionPoolManager.getConnection(URL, USER, PASSWORD);
    }

    /**
     * 获取数据库连接（使用连接池）
     * @return 数据库连接对象
     * @throws SQLException 如果获取连接时出现 SQL 异常
     */
    public static Connection getConnection(String url, String user, String password) throws SQLException {
        return ConnectionPoolManager.getConnection(url, user, password);
    }

    /**
     * 获取数据库连接（使用连接池）。
     *
     * <p>连接建立及 DuckLake / Quack 等特殊初始化由对应数据源 Provider 负责。
     */
    public static Connection getConnection(DatasourceInfo datasourceInfo) throws SQLException {
        DataSourceProvider provider = datasourceInfo.getType() == null ? null : DataSourceProviderFactory.provider(datasourceInfo.getType());
        if (provider instanceof JdbcDataSourceProvider jdbcProvider) {
            return jdbcProvider.openConnection(datasourceInfo);
        }
        return ConnectionPoolManager.getConnection(datasourceInfo);
    }

    public static final String DUCKLAKE_CATALOG = "ducklake";
    public static final String QUACK_CATALOG = "quack";

    // DuckLake 本地挂载文件目录（相对于工作目录）
    private static final String DUCKLAKE_LOCAL_DIR = "log/ducklake";

    /**
     * 计算 DuckLake 元数据位置（用于生成本地挂载文件与串行锁）。
     *
     * <p>DuckLake 元数据（文件或 postgres/mysql/sqlite 元数据库）在同一进程内只能被挂载一次，
     * 因此本地挂载文件必须以元数据位置为唯一键，而不能包含 s3 等扩展参数，
     * 否则同一元数据会生成多个 DuckDB 实例并相互冲突（Unique file handle conflict）。
     */
    public static String resolveDuckLakeMetadataLocation(DatasourceInfo datasourceInfo) {
        String meta = datasourceInfo.getUrl();
        if (meta == null) {
            return "";
        }
        if (meta.startsWith("ducklake:")) {
            meta = meta.substring("ducklake:".length());
        }
        String lower = meta.toLowerCase(Locale.ROOT);
        boolean remoteMetadata =
            lower.startsWith("postgres:") ||
            lower.startsWith("mysql:") ||
            lower.startsWith("sqlite:") ||
            lower.startsWith("duckdb:");
        if (!remoteMetadata) {
            File file = new File(meta);
            if (!file.isAbsolute()) {
                file = new File(System.getProperty("user.dir"), meta);
            }
            meta = file.toPath().normalize().toAbsolutePath().toString();
        }
        return meta;
    }

    /**
     * 根据 DuckLake 元数据位置计算其本地 DuckDB 文件的 JDBC URL。
     *
     * <p>不同的 DuckLake 使用不同的本地 DuckDB 文件；同一个 DuckLake 元数据始终复用同一文件，
     * 避免同一元数据被重复挂载时触发 DuckDB 的 "Unique file handle conflict"。
     */
    public static String resolveDuckLakeJdbcUrl(DatasourceInfo datasourceInfo) {
        String hash = DigestUtil.sha256Hex(resolveDuckLakeMetadataLocation(datasourceInfo)).substring(0, 16);

        File dir = new File(System.getProperty("user.dir"), DUCKLAKE_LOCAL_DIR);
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new IllegalStateException("无法创建 DuckLake 本地挂载目录: " + dir.getAbsolutePath());
        }
        File dbFile = new File(dir, "ducklake_" + hash + ".duckdb");
        return "jdbc:duckdb:" + dbFile.getAbsolutePath();
    }

    /**
     * 判断连接是否挂载了 Quack 远程 catalog。
     */
    public static boolean isQuackConnection(Connection conn) {
        try {
            try (ResultSet rs = conn.getMetaData().getCatalogs()) {
                while (rs.next()) {
                    if (QUACK_CATALOG.equals(rs.getString("TABLE_CAT"))) {
                        return true;
                    }
                }
            }
        } catch (SQLException ignored) {
            // ignore
        }
        return false;
    }

    /**
     * 判断连接是否挂载了 DuckLake catalog。
     */
    public static boolean isDuckLakeConnection(Connection conn) {
        try {
            try (ResultSet rs = conn.getMetaData().getCatalogs()) {
                while (rs.next()) {
                    if (DUCKLAKE_CATALOG.equals(rs.getString("TABLE_CAT"))) {
                        return true;
                    }
                }
            }
        } catch (SQLException ignored) {
            // ignore
        }
        return false;
    }

    /**
     * 将 SQL 包装为 Quack 远程查询。
     *
     * <p>本地把远端挂载为 catalog {@code quack}，但 Quack 1.5.3 对
     * {@code "quack"."schema"."table"} 形式的表引用有缺陷（会丢失 schema）。
     * 这里去掉本地 catalog 限定符并交给 {@code quack.query()} 在远端执行。
     */
    public static String wrapQuackQuery(String sql) {
        String remoteSql = sql.replaceAll("(?i)(?<![A-Za-z0-9_])\"?quack\"?\\s*\\.\\s*", "");
        String tag = "$quack$";
        if (remoteSql.contains(tag)) {
            tag = "$quack_query$";
        }
        if (remoteSql.contains(tag)) {
            return "SELECT * FROM quack.query('" + remoteSql.replace("'", "''") + "')";
        }
        return "SELECT * FROM quack.query(" + tag + remoteSql + tag + ")";
    }

    /**
     * 在 Quack 远端执行非查询语句（DDL/DML）。
     *
     * @return 远端返回的结果行数
     */
    public static int executeQuack(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(wrapQuackQuery(sql))) {
            int rows = 0;
            while (rs.next()) {
                rows++;
            }
            return rows;
        }
    }

    /**
     * 获取数据库连接（不使用连接池，保持向后兼容性）
     * @return 数据库连接对象
     * @throws SQLException 如果获取连接时出现 SQL 异常
     * @deprecated 建议使用连接池版本，此方法仅用于向后兼容
     */
    @Deprecated
    public static Connection getDirectConnection(String url, String user, String password) throws SQLException {
        Connection conn = DriverManager.getConnection(url, user, password);
        return conn;
    }

    /**
     * 反射获取 HikariProxyConnection 中的底层真实连接
     */
    public static Connection getRawConnection(HikariProxyConnection proxyConn) throws SQLException {
        try {
            // Hikari 5.1 中，HikariProxyConnection 的底层连接存储在 "delegate" 私有字段中
            Field delegateField = ProxyConnection.class.getDeclaredField("delegate");
            delegateField.setAccessible(true); // 允许访问私有字段
            return (Connection) delegateField.get(proxyConn);
        } catch (NoSuchFieldException e) {
            throw new SQLException("Hikari 版本不兼容，未找到 delegate 字段", e);
        } catch (IllegalAccessException e) {
            throw new SQLException("反射访问 delegate 字段失败", e);
        }
    }

    // 在类中添加日期时间处理方法
    public static LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }

        String trimmed = dateTimeStr.trim();

        // 定义多种可能的日期时间格式
        DateTimeFormatter[] formatters = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        };

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (Exception e) {
                continue;
            }
        }

        throw new IllegalArgumentException("无法解析日期时间格式: " + trimmed);
    }

    public static void appendValue(DuckDBAppender appender, String fieldType, Object value) throws SQLException {
        if (value == null) {
            appender.appendNull();
            return;
        }
        String type = fieldType == null ? "" : fieldType.toUpperCase().trim();

        try {
            if ("BIGINT".equals(type) || type.endsWith("BIGINT")) {
                appender.append(toLong(value));
            } else if ("TINYINT".equals(type) || "UTINYINT".equals(type) || type.endsWith("TINYINT")) {
                appender.append(toByte(value));
            } else if ("SMALLINT".equals(type) || "USMALLINT".equals(type) || type.endsWith("SMALLINT")) {
                appender.append(toShort(value));
            } else if ("INTEGER".equals(type) || "INT".equals(type) || type.endsWith("INTEGER")) {
                appender.append(toInt(value));
            } else if ("HUGEINT".equals(type)) {
                appender.append(toLong(value));
            } else if ("DOUBLE".equals(type) || type.endsWith("DOUBLE")) {
                appender.append(toDouble(value));
            } else if ("FLOAT".equals(type) || "REAL".equals(type) || type.endsWith("FLOAT")) {
                appender.append(toFloat(value));
            } else if (type.startsWith("DECIMAL") || type.startsWith("NUMERIC")) {
                appender.append(toBigDecimal(value));
            } else if ("BOOLEAN".equals(type) || type.endsWith("BOOLEAN")) {
                appender.append(toBoolean(value));
            } else if ("BIT".equals(type)) {
                appender.append((byte) (toBoolean(value) ? 1 : 0));
            } else if ("BLOB".equals(type) || "BINARY".equals(type) || "VARBINARY".equals(type)) {
                appender.append(toByteArray(value));
            } else if ("DATE".equals(type)) {
                appender.append(toLocalDate(value));
            } else if ("TIME".equals(type)) {
                appender.append(toLocalTime(value));
            } else if ("DATETIME".equals(type) || "TIMESTAMP".equals(type) || type.endsWith("TIMESTAMP")) {
                appender.append(toLocalDateTime(value));
            } else {
                appender.append(toStringValue(value));
            }
        } catch (Exception e) {
            appender.append(toStringValue(value));
        }
    }

    /**
     * 转换为字符串：MySQL Binlog 对 TEXT 等字符串类型可能返回 {@code byte[]}，按 UTF-8 解码。
     */
    private static String toStringValue(Object value) {
        if (value instanceof byte[]) {
            return new String((byte[]) value, java.nio.charset.StandardCharsets.UTF_8);
        }
        return value.toString();
    }

    private static boolean isTextType(String fieldType) {
        String type = fieldType == null ? "" : fieldType.toUpperCase().trim();
        return (
            type.equals("VARCHAR") ||
            type.equals("CHAR") ||
            type.equals("TEXT") ||
            type.endsWith("TEXT") ||
            type.equals("STRING") ||
            type.equals("CLOB") ||
            type.equals("JSON") ||
            type.equals("UUID")
        );
    }

    /**
     * 转换为 LocalDateTime。兼容 MySQL Binlog 采集返回的 {@link java.util.Date}
     * （其内部按 UTC 记录源库无时区的日期时间，需按 UTC 还原墙上时间）。
     */
    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime();
        }
        if (value instanceof java.util.Date) {
            return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(((java.util.Date) value).getTime()), ZoneOffset.UTC);
        }
        return parseDateTime(value.toString());
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        if (value instanceof java.util.Date) {
            return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(((java.util.Date) value).getTime()), ZoneOffset.UTC).toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    private static LocalTime toLocalTime(Object value) {
        if (value instanceof LocalTime) {
            return (LocalTime) value;
        }
        if (value instanceof java.sql.Time) {
            // MySQL Binlog 的 TIME 以 UTC 记录墙上时间，需按 UTC 还原，避免时区偏移。
            // 注意：java.sql.Time.toInstant() 会抛 UnsupportedOperationException，故用 epoch 毫秒。
            return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(((java.util.Date) value).getTime()), ZoneOffset.UTC).toLocalTime();
        }
        if (value instanceof java.util.Date) {
            return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(((java.util.Date) value).getTime()), ZoneOffset.UTC).toLocalTime();
        }
        return LocalTime.parse(value.toString());
    }

    /**
     * 把来自 CDC 等场景的原始值规范化为 JDBC 可写入的目标类型。
     * <p>主要处理 MySQL Binlog 采集返回的 {@link java.util.Date}（按 UTC 记录无时区时间），
     * 依据目标字段类型转换为 DATE/TIME/TIMESTAMP 对应的 Java 时间类型。
     */
    public static Object normalizeParameter(String fieldType, Object value) {
        if (value == null) {
            return null;
        }
        String type = fieldType == null ? "" : fieldType.toUpperCase().trim();
        if ("BIT".equals(type)) {
            return toBitValue(value);
        }
        if (value instanceof byte[]) {
            // MySQL Binlog 对 TEXT 等字符串类型返回 byte[]，文本目标列按 UTF-8 解码
            if (isTextType(fieldType)) {
                return new String((byte[]) value, java.nio.charset.StandardCharsets.UTF_8);
            }
            return value;
        }
        if (!(value instanceof java.util.Date)) {
            return value;
        }
        if ("DATE".equals(type)) {
            return toLocalDate(value);
        }
        if ("TIME".equals(type)) {
            return toLocalTime(value);
        }
        if ("DATETIME".equals(type) || "TIMESTAMP".equals(type) || type.endsWith("TIMESTAMP")) {
            return toLocalDateTime(value);
        }
        return value;
    }

    private static long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? 1L : 0L;
        }
        return Long.parseLong(value.toString().trim());
    }

    private static int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? 1 : 0;
        }
        return Integer.parseInt(value.toString().trim());
    }

    private static short toShort(Object value) {
        if (value instanceof Number) {
            return ((Number) value).shortValue();
        }
        if (value instanceof Boolean) {
            return (short) (((Boolean) value) ? 1 : 0);
        }
        return Short.parseShort(value.toString().trim());
    }

    private static byte toByte(Object value) {
        if (value instanceof Number) {
            return ((Number) value).byteValue();
        }
        if (value instanceof Boolean) {
            return (byte) (((Boolean) value) ? 1 : 0);
        }
        return Byte.parseByte(value.toString().trim());
    }

    private static double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? 1.0d : 0.0d;
        }
        return Double.parseDouble(value.toString().trim());
    }

    private static float toFloat(Object value) {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? 1.0f : 0.0f;
        }
        return Float.parseFloat(value.toString().trim());
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        return new BigDecimal(value.toString().trim());
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0;
        }
        if (value instanceof java.util.BitSet) {
            java.util.BitSet bits = (java.util.BitSet) value;
            return bits.length() > 0 && bits.get(0);
        }
        String s = value.toString().trim().toLowerCase();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }

    private static byte toBitValue(Object value) {
        if (value instanceof java.util.BitSet) {
            java.util.BitSet bits = (java.util.BitSet) value;
            return (byte) (bits.length() > 0 && bits.get(0) ? 1 : 0);
        }
        return (byte) (toBoolean(value) ? 1 : 0);
    }

    private static byte[] toByteArray(Object value) {
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        if (value instanceof String) {
            return ((String) value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 关闭连接池
     */
    public static void closePool(String url, String user) {
        ConnectionPoolManager.closePool(url, user);
    }

    /**
     * 关闭所有连接池
     */
    public static void closeAllPools() {
        ConnectionPoolManager.closeAllPools();
    }

    /**
     * 测试数据源连接（通过URL、用户名、密码）
     * @param url 数据库URL
     * @param username 用户名
     * @param password 密码
     * @return 连接测试结果，true表示连接成功，false表示连接失败
     */
    public static boolean testConnection(String url, String username, String password) {
        try (Connection connection = getConnection(url, username, password)) {
            // 执行一个简单的查询来验证连接
            try (Statement statement = connection.createStatement()) {
                String testQuery = getTestQuery(url);
                try (ResultSet resultSet = statement.executeQuery(testQuery)) {
                    return true; // 查询执行成功，连接有效
                }
            }
        } catch (SQLException e) {
            //            LOG.error("数据源连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 测试数据源连接（支持 DuckLake / Quack 等需要额外初始化步骤的数据源）
     * @param datasourceInfo 数据源信息
     * @return 连接测试结果，true表示连接成功，false表示连接失败
     */
    public static boolean testConnection(DatasourceInfo datasourceInfo) {
        try (Connection connection = getConnection(datasourceInfo)) {
            try (Statement statement = connection.createStatement()) {
                String testQuery = getTestQuery(datasourceInfo.getUrl());
                try (ResultSet resultSet = statement.executeQuery(testQuery)) {
                    return true;
                }
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public static Statement createStreamStatement(String dbType, Connection conn) throws SQLException {
        Statement statement = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        if (DBType.MYSQL.name().equalsIgnoreCase(dbType)) {
            statement.setFetchSize(Integer.MIN_VALUE);
        } else if (DBType.ORACLE.name().equalsIgnoreCase(dbType)) {
            statement.setFetchSize(1000);
        } else if (DBType.SQLSERVER.name().equalsIgnoreCase(dbType)) {
            statement.setFetchSize(1000);
        } else if (DBType.POSTGRESQL.name().equalsIgnoreCase(dbType)) {
            statement.setFetchSize(1);
        } else {
            statement.setFetchSize(1000);
        }
        return statement;
    }

    /**
     * 根据数据库URL获取测试查询语句（由对应数据源 Provider 提供）。
     * @param url 数据库URL
     * @return 适合该数据库的测试查询语句
     */
    public static String getTestQuery(String url) {
        JdbcDataSourceProvider provider = resolveJdbcProvider(url);
        return provider != null ? provider.getTestQuery() : "SELECT 1";
    }

    //获取schema信息（由对应数据源 Provider 提供）
    public static List<String> getSchemas(Connection conn) throws SQLException {
        JdbcDataSourceProvider provider = resolveJdbcProvider(conn);
        return provider != null ? provider.getSchemas(conn) : JdbcMetadata.readSchemas(conn);
    }

    //获取catalog信息
    public static List<String> getCatalogs(Connection conn) throws SQLException {
        return JdbcMetadata.getCatalogs(conn);
    }

    //获取表列表
    public static List<TableMeta> getTableList(Connection conn, String schema) throws SQLException {
        return getTableList(conn, schema, null, null);
    }

    public static List<TableMeta> getTableList(Connection conn, String schema, Integer limit) throws SQLException {
        return getTableList(conn, schema, limit, null);
    }

    public static List<TableMeta> getTableList(Connection conn, String schema, Integer limit, String search) throws SQLException {
        JdbcDataSourceProvider provider = resolveJdbcProvider(conn);
        if (provider != null) {
            return provider.getTables(conn, schema, limit, search);
        }
        return JdbcMetadata.readTableList(conn, null, schema, new String[] { "TABLE" }, limit, search, false);
    }

    public static TableMeta getTableMetaData(Connection conn, String schema, String table) throws SQLException {
        JdbcDataSourceProvider provider = resolveJdbcProvider(conn);
        if (provider != null) {
            return provider.getTableMeta(conn, schema, table);
        }
        return JdbcMetadata.readTableMeta(conn, schema, null, table);
    }

    /**
     * 从ResultSet元数据构建TableMeta
     */
    public static TableMeta getTableMetaFromResultSet(ResultSetMetaData metaData) throws SQLException {
        TableMeta tableMeta = new TableMeta();
        int columnCount = metaData.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnName(i);
            String columnType = metaData.getColumnTypeName(i);
            int columnSize = metaData.getPrecision(i);
            int scale = metaData.getScale(i);

            ColumnMeta column = new ColumnMeta(columnName, columnType);
            column.setLength(columnSize);
            column.setScale(scale);
            column.setNullable(metaData.isNullable(i) == ResultSetMetaData.columnNullable);

            tableMeta.addColumn(column);
        }

        return tableMeta;
    }

    public static TableMeta getTableMetaData(Connection conn, String catalog, String schema, String table) throws SQLException {
        return JdbcMetadata.readTableMeta(conn, catalog, schema, table);
    }

    /**
     * 获取表详细信息：字段、索引、表注释等（由对应数据源 Provider 提供）。
     */
    public static TableMeta getTableDetail(Connection conn, String schema, String table) throws SQLException {
        JdbcDataSourceProvider provider = resolveJdbcProvider(conn);
        if (provider != null) {
            return provider.getTableDetail(conn, schema, table);
        }
        TableMeta tableMeta = JdbcMetadata.readTableMeta(conn, schema, null, table);
        tableMeta.setCatalog(schema);
        tableMeta.setComment(JdbcMetadata.getTableComment(conn, schema, null, table));
        tableMeta.setIndexes(JdbcMetadata.getTableIndexes(conn, schema, null, table));
        return tableMeta;
    }

    /**
     * 获取表注释。
     */
    public static String getTableComment(Connection conn, String catalog, String schema, String table) {
        return JdbcMetadata.getTableComment(conn, catalog, schema, table);
    }

    /**
     * 获取表索引信息。
     */
    public static List<IndexMeta> getTableIndexes(Connection conn, String catalog, String schema, String table) throws SQLException {
        return JdbcMetadata.getTableIndexes(conn, catalog, schema, table);
    }

    // 根据 JDBC URL 判断数据库类型
    public static String getDBType(String jdbcUrl) {
        if (jdbcUrl.startsWith("jdbc:mysql:")) {
            return DBType.MYSQL.toString();
        } else if (jdbcUrl.startsWith("jdbc:oracle:")) {
            return DBType.ORACLE.toString();
        } else if (jdbcUrl.startsWith("jdbc:postgresql:")) {
            return DBType.POSTGRESQL.toString();
        } else if (jdbcUrl.startsWith("jdbc:sqlserver:")) {
            return DBType.SQLSERVER.toString();
        } else if (jdbcUrl.startsWith("jdbc:dm:")) {
            return DBType.DM.toString();
        } else if (jdbcUrl.startsWith("jdbc:duckdb:") || jdbcUrl.startsWith("quack:")) {
            return DBType.DUCKDB.toString();
        } else if (jdbcUrl.startsWith("ducklake:")) {
            return DBType.DUCKLAKE.toString();
        } else if (jdbcUrl.startsWith("jdbc:sqlite:")) {
            return DBType.SQLITE.toString();
        }
        return "Unknown";
    }

    /**
     * 根据 JDBC URL 判断数据库类型，返回 DBType 枚举
     */
    public static DBType getDBTypeEnum(String jdbcUrl) {
        if (jdbcUrl.startsWith("jdbc:mysql:")) {
            return DBType.MYSQL;
        } else if (jdbcUrl.startsWith("jdbc:oracle:")) {
            return DBType.ORACLE;
        } else if (jdbcUrl.startsWith("jdbc:postgresql:")) {
            return DBType.POSTGRESQL;
        } else if (jdbcUrl.startsWith("jdbc:sqlserver:")) {
            return DBType.SQLSERVER;
        } else if (jdbcUrl.startsWith("jdbc:dm:")) {
            return DBType.DM;
        } else if (jdbcUrl.startsWith("jdbc:duckdb:") || jdbcUrl.startsWith("quack:")) {
            return DBType.DUCKDB;
        } else if (jdbcUrl.startsWith("jdbc:clickhouse:")) {
            return DBType.CLICKHOUSE;
        } else if (jdbcUrl.startsWith("jdbc:greenplum:")) {
            return DBType.GREENPLUM;
        } else if (jdbcUrl.startsWith("jdbc:doris:")) {
            return DBType.DORIS;
        } else if (jdbcUrl.startsWith("ducklake:")) {
            return DBType.DUCKLAKE;
        } else if (jdbcUrl.startsWith("jdbc:sqlite:")) {
            return DBType.SQLITE;
        }
        return null;
    }

    /**
     * 按 JDBC URL 解析对应的数据源 Provider，用于把库表元数据、连通性测试、表存在性等
     * 与具体数据库相关的能力委托到各数据源 Provider 实现。
     */
    private static JdbcDataSourceProvider resolveJdbcProvider(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        DataSourceProvider provider = null;
        DBType dbType = getDBTypeEnum(jdbcUrl);
        if (dbType != null) {
            provider = DataSourceProviderFactory.provider(dbType.name());
        }
        if (provider == null) {
            // 未在中心表中登记的类型，改由各 Provider 依据自身 URL 前缀识别
            provider = DataSourceProviderFactory.providerForUrl(jdbcUrl);
        }
        return provider instanceof JdbcDataSourceProvider jdbcProvider ? jdbcProvider : null;
    }

    private static JdbcDataSourceProvider resolveJdbcProvider(Connection conn) throws SQLException {
        return resolveJdbcProvider(conn.getMetaData().getURL());
    }

    /**
     * 解析数据库连接URL，提取主机名、端口、数据库名等信息
     */
    public static DatasourceInfo parseConnectionUrl(DatasourceInfo datasourceInfo) {
        DatasourceInfo info = new DatasourceInfo();
        if (datasourceInfo.getUrl() == null || datasourceInfo.getUrl().trim().isEmpty()) {
            return datasourceInfo;
        }
        String urlLower = datasourceInfo.getUrl().toLowerCase();
        info.setType(datasourceInfo.getType());
        info.setUrl(datasourceInfo.getUrl());
        info.setExtraParams(datasourceInfo.getExtraParams());
        info.setUsername(datasourceInfo.getUsername());
        info.setPassword(datasourceInfo.getPassword());
        info.setDbschema(datasourceInfo.getDbschema());

        // MySQL URL格式: jdbc:mysql://hostname:port/database
        if (urlLower.contains("mysql")) {
            Pattern pattern = Pattern.compile("jdbc:mysql://([^:/]+)(?::(\\d+))?(?:/([^?]+))?");
            Matcher matcher = pattern.matcher(datasourceInfo.getUrl());
            if (matcher.find()) {
                info.setHostname(matcher.group(1));
                info.setPort(matcher.group(2));
                if (matcher.group(3) != null && !matcher.group(3).isEmpty()) {
                    info.setDbschema(matcher.group(3));
                }
            }
        }
        // PostgreSQL URL格式: jdbc:postgresql://hostname:port/database
        else if (urlLower.contains("postgresql")) {
            Pattern pattern = Pattern.compile("jdbc:postgresql://([^:/]+)(?::(\\d+))?(?:/([^?]+))?");
            Matcher matcher = pattern.matcher(datasourceInfo.getUrl());
            if (matcher.find()) {
                info.setHostname(matcher.group(1));
                info.setPort(matcher.group(2));
                info.setDbschema(matcher.group(3));
            }
        }
        // SQLite URL格式: jdbc:sqlite:filepath
        else if (urlLower.contains("sqlite")) {
            Pattern pattern = Pattern.compile("jdbc:sqlite:(.+)");
            Matcher matcher = pattern.matcher(datasourceInfo.getUrl());
            if (matcher.find()) {
                info.setDatabase(matcher.group(1));
            }
        }
        // SQL Server URL格式: jdbc:sqlserver://hostname:port;databaseName=database
        else if (urlLower.contains("sqlserver")) {
            Pattern pattern = Pattern.compile("jdbc:sqlserver://([^:;]+)(?::(\\d+))?.*databaseName=([^;]+)");
            Matcher matcher = pattern.matcher(datasourceInfo.getUrl());
            if (matcher.find()) {
                info.setHostname(matcher.group(1));
                info.setPort(matcher.group(2));
                info.setDatabase(matcher.group(3));
            }
        }
        // 达梦 URL格式: jdbc:dm://hostname:port[/schema]
        else if (urlLower.startsWith("jdbc:dm:")) {
            Pattern pattern = Pattern.compile("jdbc:dm://([^:/?]+)(?::(\\d+))?(?:/([^?]+))?");
            Matcher matcher = pattern.matcher(datasourceInfo.getUrl());
            if (matcher.find()) {
                info.setHostname(matcher.group(1));
                info.setPort(matcher.group(2));
                if (matcher.group(3) != null && !matcher.group(3).isEmpty()) {
                    info.setDbschema(matcher.group(3));
                }
            }
        }

        return info;
    }

    /**
     * 获取驱动类名（向后兼容方法）
     */
    public static String getDriverClassName(String url) {
        DBType dbType = getDBTypeEnum(url);
        if (dbType != null) {
            return dbType.getDriverClassName();
        }
        return "Unknown";
    }

    /**
     * 获取驱动类名（带版本信息）
     */
    public static String getDriverClassName(String url, String version) {
        DBType dbType = getDBTypeEnum(url);
        if (dbType != null) {
            return dbType.getDriverClassNameForVersion(version);
        }
        return "Unknown";
    }

    /**
     * 获取指定版本的驱动包路径
     */
    public static String getDriverJarForVersion(String url, String version) {
        DBType dbType = getDBTypeEnum(url);
        if (dbType != null) {
            return dbType.getDriverJarForVersion(version);
        }
        return null;
    }

    /**
     * 检查是否支持指定版本
     */
    public static boolean isVersionSupported(String url, String version) {
        DBType dbType = getDBTypeEnum(url);
        if (dbType != null) {
            return dbType.isVersionSupported(version);
        }
        return false;
    }

    /**
     * 获取支持的版本列表
     */
    public static List<String> getSupportedVersions(String url) {
        DBType dbType = getDBTypeEnum(url);
        if (dbType != null) {
            return dbType.getSupportedVersions();
        }
        return Collections.emptyList();
    }

    /**
     * 构建 JDBC URL（带版本信息）
     */
    public static String buildJdbcUrl(String url, String version) {
        DBType dbType = getDBTypeEnum(url);
        if (dbType != null) {
            return dbType.buildJdbcUrl(url, version);
        }
        return url;
    }

    /**
     * 获取默认版本
     */
    public static String getDefaultVersion(String url) {
        DBType dbType = getDBTypeEnum(url);
        if (dbType != null) {
            return dbType.getDefaultVersion();
        }
        return "default";
    }

    /**
     * 获取数据库显示名称
     */
    public static String getDatabaseDisplayName(String url) {
        DBType dbType = getDBTypeEnum(url);
        if (dbType != null) {
            return dbType.getDisplayName();
        }
        return "Unknown";
    }

    /**
     * 获取所有支持的数据库类型
     */
    public static List<DBType> getAllSupportedDBTypes() {
        return Arrays.asList(DBType.values());
    }

    /**
     * 执行非查询语句<br>
     * 语句包括 插入、更新、删除<br>
     * 此方法不会关闭Connection
     *
     * @param conn 数据库连接对象
     * @param sql SQL，使用name做为占位符，例如:name
     * @param paramMap 参数Map
     * @return 影响的行数
     * @throws SQLException SQL执行异常
     * @since 4.0.10
     */
    public static int execute(Connection conn, String sql, Map<String, Object> paramMap) throws SQLException {
        final NamedSql namedSql = new NamedSql(sql, paramMap);
        return execute(conn, namedSql.getSql(), namedSql.getParams());
    }

    /**
     * 执行非查询语句<br>
     * 语句包括 插入、更新、删除<br>
     * 此方法不会关闭Connection
     *
     * @param conn 数据库连接对象
     * @param sql SQL
     * @param params 参数
     * @return 影响的行数
     * @throws SQLException SQL执行异常
     */
    public static int execute(Connection conn, String sql, Object... params) throws SQLException {
        // Quack 直接执行 DELETE/TRUNCATE 会报 “Can only delete from base table”，改由远端执行
        if ((params == null || params.length == 0) && isQuackConnection(conn) && needsQuackRemoteExec(sql)) {
            return executeQuack(conn, sql);
        }
        try (PreparedStatement ps = StatementUtil.prepareStatement(conn, sql, params)) {
            return ps.executeUpdate();
        }
    }

    private static boolean needsQuackRemoteExec(String sql) {
        if (sql == null) {
            return false;
        }
        String s = sql.trim().toUpperCase();
        return s.startsWith("DELETE") || s.startsWith("TRUNCATE");
    }

    /**
     * 执行调用存储过程<br>
     * 此方法不会关闭Connection
     *
     * @param conn 数据库连接对象
     * @param sql SQL
     * @param params 参数
     * @return 如果执行后第一个结果是ResultSet，则返回true，否则返回false。
     * @throws SQLException SQL执行异常
     */
    public static boolean call(Connection conn, String sql, Object... params) throws SQLException {
        CallableStatement call = null;
        try {
            call = StatementUtil.prepareCall(conn, sql, params);
            return call.execute();
        } finally {
            DbUtil.close(call);
        }
    }

    /**
     * 执行调用存储过程<br>
     * 此方法不会关闭Connection
     *
     * @param conn 数据库连接对象
     * @param sql SQL
     * @param params 参数
     * @return ResultSet
     * @throws SQLException SQL执行异常
     * @since 4.1.4
     */
    public static ResultSet callQuery(Connection conn, String sql, Object... params) throws SQLException {
        return StatementUtil.prepareCall(conn, sql, params).executeQuery();
    }

    /**
     * 执行非查询语句，返回主键<br>
     * 发查询语句包括 插入、更新、删除<br>
     * 此方法不会关闭Connection
     *
     * @param conn 数据库连接对象
     * @param sql SQL
     * @param paramMap 参数Map
     * @return 主键
     * @throws SQLException SQL执行异常
     * @since 4.0.10
     */
    public static Long executeForGeneratedKey(Connection conn, String sql, Map<String, Object> paramMap) throws SQLException {
        final NamedSql namedSql = new NamedSql(sql, paramMap);
        return executeForGeneratedKey(conn, namedSql.getSql(), namedSql.getParams());
    }

    /**
     * 执行非查询语句，返回主键<br>
     * 发查询语句包括 插入、更新、删除<br>
     * 此方法不会关闭Connection
     *
     * @param conn 数据库连接对象
     * @param sql SQL
     * @param params 参数
     * @return 主键
     * @throws SQLException SQL执行异常
     */
    public static Long executeForGeneratedKey(Connection conn, String sql, Object... params) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = StatementUtil.prepareStatement(conn, sql, params);
            ps.executeUpdate();
            rs = ps.getGeneratedKeys();
            if (rs != null && rs.next()) {
                try {
                    return rs.getLong(1);
                } catch (SQLException e) {
                    // 可能会出现没有主键返回的情况
                }
            }
            return null;
        } finally {
            DbUtil.close(ps);
            DbUtil.close(rs);
        }
    }

    /**
     * 批量执行非查询语句<br>
     * 语句包括 插入、更新、删除<br>
     * 此方法不会关闭Connection
     *
     * @param conn 数据库连接对象
     * @param sql SQL
     * @param paramsBatch 批量的参数
     * @return 每个SQL执行影响的行数
     * @throws SQLException SQL执行异常
     * @deprecated 重载导致编译器无法区分
     */
    @Deprecated
    public static int[] executeBatch(Connection conn, String sql, Object[]... paramsBatch) throws SQLException {
        return executeBatch(conn, sql, new ArrayIter<>(paramsBatch));
    }

    /**
     * 批量执行非查询语句<br>
     * 语句包括 插入、更新、删除<br>
     * 此方法不会关闭Connection
     *
     * @param conn 数据库连接对象
     * @param sql SQL
     * @param paramsBatch 批量的参数
     * @return 每个SQL执行影响的行数
     * @throws SQLException SQL执行异常
     */
    public static int[] executeBatch(Connection conn, String sql, Iterable<Object[]> paramsBatch) throws SQLException {
        PreparedStatement ps = null;
        try {
            ps = StatementUtil.prepareStatementForBatch(conn, sql, paramsBatch);
            return ps.executeBatch();
        } finally {
            DbUtil.close(ps);
        }
    }

    /**
     * 批量执行非查询语句<br>
     * 语句包括 插入、更新、删除<br>
     * 此方法不会关闭Connection
     *
     * @param conn 数据库连接对象
     * @param sqls SQL列表
     * @return 每个SQL执行影响的行数
     * @throws SQLException SQL执行异常
     * @since 4.5.6
     */
    public static int[] executeBatch(Connection conn, String... sqls) throws SQLException {
        return executeBatch(conn, new ArrayIter<>(sqls));
    }

    /**
     * 批量执行非查询语句<br>
     * 语句包括 插入、更新、删除<br>
     * 此方法不会关闭Connection
     *
     * @param conn 数据库连接对象
     * @param sqls SQL列表
     * @return 每个SQL执行影响的行数
     * @throws SQLException SQL执行异常
     * @since 4.5.6
     */
    public static int[] executeBatch(Connection conn, Iterable<String> sqls) throws SQLException {
        Statement statement = null;
        try {
            statement = conn.createStatement();
            for (String sql : sqls) {
                statement.addBatch(sql);
            }
            return statement.executeBatch();
        } finally {
            DbUtil.close(statement);
        }
    }

    /**
     * 执行查询语句，例如：select * from table where field1=:name1 <br>
     * 此方法不会关闭Connection
     *
     * @param <T> 处理结果类型
     * @param conn 数据库连接对象
     * @param sql 查询语句，使用参数名占位符，例如:name
     * @param rsh 结果集处理对象
     * @param paramMap 参数对
     * @return 结果对象
     * @throws SQLException SQL执行异常
     * @since 4.0.10
     */
    public static <T> T query(Connection conn, String sql, RsHandler<T> rsh, Map<String, Object> paramMap) throws SQLException {
        final NamedSql namedSql = new NamedSql(sql, paramMap);
        return query(conn, namedSql.getSql(), rsh, namedSql.getParams());
    }

    /**
     * 执行查询语句<br>
     * 此方法不会关闭Connection
     *
     * @param <T> 处理结果类型
     * @param conn 数据库连接对象
     * @param sqlBuilder SQL构建器，包含参数
     * @param rsh 结果集处理对象
     * @return 结果对象
     * @throws SQLException SQL执行异常
     * @since 5.5.3
     */
    public static <T> T query(Connection conn, SqlBuilder sqlBuilder, RsHandler<T> rsh) throws SQLException {
        return query(conn, sqlBuilder.build(), rsh, sqlBuilder.getParamValueArray());
    }

    public static <T> List<T> query2Bean(Connection connection, String sql, Class<T> clazz) throws SQLException {
        return SqlExecutor.query(connection, sql, new BeanListHandler<T>(clazz));
    }


    /**
     * 执行查询语句<br>
     * 此方法不会关闭Connection
     *
     * @param <T> 处理结果类型
     * @param conn 数据库连接对象
     * @param sql 查询语句
     * @param rsh 结果集处理对象
     * @param params 参数
     * @return 结果对象
     * @throws SQLException SQL执行异常
     */
    public static <T> T query(Connection conn, String sql, RsHandler<T> rsh, Object... params) throws SQLException {
        PreparedStatement ps = null;
        try {
            ps = StatementUtil.prepareStatement(conn, sql, params);
            return executeQuery(ps, rsh);
        } finally {
            DbUtil.close(ps);
        }
    }

    /**
     * 执行自定义的{@link PreparedStatement}，结果使用{@link RsHandler}处理<br>
     * 此方法主要用于自定义场景，如游标查询等
     *
     * @param <T> 处理结果类型
     * @param conn 数据库连接对象
     * @param statementFunc 自定义{@link PreparedStatement}创建函数
     * @param rsh 自定义结果集处理
     * @return 结果
     * @throws SQLException SQL执行异常
     * @since 5.7.17
     */
    public static <T> T query(Connection conn, Func1<Connection, PreparedStatement> statementFunc, RsHandler<T> rsh) throws SQLException {
        PreparedStatement ps = null;
        try {
            ps = statementFunc.call(conn);
            return executeQuery(ps, rsh);
        } catch (Exception e) {
            if (e instanceof SQLException) {
                throw (SQLException) e;
            }
            throw ExceptionUtil.wrapRuntime(e);
        } finally {
            DbUtil.close(ps);
        }
    }

    // -------------------------------------------------------------------------------------- Execute With PreparedStatement
    /**
     * 用于执行 INSERT、UPDATE 或 DELETE 语句以及 SQL DDL（数据定义语言）语句，例如 CREATE TABLE 和 DROP TABLE。<br>
     * INSERT、UPDATE 或 DELETE 语句的效果是修改表中零行或多行中的一列或多列。<br>
     * executeUpdate 的返回值是一个整数（int），指示受影响的行数（即更新计数）。<br>
     * 对于 CREATE TABLE 或 DROP TABLE 等不操作行的语句，executeUpdate 的返回值总为零。<br>
     * 此方法不会关闭PreparedStatement
     *
     * @param ps PreparedStatement对象
     * @param params 参数
     * @return 影响的行数
     * @throws SQLException SQL执行异常
     */
    public static int executeUpdate(PreparedStatement ps, Object... params) throws SQLException {
        StatementUtil.fillParams(ps, params);
        return ps.executeUpdate();
    }

    /**
     * 可用于执行任何SQL语句，返回一个boolean值，表明执行该SQL语句是否返回了ResultSet。<br>
     * 如果执行后第一个结果是ResultSet，则返回true，否则返回false。<br>
     * 此方法不会关闭PreparedStatement
     *
     * @param ps PreparedStatement对象
     * @param params 参数
     * @return 如果执行后第一个结果是ResultSet，则返回true，否则返回false。
     * @throws SQLException SQL执行异常
     */
    public static boolean execute(PreparedStatement ps, Object... params) throws SQLException {
        StatementUtil.fillParams(ps, params);
        return ps.execute();
    }

    /**
     * 执行查询语句<br>
     * 此方法不会关闭PreparedStatement
     *
     * @param <T> 处理结果类型
     * @param ps PreparedStatement
     * @param rsh 结果集处理对象
     * @param params 参数
     * @return 结果对象
     * @throws SQLException SQL执行异常
     */
    public static <T> T query(PreparedStatement ps, RsHandler<T> rsh, Object... params) throws SQLException {
        StatementUtil.fillParams(ps, params);
        return executeQuery(ps, rsh);
    }

    /**
     * 执行查询语句并关闭PreparedStatement
     *
     * @param <T> 处理结果类型
     * @param ps PreparedStatement
     * @param rsh 结果集处理对象
     * @param params 参数
     * @return 结果对象
     * @throws SQLException SQL执行异常
     */
    public static <T> T queryAndClosePs(PreparedStatement ps, RsHandler<T> rsh, Object... params) throws SQLException {
        try {
            return query(ps, rsh, params);
        } finally {
            DbUtil.close(ps);
        }
    }

    // -------------------------------------------------------------------------------------------------------------------------------- Private method start
    /**
     * 执行查询
     *
     * @param ps {@link PreparedStatement}
     * @param rsh 结果集处理对象
     * @return 结果对象
     * @throws SQLException SQL执行异常
     * @since 4.1.13
     */
    private static <T> T executeQuery(PreparedStatement ps, RsHandler<T> rsh) throws SQLException {
        ResultSet rs = null;
        try {
            rs = ps.executeQuery();
            return rsh.handle(rs);
        } finally {
            DbUtil.close(rs);
        }
    }

    public static void close(Object... objsToClose) {
        for (Object obj : objsToClose) {
            if (null != obj) {
                if (obj instanceof AutoCloseable) {
                    IoUtil.close((AutoCloseable) obj);
                }
            }
        }
    }

    public static boolean tableExists(Connection conn, String schema, String table) throws SQLException {
        JdbcDataSourceProvider provider = resolveJdbcProvider(conn);
        if (provider != null) {
            return provider.tableExists(conn, schema, table);
        }
        return tableExists(conn, schema, null, table);
    }

    // 判断表是否存在
    public static boolean tableExists(Connection conn, String catalog, String schema, String table) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(catalog, schema, table, null)) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("表存在性检查失败", e);
        }
    }

    // 判断表是否存在
    public static boolean schemaExists(Connection conn, String schema) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getSchemas(null, schema)) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("schema存在性检查失败", e);
        }
    }
}