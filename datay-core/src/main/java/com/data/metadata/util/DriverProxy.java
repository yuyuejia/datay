package com.data.metadata.util;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * 驱动代理类
 * 用于在不同类加载器环境中管理驱动实例
 */
public class DriverProxy implements Driver {

    private final Driver targetDriver;
    private final DriverClassLoader classLoader;

    public DriverProxy(Driver targetDriver, DriverClassLoader classLoader) {
        this.targetDriver = targetDriver;
        this.classLoader = classLoader;
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        // 保存当前线程的类加载器
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            // 设置为驱动类加载器
            Thread.currentThread().setContextClassLoader(classLoader);
            return targetDriver.connect(url, info);
        } finally {
            // 恢复原始类加载器
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return targetDriver.acceptsURL(url);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return targetDriver.getPropertyInfo(url, info);
    }

    @Override
    public int getMajorVersion() {
        return targetDriver.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return targetDriver.getMinorVersion();
    }

    @Override
    public boolean jdbcCompliant() {
        return targetDriver.jdbcCompliant();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return targetDriver.getParentLogger();
    }

    /**
     * 获取目标驱动
     */
    public Driver getTargetDriver() {
        return targetDriver;
    }

    /**
     * 获取类加载器
     */
    public DriverClassLoader getClassLoader() {
        return classLoader;
    }
}
