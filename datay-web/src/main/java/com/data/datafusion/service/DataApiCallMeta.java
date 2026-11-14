package com.data.datafusion.service;

import com.data.datafusion.domain.DataApiCallLog;

/**
 * 一次数据服务调用的入口信息，用于写入调用日志。
 *
 * @param source    调用来源：OPEN_API / MCP / INTERNAL
 * @param method    请求方法，MCP 或内部调用可为空
 * @param path      请求路径，MCP 或内部调用可为空
 * @param clientIp  调用方 IP，可为空
 * @param userAgent User-Agent，可为空
 */
public record DataApiCallMeta(String source, String method, String path, String clientIp, String userAgent) {
    public static DataApiCallMeta openApi(String method, String path, String clientIp, String userAgent) {
        return new DataApiCallMeta(DataApiCallLog.REQUEST_SOURCE_OPEN_API, method, path, clientIp, userAgent);
    }

    public static DataApiCallMeta mcp() {
        return new DataApiCallMeta(DataApiCallLog.REQUEST_SOURCE_MCP, "MCP", null, null, null);
    }

    public static DataApiCallMeta internal() {
        return new DataApiCallMeta(DataApiCallLog.REQUEST_SOURCE_INTERNAL, null, null, null, null);
    }
}
