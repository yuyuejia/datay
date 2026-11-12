package com.data.metadata.util;

import java.util.List;

/**
 * TDengine JDBC 连接协议辅助方法。
 *
 * <p>TDengine 驱动在同一套 {@code taos-jdbcdriver} 中提供三个驱动类，需要按 JDBC URL
 * 选择：WebSocket（默认，端口 6041）、REST（6041）、原生（6030）。
 * 把 URL 判断与驱动类映射集中在这里，供 {@link DBUtils} 与数据源 Provider 复用。
 */
public final class TdengineJdbcSupport {

    public static final String URL_PREFIX_WS = "jdbc:TAOS-WS:";
    public static final String URL_PREFIX_RS = "jdbc:TAOS-RS:";
    public static final String URL_PREFIX_NATIVE = "jdbc:TAOS:";

    public static final String DRIVER_WS = "com.taosdata.jdbc.ws.WebSocketDriver";
    public static final String DRIVER_RS = "com.taosdata.jdbc.rs.RestfulDriver";
    public static final String DRIVER_NATIVE = "com.taosdata.jdbc.TSDBDriver";

    private TdengineJdbcSupport() {}

    /**
     * 三个协议前缀，注意 {@code TAOS-WS} / {@code TAOS-RS} 必须排在 {@code TAOS} 之前。
     */
    public static List<String> urlPrefixes() {
        return List.of(URL_PREFIX_WS, URL_PREFIX_RS, URL_PREFIX_NATIVE);
    }

    /**
     * 是否为 TDengine JDBC URL（大小写不敏感）。
     */
    public static boolean isTdengineUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        return startsWithIgnoreCase(url, URL_PREFIX_WS) ||
            startsWithIgnoreCase(url, URL_PREFIX_RS) ||
            startsWithIgnoreCase(url, URL_PREFIX_NATIVE);
    }

    /**
     * 按 URL 返回协议对应的驱动类；无法识别时默认 WebSocket。
     */
    public static String driverClassName(String url) {
        if (startsWithIgnoreCase(url, URL_PREFIX_RS)) {
            return DRIVER_RS;
        }
        if (startsWithIgnoreCase(url, URL_PREFIX_NATIVE) && !startsWithIgnoreCase(url, URL_PREFIX_WS)) {
            return DRIVER_NATIVE;
        }
        return DRIVER_WS;
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value != null && value.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
