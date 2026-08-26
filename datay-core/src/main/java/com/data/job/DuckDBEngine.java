package com.data.job;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.duckdb.DuckDBDriver;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DuckDBEngine {

    // 默认数据库文件路径
    private static final String DEFAULT_DB_FILE = "test.db";

    // 单例实例
    private static volatile DuckDBEngine instance;

    // 数据库文件路径到连接池的映射
    private static final ConcurrentHashMap<String, HikariDataSource> dataSourceMap = new ConcurrentHashMap<>();

    // 默认连接池（用于向后兼容）
    private final HikariDataSource defaultDataSource;

    // 私有构造方法实现单例
    private DuckDBEngine() {
        // 初始化默认连接池
        defaultDataSource = createDataSource(DEFAULT_DB_FILE);
        dataSourceMap.put(DEFAULT_DB_FILE, defaultDataSource);
    }

    // 双重校验锁实现线程安全单例
    public static DuckDBEngine getInstance() {
        if (instance == null) {
            synchronized (DuckDBEngine.class) {
                if (instance == null) {
                    instance = new DuckDBEngine();
                }
            }
        }
        return instance;
    }

    /**
     * 创建HikariCP连接池
     */
    private HikariDataSource createDataSource(String dbFile) {
        //加载驱动
        try {
            Class.forName("org.duckdb.DuckDBDriver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("DuckDB 驱动未找到", e);
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:duckdb:" + getDBFile(dbFile));
        config.setPoolName("DuckDB-Pool-" + dbFile);
        config.setMaximumPoolSize(10); // 每个数据库文件最大连接数
        config.setMinimumIdle(2); // 每个数据库文件最小空闲连接数
        config.setConnectionTimeout(30000); // 连接超时时间30秒
        config.setIdleTimeout(600000); // 空闲连接超时时间10分钟
        config.setMaxLifetime(1800000); // 连接最大生命周期30分钟
        config.setLeakDetectionThreshold(60000); // 连接泄漏检测阈值60秒
        Properties dsProperties = new Properties();
        dsProperties.setProperty(DuckDBDriver.JDBC_STREAM_RESULTS, String.valueOf(true));
        config.setDataSourceProperties(dsProperties);

        // 配置连接测试
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000); // 验证超时时间5秒

        return new HikariDataSource(config);
    }

    public static String getDBFile(String dbFile) {
        // 获取当前工作目录的绝对路径
        String currentDir = System.getProperty("user.dir");
        if (dbFile == null || dbFile.trim().isEmpty()) {
            return currentDir + "/log/" + DEFAULT_DB_FILE;
        }
        return currentDir + "/log/" + dbFile;
    }

    /**
     * 根据数据库文件路径获取连接池
     */
    private synchronized HikariDataSource getDataSource(String dbFile) {
        if (dbFile == null || dbFile.trim().isEmpty()) {
            return defaultDataSource;
        }

        return dataSourceMap.computeIfAbsent(dbFile, this::createDataSource);
    }

    /**
     * 从默认连接池获取连接（向后兼容）
     */
    public Connection getConnection() throws SQLException {
        return defaultDataSource.getConnection();
    }

    /**
     * 根据数据库文件路径获取连接
     */
    public Connection getConnection(String dbFile) throws SQLException {
        HikariDataSource dataSource = getDataSource(dbFile);
        return dataSource.getConnection();
    }

    /**
     * 关闭所有连接池
     */
    public void close() {
        for (HikariDataSource dataSource : dataSourceMap.values()) {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }
        dataSourceMap.clear();
    }

    /**
     * 获取指定数据库文件的连接池状态信息
     */
    public String getPoolStatus(String dbFile) {
        HikariDataSource dataSource = getDataSource(dbFile);
        if (dataSource == null) {
            return "连接池未初始化: " + dbFile;
        }

        return String.format(
            "连接池状态[%s]: 活跃连接=%d, 空闲连接=%d, 等待连接=%d, 总连接=%d",
            dbFile,
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection(),
            dataSource.getHikariPoolMXBean().getTotalConnections()
        );
    }

    /**
     * 获取所有连接池的状态信息
     */
    public Map<String, String> getAllPoolStatus() {
        Map<String, String> statusMap = new HashMap<>();
        for (String dbFile : dataSourceMap.keySet()) {
            statusMap.put(dbFile, getPoolStatus(dbFile));
        }
        return statusMap;
    }

    /**
     * 通用执行方法（适合DDL）- 使用默认数据库
     */
    public void executeUpdate(String sql) throws SQLException {
        executeUpdate(sql, DEFAULT_DB_FILE);
    }

    /**
     * 通用执行方法（适合DDL）- 指定数据库文件
     */
    public void executeUpdate(String sql, String dbFile) throws SQLException {
        try (Connection conn = getConnection(dbFile); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    public static void deleteDBFile(String dbName) {
        String dbFile = getDBFile(dbName);
        close(dbFile);
        File dbFileObj = new File(dbFile);
        if (dbFileObj.exists()) {
            dbFileObj.delete();
        }
        File walFileObj = new File(dbFile + ".wal");
        if (walFileObj.exists()) {
            walFileObj.delete();
        }
    }

    /**
     * 关闭指定数据库文件的连接池
     */
    public static void close(String dbFile) {
        if (dbFile == null || dbFile.trim().isEmpty()) {
            return;
        }
        HikariDataSource dataSource = dataSourceMap.remove(dbFile);
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
