package com.data.datafusion.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从模型回复中抽取最终 SQL。
 *
 * <p>模型输出并不总是规整的 markdown，因此这里按「代码块 → 裸 SQL 开头」的顺序兜底解析，
 * 保证前端始终能拿到一段可直接落进编辑器的 SQL。抽取范围随生成模式变化：
 * 查询模式只认 SELECT/WITH，任务模式还接受 DDL/DML 等写语句。
 */
public final class SqlExtractor {

    /** 匹配 ```sql ... ``` 或 ``` ... ``` 代码块。 */
    private static final Pattern FENCE = Pattern.compile("```(?:sql|SQL)?\\s*\\n?(.*?)```", Pattern.DOTALL);

    /** 匹配以常见 SQL 关键字开头的裸语句（含写操作，供任务模式使用）。 */
    private static final Pattern BARE_SQL = Pattern.compile(
        "(?is)((?:WITH|SELECT|INSERT|UPDATE|DELETE|MERGE|REPLACE|CREATE|ALTER|DROP|TRUNCATE|COMMENT|GRANT|REVOKE|RENAME|CALL)\\b.*?)(?:;|\\n\\s*\\n|$)"
    );

    /** 只读查询允许的前缀关键字。 */
    private static final List<String> READ_ONLY_KEYWORDS = List.of("SELECT", "WITH");

    /** 任务模式允许的前缀关键字。 */
    private static final List<String> TASK_KEYWORDS = List.of(
        "SELECT",
        "WITH",
        "INSERT",
        "UPDATE",
        "DELETE",
        "MERGE",
        "REPLACE",
        "CREATE",
        "ALTER",
        "DROP",
        "TRUNCATE",
        "COMMENT",
        "GRANT",
        "REVOKE",
        "RENAME",
        "CALL"
    );

    private SqlExtractor() {}

    /**
     * 抽取只读查询 SQL，抽取失败时返回 null。
     */
    public static String extract(String content) {
        return extract(content, AiSqlMode.QUERY);
    }

    /**
     * 按指定模式抽取 SQL，抽取失败时返回 null。
     */
    public static String extract(String content, AiSqlMode mode) {
        if (content == null || content.isBlank()) {
            return null;
        }
        AiSqlMode effectiveMode = mode == null ? AiSqlMode.QUERY : mode;

        List<String> candidates = new ArrayList<>();
        Matcher fenceMatcher = FENCE.matcher(content);
        while (fenceMatcher.find()) {
            String candidate = fenceMatcher.group(1);
            if (candidate != null && !candidate.isBlank()) {
                candidates.add(candidate.trim());
            }
        }

        for (String candidate : candidates) {
            if (looksLikeSql(candidate, effectiveMode)) {
                return normalize(candidate);
            }
        }
        // 代码块内容不像 SQL 时，再尝试裸语句兜底
        Matcher bareMatcher = BARE_SQL.matcher(content);
        while (bareMatcher.find()) {
            String candidate = bareMatcher.group(1);
            if (candidate != null && looksLikeSql(candidate, effectiveMode)) {
                return normalize(candidate);
            }
        }
        return null;
    }

    /**
     * 判断候选文本是否属于当前模式允许的 SQL。
     */
    public static boolean looksLikeSql(String text, AiSqlMode mode) {
        if (text == null) {
            return false;
        }
        String upper = text.trim().toUpperCase();
        List<String> keywords = (mode == null || !mode.isTask()) ? READ_ONLY_KEYWORDS : TASK_KEYWORDS;
        for (String keyword : keywords) {
            if (upper.startsWith(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String sql) {
        String trimmed = sql.trim();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}
