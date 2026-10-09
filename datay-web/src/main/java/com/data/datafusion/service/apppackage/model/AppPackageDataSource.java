package com.data.datafusion.service.apppackage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 资产包中的数据源条目。
 *
 * <p>导出时保留完整连接信息（含密码），以便目标租户一键初始化；导入时按
 * {@link #key}（名称 + 类型 + 连接地址）与目标租户已有数据源匹配，命中则复用，未命中才新建。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppPackageDataSource {

    /** 包内逻辑 ID，即导出时来源租户的真实数据源 ID。 */
    public String oldId;

    /** 逻辑唯一键：{@code name|type|url}，用于跨租户匹配复用。 */
    public String key;

    public String name;

    public String description;

    public String type;

    public String url;

    public String hostname;

    public String port;

    public String schemaName;

    public String database;

    public String username;

    public String password;

    public String connectionMode;

    public Map<String, String> extraParams = new LinkedHashMap<>();

    /** 构建逻辑唯一键。 */
    public static String buildKey(String name, String type, String url) {
        return String.join("|", nullToEmpty(name), nullToEmpty(type), nullToEmpty(url));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
