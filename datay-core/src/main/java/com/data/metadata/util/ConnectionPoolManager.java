package com.data.metadata.util;

import com.data.datasource.DataSourceProvider;
import com.data.datasource.DataSourceProviderFactory;
import com.data.datasource.provider.JdbcDataSourceProvider;
import com.data.job.DatasourceInfo;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 连接池管理器
 * 支持不同数据源单独创建连接池，避免驱动类冲突
 * 支持按数据源类型和版本加载对应的驱动包
 */
public class ConnectionPoolManager {

    private static final Map<String, HikariDataSource> dataSourcePool = new ConcurrentHashMap<>();
    private static final Map<String, DriverClassLoader> driverClassLoaders = new ConcurrentHashMap<>();

    // 默认连接池配置
    private static final int DEFAULT_MAX_POOL_SIZE = 10;
    private static final int DEFAULT_MIN_IDLE = 2;
    private static final long DEFAULT_CONNECTION_TIMEOUT = 10000;
    private static final long DEFAULT_IDLE_TIMEOUT = 600000;
    private static final long DEFAULT_MAX_LIFETIME = 1800000;

    // 驱动包根目录配置
    private static final String DRIVER_BASE_DIR = "drivers";


    /**
     * 获取数据库连接（使用连接池）- 带版本信息
     */
    public static Connection getConnection(String url, String user, String password, String version) throws SQLException {
        String poolKey = generatePoolKey(url, user, version);
        return getConnectionFromPool(poolKey, url, user, password, version);
    }

    /**
     * 获取数据库连接（使用连接池）- 带版本信息
     */
    public static Connection getConnection(DatasourceInfo datasourceInfo, String version) throws SQLException {
        String poolKey = generatePoolKey(datasourceInfo.getUrl(), datasourceInfo.getUsername(), version);
        return getConnectionFromPool(poolKey, datasourceInfo.getUrl(), datasourceInfo.getUsername(), datasourceInfo.getPassword(), version);
    }

    /**
     * 获取数据库连接（使用连接池）- 通用 JDBC：显式指定驱动类、驱动 Jar 与测试语句。
     *
     * <p>用于「通用 JDBC」数据源：驱动类名任意，且驱动 Jar 可能不在平台类路径上，
     * 需要通过隔离类加载器从指定 Jar/目录加载。
     *
     * @param datasourceInfo  数据源（类型、URL、账号等）
     * @param driverClassName JDBC 驱动类名，可为 {@code null}（回退到类型/URL 推断）
     * @param driverJarPath   驱动 Jar 文件或目录路径，可为 {@code null}（使用平台类路径）
     * @param testQuery       连通性测试语句，可为 {@code null}（默认 SELECT 1）
     */
    public static Connection getConnection(
        DatasourceInfo datasourceInfo,
        String driverClassName,
        String driverJarPath,
        String testQuery
    ) throws SQLException {
        String poolKey = generatePoolKey(datasourceInfo.getUrl(), datasourceInfo.getUsername(), "default");
        if (driverClassName != null && !driverClassName.isBlank()) {
            poolKey = poolKey + "|" + driverClassName;
        }
        return getConnectionFromPool(
            poolKey,
            datasourceInfo.getUrl(),
            datasourceInfo.getUsername(),
            datasourceInfo.getPassword(),
            "default",
            datasourceInfo.getType(),
            driverClassName,
            driverJarPath,
            testQuery
        );
    }

    /**
     * 获取数据库连接（使用连接池）- 不带版本信息（向后兼容）
     */
    public static Connection getConnection(String url, String user, String password) throws SQLException {
        return getConnection(url, user, password, "default");
    }

    /**
     * 获取数据库连接（使用连接池）- 不带版本信息（向后兼容）
     */
    public static Connection getConnection(DatasourceInfo datasourceInfo) throws SQLException {
        return getConnection(datasourceInfo, "default");
    }

    /**
     * 从连接池获取连接
     */
    private static Connection getConnectionFromPool(String poolKey, String url, String user, String password, String version)
        throws SQLException {
        return getConnectionFromPool(poolKey, url, user, password, version, null, null, null, null);
    }

