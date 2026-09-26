package com.data.datafusion.service.dashboard;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 看板数据集 SQL 只读守卫。
 *
 * <p>看板物理表数据集允许由 AI 生成 SQL，但必须是单条只读查询。
 * 校验策略：
 * <ol>
 *     <li>忽略前导注释后，首个关键字必须为只读关键字（SELECT / WITH 等）；</li>
 *     <li>不允许出现多条语句（未加引号的分号）；</li>
 *     <li>在剔除字符串字面量、引号标识符与注释后，扫描危险的写操作关键字，
 *         并跳过函数调用形式（如 {@code REPLACE(...)}、{@code DATE_TRUNC(...)} 等合法只读函数）。</li>
 * </ol>
 */
public final class DashboardSqlGuard {

    private static final Pattern FIRST_KEYWORD = Pattern.compile(
        "^\\s*(?:(?:--[^\\n]*\\n|/\\*[\\s\\S]*?\\*/)\\s*)*([A-Za-z]+)"
    );

    /** 危险的语句级写操作关键字（不含 REPLACE/SET 等常见只读函数名）。 */
    private static final Pattern DML_KEYWORD = Pattern.compile(
        "(?i)\\b(insert|update|delete|merge|create|drop|alter|truncate|grant|revoke)\\b"
    );

    private static final Pattern READ_ONLY_FIRST = Pattern.compile("(?i)^(select|with|show|describe|desc|explain)$");

    private DashboardSqlGuard() {}

    /**
     * 校验 SQL 是否为单条只读查询，不合法时抛出 {@link IllegalArgumentException}。
     */
    public static String requireReadOnly(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("看板数据集 SQL 不能为空");
        }
        String trimmed = sql.trim();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        Matcher keywordMatcher = FIRST_KEYWORD.matcher(trimmed);
        String firstWord = keywordMatcher.find() ? keywordMatcher.group(1) : "";
        if (!READ_ONLY_FIRST.matcher(firstWord).matches()) {
            throw new IllegalArgumentException("看板数据集仅支持 SELECT 等只读查询");
        }
        // 单条语句：去掉字符串字面量后不允许出现分号
        if (containsStatementSeparator(trimmed)) {
            throw new IllegalArgumentException("看板数据集仅支持单条查询语句");
        }
        if (containsWriteStatement(maskLiteralsAndComments(trimmed))) {
            throw new IllegalArgumentException("看板数据集 SQL 不得包含写操作关键字");
        }
        return trimmed;
    }

    private static boolean containsWriteStatement(String maskedSql) {
        Matcher matcher = DML_KEYWORD.matcher(maskedSql);
        while (matcher.find()) {
            int cursor = matcher.end();
            while (cursor < maskedSql.length() && Character.isWhitespace(maskedSql.charAt(cursor))) {
                cursor++;
            }
            // 紧跟左括号的是函数调用（如 replace(...)），并非写语句
            if (cursor < maskedSql.length() && maskedSql.charAt(cursor) == '(') {
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean containsStatementSeparator(String sql) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == ';' && !inSingle && !inDouble) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将字符串字面量、引号标识符与注释替换为等长空格，便于后续按关键字扫描而不误伤。
     */
    private static String maskLiteralsAndComments(String sql) {
        StringBuilder masked = new StringBuilder(sql.length());
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inBacktick = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (inLineComment) {
                masked.append(c == '\n' ? '\n' : ' ');
                if (c == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                masked.append(' ');
                if (c == '*' && next == '/') {
                    masked.append(' ');
                    i++;
                    inBlockComment = false;
                }
                continue;
            }
            if (inSingle) {
                masked.append(c == '\'' && next == '\'' ? "  " : " ");
                if (c == '\'' && next == '\'') {
                    i++;
                } else if (c == '\'') {
                    inSingle = false;
                }
                continue;
            }
            if (inDouble) {
                masked.append(' ');
                if (c == '"' && next == '"') {
                    masked.append(' ');
                    i++;
                } else if (c == '"') {
                    inDouble = false;
                }
                continue;
            }
            if (inBacktick) {
                masked.append(' ');
                if (c == '`') {
                    inBacktick = false;
                }
                continue;
            }
            if (c == '-' && next == '-') {
                masked.append("  ");
                i++;
                inLineComment = true;
            } else if (c == '/' && next == '*') {
                masked.append("  ");
                i++;
                inBlockComment = true;
            } else if (c == '\'') {
                masked.append(' ');
                inSingle = true;
            } else if (c == '"') {
                masked.append(' ');
                inDouble = true;
            } else if (c == '`') {
                masked.append(' ');
                inBacktick = true;
            } else {
                masked.append(c);
            }
        }
        return masked.toString();
    }
}
