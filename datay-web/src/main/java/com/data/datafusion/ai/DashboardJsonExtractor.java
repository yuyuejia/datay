package com.data.datafusion.ai;

/**
 * 看板 JSON 抽取器。
 *
 * @deprecated 使用通用的 {@link JsonObjectExtractor}。
 */
@Deprecated
public final class DashboardJsonExtractor {

    private DashboardJsonExtractor() {}

    /**
     * 抽取 JSON 对象字符串，失败返回 null。
     */
    public static String extract(String content) {
        return JsonObjectExtractor.extract(content);
    }
}