    private static Connection getConnectionFromPool(
        String poolKey,
        String url,
        String user,
        String password,
        String version,
        String type,
        String driverClassName,
        String driverJarPath,
        String testQuery
    ) throws SQLException {
        HikariDataSource dataSource = dataSourcePool.get(poolKey);

        if (dataSource == null) {
            synchronized (ConnectionPoolManager.class) {
                dataSource = dataSourcePool.get(poolKey);
                if (dataSource == null) {
                    dataSource = createDataSource(poolKey, url, user, password, version, type, driverClassName, driverJarPath, testQuery);
                    dataSourcePool.put(poolKey, dataSource);
                }
            }
        }

        return dataSource.getConnection();
    }

    /**
     * 创建HikariCP数据源。
     *
     * <p>与具体数据库相关的差异（URL 转换、账号密码、驱动属性、连通性测试语句、驱动加载）
     * 由类型或 URL 对应的 {@link JdbcDataSourceProvider} 提供；通用 JDBC 可通过
     * {@code driverClassName / driverJarPath / testQuery} 显式覆盖驱动与测试语句。
     */
    private static HikariDataSource createDataSource(
        String poolKey,
        String url,
        String user,
        String password,
        String version,
        String type,
        String driverClassName,
        String driverJarPath,
        String testQuery
    ) {
        HikariConfig config = new HikariConfig();

        JdbcDataSourceProvider provider = resolveJdbcProvider(type, url);
        String resolvedUrl = provider != null ? provider.resolveJdbcUrl(url) : url;

        config.setJdbcUrl(resolvedUrl);
        if (provider == null || provider.requiresCredentials()) {
            config.setUsername(user);
            config.setPassword(password);
        } else {
            config.setUsername(null);
            config.setPassword(null);
        }
        config.setPoolName("HikariPool-" + poolKey);

        config.setMaximumPoolSize(DEFAULT_MAX_POOL_SIZE);
        config.setMinimumIdle(DEFAULT_MIN_IDLE);
        config.setConnectionTimeout(DEFAULT_CONNECTION_TIMEOUT);
        config.setIdleTimeout(DEFAULT_IDLE_TIMEOUT);
        config.setMaxLifetime(DEFAULT_MAX_LIFETIME);

        if (testQuery != null && !testQuery.isBlank()) {
            config.setConnectionTestQuery(testQuery);
        } else {
            config.setConnectionTestQuery(provider != null ? provider.getTestQuery() : "SELECT 1");
        }
        config.setValidationTimeout(5000);

        if (provider != null) {
            provider.dataSourceProperties().forEach(config::addDataSourceProperty);
        }

        if (driverClassName != null && !driverClassName.isBlank()) {
            loadCustomDriver(driverClassName, driverJarPath, version);
        } else {
            loadDriverWithCustomClassLoader(provider, version);
        }

        return new HikariDataSource(config);
    }

    /**
     * 按数据源类型或 JDBC URL 解析对应的数据源 Provider，无法匹配时返回 {@code null}。
     */
    private static JdbcDataSourceProvider resolveJdbcProvider(String type, String url) {
        if (type != null && !type.isBlank()) {
            DataSourceProvider provider = DataSourceProviderFactory.provider(type);
            if (provider instanceof JdbcDataSourceProvider jdbcProvider) {
                return jdbcProvider;
            }
        }
        return resolveJdbcProvider(url);
    }

    /**
     * 按 JDBC URL 解析对应的数据源 Provider，无法匹配时返回 {@code null}。
     */
    private static JdbcDataSourceProvider resolveJdbcProvider(String url) {
        if (url == null) {
            return null;
        }
        DataSourceProvider provider = DataSourceProviderFactory.providerForUrl(url);
        return provider instanceof JdbcDataSourceProvider jdbcProvider ? jdbcProvider : null;
    }

