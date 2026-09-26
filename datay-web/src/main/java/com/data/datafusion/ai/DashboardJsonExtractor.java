package com.data.datafusion.ai;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从模型回复中抽取最终看板定义 JSON。
 *
 * <p>模型通常会把 spec 放在 ```json 代码块中，但也可能带上自然语言说明。
 * 这里按「首个包含对象结构的围栏代码块 → 裸 JSON 对象」优先级兜底解析，
 * 返回从首个 <code>{</code> 到最后一个 <code>}</code> 的子串。
 */
public final class DashboardJsonExtractor {

    /** 匹配围栏代码块（可带 json/javascript 等语言标注）。 */
    private static final Pattern FENCE_PATTERN = Pattern.compile("```[ \\t]*([A-Za-z]*)[ \\t]*\\n([\\s\\S]*?)```");

    private DashboardJsonExtractor() {}

    /**
     * 抽取 JSON 对象字符串，失败返回 null。
     */
    public static String extract(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        Matcher matcher = FENCE_PATTERN.matcher(content);
        while (matcher.find()) {
            String candidate = sliceObject(matcher.group(2).trim());
            if (candidate != null) {
                return candidate;
            }
        }
        return sliceObject(content);
    }

    private static String sliceObject(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }
}
