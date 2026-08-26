package com.data.metadata.util;

import com.data.job.DatasourceInfo;
import com.data.metadata.DBType;
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
    private static final String MYSQL_DRIVER_DIR = DRIVER_BASE_DIR + "/mysql";
    private static final String ORACLE_DRIVER_DIR = DRIVER_BASE_DIR + "/oracle";
    private static final String POSTGRESQL_DRIVER_DIR = DRIVER_BASE_DIR + "/postgresql";
    private static final String SQLSERVER_DRIVER_DIR = DRIVER_BASE_DIR + "/sqlserver";
    private static final String DUCKDB_DRIVER_DIR = DRIVER_BASE_DIR + "/duckdb";


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
        HikariDataSource dataSource = dataSourcePool.get(poolKey);

        if (dataSource == null) {
            synchronized (ConnectionPoolManager.class) {
                dataSource = dataSourcePool.get(poolKey);
                if (dataSource == null) {
                    dataSource = createDataSource(poolKey, url, user, password, version);
                    dataSourcePool.put(poolKey, dataSource);
                }
            }
        }

        return dataSource.getConnection();
    }

    /**
     * 创建HikariCP数据源
     */
    private static HikariDataSource createDataSource(String poolKey, String url, String user, String password, String version) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setPoolName("HikariPool-" + poolKey);

        // 设置连接池参数
        config.setMaximumPoolSize(DEFAULT_MAX_POOL_SIZE);
        config.setMinimumIdle(DEFAULT_MIN_IDLE);
        config.setConnectionTimeout(DEFAULT_CONNECTION_TIMEOUT);
        config.setIdleTimeout(DEFAULT_IDLE_TIMEOUT);
        config.setMaxLifetime(DEFAULT_MAX_LIFETIME);

        // 配置连接测试
        config.setConnectionTestQuery(DBUtils.getTestQuery(url));
        config.setValidationTimeout(5000); // 验证超时时间5秒

        // 数据库特定配置
        DBType dbType = DBUtils.getDBTypeEnum(url);
        configureDatabaseSpecificSettings(config, dbType);

        // 使用自定义驱动类加载器加载驱动
        loadDriverWithCustomClassLoader(url, dbType, version);

        return new HikariDataSource(config);
    }

    /**
     * 配置数据库特定设置
     */
    private static void configureDatabaseSpecificSettings(HikariConfig config, DBType dbType) {
        if (dbType == null) {
            return;
        }

        switch (dbType) {
            case MYSQL:
                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                config.addDataSourceProperty("useServerPrepStmts", "true");
                config.addDataSourceProperty("useLocalSessionState", "true");
                config.addDataSourceProperty("rewriteBatchedStatements", "true");
                config.addDataSourceProperty("cacheResultSetMetadata", "true");
                config.addDataSourceProperty("cacheServerConfiguration", "true");
                config.addDataSourceProperty("elideSetAutoCommits", "true");
                config.addDataSourceProperty("maintainTimeStats", "false");
                break;
            case POSTGRESQL:
                config.addDataSourceProperty("preparedStatementCacheQueries", "256");
                config.addDataSourceProperty("preparedStatementCacheSizeMiB", "5");
                config.addDataSourceProperty("assumeMinServerVersion", "9.0");
                break;
            case ORACLE:
                config.addDataSourceProperty("oracle.jdbc.ReadTimeout", "30000");
                config.addDataSourceProperty("oracle.net.CONNECT_TIMEOUT", "10000");
                break;
            case DUCKDB:
                config.addDataSourceProperty("jdbc_stream_results", "true");
                break;
            default:
                // 其他数据库类型使用默认配置
                break;
        }
    }

    /**
     * 使用自定义类加载器加载驱动
     */
    private static void loadDriverWithCustomClassLoader(String url, DBType dbType, String version) {
        if (dbType == null) {
            return;
        }

        String driverClassName = DBUtils.getDriverClassName(url, version);
        if ("Unknown".equals(driverClassName)) {
            return;
        }

        String driverKey = generateDriverKey(dbType.name(), version, driverClassName);
        DriverClassLoader classLoader = driverClassLoaders.get(driverKey);

        if (classLoader == null) {
            synchronized (ConnectionPoolManager.class) {
                classLoader = driverClassLoaders.get(driverKey);
                if (classLoader == null) {
                    classLoader = new DriverClassLoader();
                    driverClassLoaders.put(driverKey, classLoader);

                    // 根据数据源类型和版本加载对应的驱动包
                    loadDriverJarByTypeAndVersion(dbType, version, classLoader);

                    try {
                        // 使用自定义类加载器加载驱动类
                        Class<?> driverClass = classLoader.loadClass(driverClassName);
                        // 注册驱动
                        java.sql.Driver driver = (java.sql.Driver) driverClass.newInstance();
                        java.sql.DriverManager.registerDriver(new DriverProxy(driver, classLoader));
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to load driver: " + driverClassName, e);
                    }
                }
            }
        }
    }

    //    drivers/
    //        ├── mysql/
    //        │   ├── 5.7/
    //        │   │   └── mysql-connector-java-5.1.49.jar
    //        │   ├── 8.0/
    //        │   │   └── mysql-connector-java-8.0.33.jar
    //        │   └── default/
    //        │       └── mysql-connector-java-8.0.33.jar
    //        ├── oracle/
    //        │   ├── 11g/
    //        │   │   └── ojdbc6.jar
    //        │   ├── 19c/
    //        │   │   └── ojdbc8.jar
    //        │   └── default/
    //        │       └── ojdbc8.jar
    //        └── postgresql/
    //        │   ├── 9.6/
    //        │   │   └── postgresql-9.4.1212.jar
    //        │   ├── 13/
    //        │   │   └── postgresql-42.5.0.jar
    //        │   └── default/
    //        │       └── postgresql-42.5.0.jar

    /**
     * 根据数据源类型和版本加载对应的驱动包
     */
    private static void loadDriverJarByTypeAndVersion(DBType dbType, String version, DriverClassLoader classLoader) {
        String driverDir = getDriverDirectory(dbType, version);
        File dir = new File(driverDir);

        if (dir.exists() && dir.isDirectory()) {
            File[] jarFiles = dir.listFiles((dir1, name) -> name.toLowerCase().endsWith(".jar"));
            if (jarFiles != null) {
                for (File jarFile : jarFiles) {
                    try {
                        classLoader.addDriverJar(jarFile.getAbsolutePath());
                        System.out.println("Loaded driver JAR: " + jarFile.getAbsolutePath() + " for " + dbType + " version " + version);
                    } catch (Exception e) {
                        System.err.println("Failed to load driver JAR: " + jarFile.getAbsolutePath() + ", error: " + e.getMessage());
                    }
                }
            } else {
                System.err.println("No JAR files found in driver directory: " + driverDir);
            }
        } else {
            // 如果指定版本的目录不存在，尝试加载默认版本的驱动
            System.err.println("Driver directory not found: " + driverDir + ", trying default version");
            loadDefaultDriverJar(dbType, classLoader);
        }
    }

    /**
     * 加载默认版本的驱动包
     */
    private static void loadDefaultDriverJar(DBType dbType, DriverClassLoader classLoader) {
        String defaultDriverDir = getDefaultDriverDirectory(dbType);
        File dir = new File(defaultDriverDir);

        if (dir.exists() && dir.isDirectory()) {
            File[] jarFiles = dir.listFiles((dir1, name) -> name.toLowerCase().endsWith(".jar"));
            if (jarFiles != null) {
                for (File jarFile : jarFiles) {
                    try {
                        classLoader.addDriverJar(jarFile.getAbsolutePath());
                        System.out.println("Loaded default driver JAR: " + jarFile.getAbsolutePath() + " for " + dbType);
                    } catch (Exception e) {
                        System.err.println(
                            "Failed to load default driver JAR: " + jarFile.getAbsolutePath() + ", error: " + e.getMessage()
                        );
                    }
                }
            } else {
                System.err.println("No JAR files found in default driver directory: " + defaultDriverDir);
            }
        } else {
            System.err.println("Default driver directory not found: " + defaultDriverDir);
        }
    }

    /**
     * 获取驱动目录路径
     */
    private static String getDriverDirectory(DBType dbType, String version) {
        String baseDir = getDriverBaseDirectory(dbType);
        return baseDir + "/" + version;
    }

    /**
     * 获取默认驱动目录路径
     */
    private static String getDefaultDriverDirectory(DBType dbType) {
        return getDriverBaseDirectory(dbType) + "/default";
    }

    /**
     * 获取驱动基础目录路径
     */
    private static String getDriverBaseDirectory(DBType dbType) {
        if (dbType == null) {
            return DRIVER_BASE_DIR;
        }

        switch (dbType) {
            case MYSQL:
                return MYSQL_DRIVER_DIR;
            case ORACLE:
                return ORACLE_DRIVER_DIR;
            case POSTGRESQL:
                return POSTGRESQL_DRIVER_DIR;
            case SQLSERVER:
                return SQLSERVER_DRIVER_DIR;
            case DUCKDB:
                return DUCKDB_DRIVER_DIR;
            default:
                return DRIVER_BASE_DIR + "/" + dbType.name().toLowerCase();
        }
    }

    /**
     * 获取驱动类名（向后兼容方法）
     */
    private static String getDriverClassName(String url) {
        DBType dbType = DBUtils.getDBTypeEnum(url);
        if (dbType != null) {
            return dbType.getDriverClassName();
        }
        return "Unknown";
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