    /**
     * 使用自定义类加载器加载驱动
     */
    private static void loadDriverWithCustomClassLoader(JdbcDataSourceProvider provider, String version) {
        if (provider == null) {
            return;
        }

        String driverClassName = provider.driverClassName(version);
        if (driverClassName == null || driverClassName.isBlank() || "Unknown".equals(driverClassName)) {
            return;
        }

        String driverKey = generateDriverKey(provider.typeName(), version, driverClassName);
        DriverClassLoader classLoader = driverClassLoaders.get(driverKey);

        if (classLoader == null) {
            synchronized (ConnectionPoolManager.class) {
                classLoader = driverClassLoaders.get(driverKey);
                if (classLoader == null) {
                    classLoader = new DriverClassLoader();
                    driverClassLoaders.put(driverKey, classLoader);

                    // 按需下载，并加载该数据源驱动目录下的驱动包
                    provider.ensureDriverAvailable(version);
                    loadDriverJars(provider.driverDirectory(version), classLoader, provider.typeName(), version);

                    try {
                        // 使用自定义类加载器加载驱动类
                        Class<?> driverClass = classLoader.loadClass(driverClassName);
                        // 注册驱动
                        java.sql.Driver driver = (java.sql.Driver) driverClass.getDeclaredConstructor().newInstance();
                        registerDriverProxy(driver, classLoader);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to load driver: " + driverClassName, e);
                    }
                }
            }
        }
    }

    /**
     * 加载通用 JDBC 自定义驱动：从指定 Jar/目录用隔离类加载器加载驱动类并注册。
     *
     * <p>驱动类名任意（不受内置类型前缀限制），通过 {@link DriverClassLoader#loadDriverClass(String)}
     * 子优先加载；未提供 Jar 时回退到平台类路径。
     */
    private static void loadCustomDriver(String driverClassName, String driverJarPath, String version) {
        String driverKey = generateDriverKey("jdbc", version, driverClassName) + "|" + (driverJarPath == null ? "" : driverJarPath);
        DriverClassLoader classLoader = driverClassLoaders.get(driverKey);
        if (classLoader != null) {
            return;
        }
        synchronized (ConnectionPoolManager.class) {
            classLoader = driverClassLoaders.get(driverKey);
            if (classLoader != null) {
                return;
            }
            classLoader = new DriverClassLoader();
            driverClassLoaders.put(driverKey, classLoader);

            if (driverJarPath != null && !driverJarPath.isBlank()) {
                loadDriverJars(new File(driverJarPath), classLoader, "jdbc", version);
            }

            try {
                Class<?> driverClass = classLoader.loadDriverClass(driverClassName);
                java.sql.Driver driver = (java.sql.Driver) driverClass.getDeclaredConstructor().newInstance();
                registerDriverProxy(driver, classLoader);
                System.out.println("Loaded custom JDBC driver: " + driverClassName + " from " + driverJarPath);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load driver: " + driverClassName, e);
            }
        }
    }

    /**
     * 将隔离加载的驱动注册到 {@link java.sql.DriverManager}。
     *
     * <p>{@code DriverManager} 会按「调用方类加载器」过滤已注册驱动（{@code isDriverAllowed}）。
     * 在 Spring Boot devtools 等重启类加载器环境下，本类由重启类加载器加载，而调用 JDBC 的
     * HikariCP 由系统类加载器加载，导致 {@link DriverProxy} 被过滤、报 “No suitable driver”。
     * 因此优先用系统类加载器加载 {@link DriverProxy} 并创建代理实例，确保过滤通过；
     * 若系统类加载器无法加载（如可执行 fat jar），则回退为当前类加载器直接注册。
     */
    private static void registerDriverProxy(java.sql.Driver driver, DriverClassLoader classLoader) throws Exception {
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        if (systemClassLoader != null && DriverProxy.class.getClassLoader() != systemClassLoader) {
            try {
                Class<?> proxyClass = Class.forName(DriverProxy.class.getName(), true, systemClassLoader);
                if (proxyClass != DriverProxy.class) {
                    java.sql.Driver proxy = (java.sql.Driver) proxyClass.getConstructor(java.sql.Driver.class).newInstance(driver);
                    java.sql.DriverManager.registerDriver(proxy);
                    return;
                }
            } catch (Throwable e) {
                System.err.println(
                    "Failed to register driver proxy via system classloader, falling back to current classloader: " + e.getMessage()
                );
            }
        }
        java.sql.DriverManager.registerDriver(new DriverProxy(driver, classLoader));
    }

