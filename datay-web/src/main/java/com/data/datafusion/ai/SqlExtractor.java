package com.data.datafusion.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从模型回复中抽取最终 SQL。
 *
 * <p>模型输出并不总是规整的 markdown，因此这里按「围栏代码块 → 行内代码 → 裸语句」
 * 的优先级兜底解析，保证前端始终能拿到可直接落进编辑器的 SQL。
 *
 * <p>抽取到多条语句时会全部保留，并以分号分隔后一并返回，避免只展示第一条而丢失后续脚本。
 * 抽取范围由调用方给出：只读场景只认 SELECT/WITH，写场景还接受 DDL/DML 等写语句。
 */
public final class SqlExtractor {

    /** 匹配 ```sql ... ``` 或 ``` ... ``` 围栏代码块。 */
    private static final Pattern FENCE = Pattern.compile("```(?:sql|SQL)?[ \t]*\n?(.*?)```", Pattern.DOTALL);

    /** 匹配单反引号包裹的行内代码，如 `SELECT ...`。 */
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`\\n]+)`");

    /** 匹配以常见 SQL 关键字开头的裸语句，用于无代码块时的兜底。 */
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
        return extract(content, false);
    }

    /**
     * 按指定范围抽取 SQL，抽取失败时返回 null。多条语句会全部返回并以分号分隔。
     *
     * @param allowWrites 为 true 时同时接受 DDL/DML 等写语句
     */
    public static String extract(String content, boolean allowWrites) {
        if (content == null || content.isBlank()) {
            return null;
        }

        // 1) 优先围栏代码块：其中可能承载多语句脚本
        List<String> statements = collect(FENCE, content, allowWrites);
        // 2) 其次行内代码：模型有时把 SQL 写成 `...`
        if (statements.isEmpty()) {
            statements = collect(INLINE_CODE, content, allowWrites);
        }
        // 3) 最后裸语句兜底
        if (statements.isEmpty()) {
            statements = collect(BARE_SQL, content, allowWrites);
        }
        if (statements.isEmpty()) {
            return null;
        }
        // 统一以分号收尾：语句之间用分号分隔，最后一条也补上分号
        return String.join(";\n\n", statements) + ";";
    }

    /**
     * 用指定模式从文本中收集所有像 SQL 的片段。
     */
    private static List<String> collect(Pattern pattern, String content, boolean allowWrites) {
        List<String> statements = new ArrayList<>();
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String candidate = clean(matcher.group(1));
            if (candidate != null && looksLikeSql(candidate, allowWrites)) {
                statements.add(normalize(candidate));
            }
        }
        return statements;
    }

    /**
     * 判断候选文本是否属于当前范围允许的 SQL，会先跳过开头的 SQL 注释。
     */
    public static boolean looksLikeSql(String text, boolean allowWrites) {
        if (text == null) {
            return false;
        }
        String upper = stripLeadingComments(text).toUpperCase();
        List<String> keywords = allowWrites ? TASK_KEYWORDS : READ_ONLY_KEYWORDS;
        for (String keyword : keywords) {
            if (upper.startsWith(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 去掉开头的空白与 SQL 注释（行注释与块注释），
     * 便于识别以注释开头、随后才是真实语句的脚本。
     */
    private static String stripLeadingComments(String text) {
        String remaining = text.stripLeading();
        while (!remaining.isEmpty()) {
            if (remaining.startsWith("--")) {
                int newline = remaining.indexOf('\n');
                if (newline < 0) {
                    return "";
                }
                remaining = remaining.substring(newline + 1).stripLeading();
            } else if (remaining.startsWith("/*")) {
                int end = remaining.indexOf("*/");
                if (end < 0) {
                    return "";
                }
                remaining = remaining.substring(end + 2).stripLeading();
            } else {
                break;
            }
        }
        return remaining;
    }

    /**
     * 清理片段外围的反引号与空白。
     */
    private static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim();
        while (cleaned.startsWith("`")) {
            cleaned = cleaned.substring(1).trim();
        }
        while (cleaned.endsWith("`")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        return cleaned.isBlank() ? null : cleaned;
    }

    private static String normalize(String sql) {
        String trimmed = sql.trim();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}
