package com.data.datafusion.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从模型回复中抽取最终 SQL。
 *
 * <p>模型输出并不总是规整的 markdown，因此这里按「代码块 → 裸 SQL 开头」的顺序兜底解析，
 * 保证前端始终能拿到一段可直接落进编辑器的 SQL。
 */
public final class SqlExtractor {

    /** 匹配 ```sql ... ``` 或 ``` ... ``` 代码块。 */
    private static final Pattern FENCE = Pattern.compile("```(?:sql|SQL)?\\s*\\n?(.*?)```", Pattern.DOTALL);

    /** 匹配以常见只读 SQL 关键字开头的裸语句。 */
    private static final Pattern BARE_SQL = Pattern.compile(
        "(?is)((?:WITH|SELECT)\\b.*?)(?:;|\\n\\s*\\n|$)"
    );

    private SqlExtractor() {}

    /**
     * 抽取回复中的 SQL 语句，抽取失败时返回 null。
     */
    public static String extract(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        List<String> candidates = new ArrayList<>();
        Matcher fenceMatcher = FENCE.matcher(content);
        while (fenceMatcher.find()) {
            String candidate = fenceMatcher.group(1);
            if (candidate != null && !candidate.isBlank()) {
                candidates.add(candidate.trim());
            }
        }

        for (String candidate : candidates) {
            if (looksLikeSql(candidate)) {
                return normalize(candidate);
            }
        }
        // 代码块内容不像 SQL 时，再尝试裸语句兜底
        Matcher bareMatcher = BARE_SQL.matcher(content);
        if (bareMatcher.find()) {
            return normalize(bareMatcher.group(1));
        }
        return null;
    }

    /**
     * 粗略判断候选文本是否为只读 SQL。
     */
    public static boolean looksLikeSql(String text) {
        if (text == null) {
            return false;
        }
        String upper = text.trim().toUpperCase();
        return upper.startsWith("SELECT") || upper.startsWith("WITH");
    }

    private static String normalize(String sql) {
        String trimmed = sql.trim();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}