    /**
     * 加载指定目录（或单个 jar 文件）下的驱动 jar。
     *
     * <p>驱动目录解析与按需下载均由各数据源 Provider 提供（见
     * {@link JdbcDataSourceProvider#driverDirectory(String)}）；目录或文件不存在时仅告警，
     * 驱动类仍可能由平台类路径提供。
     */
    private static void loadDriverJars(File dir, DriverClassLoader classLoader, String dbType, String version) {
        if (dir == null || !dir.exists()) {
            System.err.println(
                "Driver path not found: " + (dir == null ? "n/a" : dir.getPath()) + " for " + dbType
            );
            return;
        }
        if (dir.isFile()) {
            if (dir.getName().toLowerCase().endsWith(".jar")) {
                loadDriverJar(dir, classLoader, dbType, version);
            } else {
                System.err.println("Driver file is not a jar: " + dir.getPath() + " for " + dbType);
            }
            return;
        }
        File[] jarFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            System.err.println("No JAR files found in driver directory: " + dir.getPath() + " for " + dbType);
            return;
        }
        for (File jarFile : jarFiles) {
            loadDriverJar(jarFile, classLoader, dbType, version);
        }
    }

    private static void loadDriverJar(File jarFile, DriverClassLoader classLoader, String dbType, String version) {
        try {
            classLoader.addDriverJar(jarFile.getAbsolutePath());
            System.out.println(
                "Loaded driver JAR: " + jarFile.getAbsolutePath() + " for " + dbType + " version " + version
            );
        } catch (Exception e) {
            System.err.println("Failed to load driver JAR: " + jarFile.getAbsolutePath() + ", error: " + e.getMessage());
        }
    }

    /**
     * 生成连接池键（带版本信息）
     */
    private static String generatePoolKey(String url, String user, String version) {
        return url + "|" + user + "|" + version;
    }

    /**
     * 生成驱动键（带版本信息）
     */
    private static String generateDriverKey(String dbType, String version, String driverClassName) {
        return dbType + "|" + version + "|" + driverClassName;
    }

    /**
     * 设置驱动包根目录（可选）
     */
    public static void setDriverBaseDir(String baseDir) {
        // 可以动态修改驱动包根目录
        // 注意：需要在创建连接池之前调用此方法
    }

    /**
     * 手动添加驱动包
     */
    public static void addDriverJar(String dbType, String version, String jarPath) {
        String driverKey = dbType + "|" + version;
        // 可以手动添加特定版本的驱动包
    }

    /**
     * 关闭连接池（带版本信息）
     */
    public static void closePool(String url, String user, String version) {
        String poolKey = generatePoolKey(url, user, version);
        HikariDataSource dataSource = dataSourcePool.remove(poolKey);
        if (dataSource != null) {
            dataSource.close();
        }
    }

    /**
     * 关闭连接池（不带版本信息，向后兼容）
     */
    public static void closePool(String url, String user) {
        closePool(url, user, "default");
    }

    /**
     * 关闭所有连接池
     */
    public static void closeAllPools() {
        for (HikariDataSource dataSource : dataSourcePool.values()) {
            if (dataSource != null) {
                dataSource.close();
            }
        }
        dataSourcePool.clear();
        driverClassLoaders.clear();
    }

    /**
     * 获取连接池统计信息（带版本信息）
     */
    public static Map<String, Object> getPoolStats(String url, String user, String version) {
        String poolKey = generatePoolKey(url, user, version);
        HikariDataSource dataSource = dataSourcePool.get(poolKey);

        if (dataSource != null) {
            Map<String, Object> stats = new java.util.HashMap<>();
            stats.put("activeConnections", dataSource.getHikariPoolMXBean().getActiveConnections());
            stats.put("idleConnections", dataSource.getHikariPoolMXBean().getIdleConnections());
            stats.put("totalConnections", dataSource.getHikariPoolMXBean().getTotalConnections());
            stats.put("threadsAwaitingConnection", dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
            return stats;
        }

        return java.util.Collections.emptyMap();
    }

    /**
     * 获取连接池统计信息（不带版本信息，向后兼容）
     */
    public static Map<String, Object> getPoolStats(String url, String user) {
        return getPoolStats(url, user, "default");
    }
}