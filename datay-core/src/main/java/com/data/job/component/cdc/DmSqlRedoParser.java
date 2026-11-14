package com.data.job.component.cdc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 达梦 {@code V$LOGMNR_CONTENTS.SQL_REDO} 解析工具。
 *
 * <p>达梦 {@code MINE_VALUE} 对 UPDATE 只返回变更列，未变更列（包括主键）返回 null；但
 * {@code RLOG_APPEND_LOGIC=2} 时 SQL_REDO 的 WHERE 子句包含完整的旧行镜像，SET 子句包含
 * 变更后的列值。这里解析 SQL_REDO 以补全 UPDATE 前后镜像，并在 MINE_VALUE 不可用时为
 * INSERT/DELETE 提供兜底。
 *
 * <p>解析结果按列名大写存储；SQL 中的 {@code NULL} 使用 map 中的 null 值表示，缺失列用
 * {@link Map#containsKey(Object)} 区分。
 */
final class DmSqlRedoParser {

    private DmSqlRedoParser() {}

    /**
     * 解析 UPDATE 的 SET 子句，返回 变更列 -> 新值。
     */
    static Map<String, String> parseSetValues(String sqlRedo) {
        if (sqlRedo == null || sqlRedo.isEmpty()) {
            return Collections.emptyMap();
        }
        int setIndex = indexOfKeyword(sqlRedo, "SET", 0);
        if (setIndex < 0) {
            return Collections.emptyMap();
        }
        int whereIndex = indexOfKeyword(sqlRedo, "WHERE", setIndex + 3);
        String setClause = whereIndex < 0
            ? sqlRedo.substring(setIndex + 3)
            : sqlRedo.substring(setIndex + 3, whereIndex);
        return parseAssignments(splitTopLevel(setClause, ','), false);
    }

    /**
     * 解析 UPDATE/DELETE 的 WHERE 子句，返回 旧列 -> 旧值（忽略 ROWID）。
     */
    static Map<String, String> parseWhereValues(String sqlRedo) {
        if (sqlRedo == null || sqlRedo.isEmpty()) {
            return Collections.emptyMap();
        }
        int whereIndex = indexOfKeyword(sqlRedo, "WHERE", 0);
        if (whereIndex < 0) {
            return Collections.emptyMap();
        }
        String whereClause = sqlRedo.substring(whereIndex + 5);
        return parseAssignments(splitByAnd(whereClause), true);
    }

    /**
     * 解析 INSERT 的列清单和 VALUES，返回 列 -> 值。
     */
    static Map<String, String> parseInsertValues(String sqlRedo) {
        if (sqlRedo == null || sqlRedo.isEmpty()) {
            return Collections.emptyMap();
        }
        int valuesIndex = indexOfKeyword(sqlRedo, "VALUES", 0);
        if (valuesIndex < 0) {
            return Collections.emptyMap();
        }
        int columnsOpen = indexOfChar(sqlRedo, '(', 0);
        if (columnsOpen < 0 || columnsOpen > valuesIndex) {
            return Collections.emptyMap();
        }
        int columnsClose = matchingParen(sqlRedo, columnsOpen);
        if (columnsClose < 0 || columnsClose > valuesIndex) {
            return Collections.emptyMap();
        }
        int valuesOpen = indexOfChar(sqlRedo, '(', valuesIndex);
        if (valuesOpen < 0) {
            return Collections.emptyMap();
        }
        int valuesClose = matchingParen(sqlRedo, valuesOpen);
        if (valuesClose < 0) {
            return Collections.emptyMap();
        }

        String[] columns = splitTopLevel(sqlRedo.substring(columnsOpen + 1, columnsClose), ',').toArray(new String[0]);
        String[] values = splitTopLevel(sqlRedo.substring(valuesOpen + 1, valuesClose), ',').toArray(new String[0]);
        if (columns.length != values.length) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < columns.length; i++) {
            String name = cleanIdentifier(columns[i]);
            if (name == null || name.isEmpty()) {
                continue;
            }
            result.put(name.toUpperCase(Locale.ROOT), cleanValue(values[i]));
        }
        return result;
    }

    private static Map<String, String> parseAssignments(List<String> parts, boolean where) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) {
                continue;
            }
            int equals = indexOfChar(part, '=', 0);
            if (equals < 0) {
                continue;
            }
            String name = cleanIdentifier(part.substring(0, equals));
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (where && "ROWID".equalsIgnoreCase(name)) {
                continue;
            }
            result.put(name.toUpperCase(Locale.ROOT), cleanValue(part.substring(equals + 1)));
        }
        return result;
    }

    /**
     * 按分隔符拆分，忽略单引号、双引号和括号内部的分隔符。
     */
    static List<String> splitTopLevel(String text, char delimiter) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'') {
                current.append(c);
                if (inSingleQuote && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    current.append(text.charAt(++i));
                } else {
                    inSingleQuote = !inSingleQuote;
                }
                continue;
            }
            if (!inSingleQuote) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                } else if (c == delimiter && depth == 0) {
                    result.add(current.toString());
                    current.setLength(0);
                    continue;
                }
            }
            current.append(c);
        }
        result.add(current.toString());
        return result;
    }

    /**
     * 按顶层 AND 拆分 WHERE 条件。
     */
    static List<String> splitByAnd(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        int start = 0;
        int index = indexOfKeyword(text, "AND", 0);
        while (index >= 0) {
            result.add(text.substring(start, index));
            start = index + 3;
            index = indexOfKeyword(text, "AND", start);
        }
        result.add(text.substring(start));
        return result;
    }

    /**
     * 查找顶层关键字（忽略引号和括号中的内容），返回关键字起始下标。
     */
    static int indexOfKeyword(String text, String keyword, int from) {
        if (text == null) {
            return -1;
        }
        boolean inSingleQuote = false;
        int depth = 0;
        for (int i = Math.max(0, from); i <= text.length() - keyword.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'') {
                if (inSingleQuote && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    i++;
                } else {
                    inSingleQuote = !inSingleQuote;
                }
                continue;
            }
            if (inSingleQuote) {
                continue;
            }
            if (c == '(') {
                depth++;
                continue;
            }
            if (c == ')') {
                depth--;
                continue;
            }
            if (depth != 0) {
                continue;
            }
            if (text.regionMatches(true, i, keyword, 0, keyword.length()) && isBoundary(text, i - 1) && isBoundary(text, i + keyword.length())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isBoundary(String text, int index) {
        if (index < 0 || index >= text.length()) {
            return true;
        }
        char c = text.charAt(index);
        return !(Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#');
    }

    private static int indexOfChar(String text, char target, int from) {
        boolean inSingleQuote = false;
        for (int i = Math.max(0, from); i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'') {
                if (inSingleQuote && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    i++;
                } else {
                    inSingleQuote = !inSingleQuote;
                }
                continue;
            }
            if (!inSingleQuote && c == target) {
                return i;
            }
        }
        return -1;
    }

    private static int matchingParen(String text, int openIndex) {
        boolean inSingleQuote = false;
        int depth = 0;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'') {
                if (inSingleQuote && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    i++;
                } else {
                    inSingleQuote = !inSingleQuote;
                }
                continue;
            }
            if (inSingleQuote) {
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String cleanIdentifier(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        int dot = value.lastIndexOf('.');
        if (dot >= 0) {
            value = value.substring(dot + 1);
        }
        value = value.trim();
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            value = value.substring(1, value.length() - 1).replace("\"\"", "\"");
        }
        return value;
    }

    /**
     * 清理 SQL 字面量：去掉字符串引号、类型前缀和末尾分号；{@code NULL} 返回 null。
     */
    static String cleanValue(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.endsWith(";")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        if (value.isEmpty()) {
            return "";
        }
        if ("NULL".equalsIgnoreCase(value)) {
            return null;
        }
        String literal = firstStringLiteral(value);
        if (literal != null) {
            return literal;
        }
        return value;
    }

    /**
     * 提取第一个单引号字符串字面量，支持 {@code ''} 转义。
     */
    private static String firstStringLiteral(String value) {
        int start = value.indexOf('\'');
        if (start < 0) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        int i = start + 1;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c == '\'') {
                if (i + 1 < value.length() && value.charAt(i + 1) == '\'') {
                    result.append('\'');
                    i += 2;
                    continue;
                }
                return result.toString();
            }
            result.append(c);
            i++;
        }
        return null;
    }
}
