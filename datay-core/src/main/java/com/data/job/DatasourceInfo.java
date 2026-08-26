package com.data.job;

import java.util.Map;

public class DatasourceInfo {

    private String type; // 数据库类型：mysql、postgresql等
    private String hostname; // 主机名
    private String port; // 端口
    private String url;
    private String username;
    private String password;
    private String driver;
    private String dbschema;
    private Map<String, String> extraParams; // 额外连接参数

    public void setUrl(String url) {
        this.url = url;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public void setDbschema(String dbschema) {
        this.dbschema = dbschema;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDriver() {
        return driver;
    }

    public String getDbschema() {
        return dbschema;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public Map<String, String> getExtraParams() {
        return extraParams;
    }

    public void setExtraParams(Map<String, String> extraParams) {
        this.extraParams = extraParams;
    }

    public void setDatabase(String database) {
        this.dbschema = database;
    }
}
