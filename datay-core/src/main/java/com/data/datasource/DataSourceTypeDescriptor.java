package com.data.datasource;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据源类型描述元数据。
 *
 * <p>由各 {@link DataSourceProvider} 声明，供注册表与前端数据源表单消费。
 * 非 JDBC 数据源（如 Elasticsearch）将 {@link #isJdbc()} 置为 {@code false}，
 * 驱动、JDBC URL 模板等字段留空。
 */
public class DataSourceTypeDescriptor {

    private String type;
    private String displayName;
    private boolean jdbc = true;
    private List<String> supportedVersions = new ArrayList<>();
    private String defaultPort;
    private String driverClassName;
    private String jdbcUrlTemplate;
    private boolean downloadable;

    public String getType() {
        return type;
    }

    public DataSourceTypeDescriptor setType(String type) {
        this.type = type;
        return this;
    }

    public String getDisplayName() {
        return displayName;
    }

    public DataSourceTypeDescriptor setDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public boolean isJdbc() {
        return jdbc;
    }

    public DataSourceTypeDescriptor setJdbc(boolean jdbc) {
        this.jdbc = jdbc;
        return this;
    }

    public List<String> getSupportedVersions() {
        return supportedVersions;
    }

    public DataSourceTypeDescriptor setSupportedVersions(List<String> supportedVersions) {
        this.supportedVersions = supportedVersions == null ? new ArrayList<>() : supportedVersions;
        return this;
    }

    public String getDefaultPort() {
        return defaultPort;
    }

    public DataSourceTypeDescriptor setDefaultPort(String defaultPort) {
        this.defaultPort = defaultPort;
        return this;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public DataSourceTypeDescriptor setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
        return this;
    }

    public String getJdbcUrlTemplate() {
        return jdbcUrlTemplate;
    }

    public DataSourceTypeDescriptor setJdbcUrlTemplate(String jdbcUrlTemplate) {
        this.jdbcUrlTemplate = jdbcUrlTemplate;
        return this;
    }

    public boolean isDownloadable() {
        return downloadable;
    }

    public DataSourceTypeDescriptor setDownloadable(boolean downloadable) {
        this.downloadable = downloadable;
        return this;
    }
}
