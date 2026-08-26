package com.data.job.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SQLUtils {
    /**
     * 判断是否为SELECT语句
     */
    public static boolean isSelectStatement(String sql) {
        String trimmed = sql.trim().toLowerCase();
        return trimmed.startsWith("select") || trimmed.startsWith("with") || trimmed.startsWith("(select") || trimmed.startsWith(" values");
    }

    /**
     * 判断是否为DML语句
     */
    public static boolean isDmlStatement(String sql) {
        String trimmed = sql.trim().toLowerCase();
        return (
                trimmed.startsWith("insert") ||
                        trimmed.startsWith("update") ||
                        trimmed.startsWith("delete") ||
                        trimmed.startsWith("merge") ||
                        trimmed.startsWith("copy")
        );
    }

    /**
     * 判断是否为DDL语句
     */
    public static boolean isDdlStatement(String sql) {
        String trimmed = sql.trim().toLowerCase();
        return (
                trimmed.startsWith("create") ||
                        trimmed.startsWith("alter") ||
                        trimmed.startsWith("drop") ||
                        trimmed.startsWith("truncate") ||
                        trimmed.startsWith("rename")
        );
    }

    /**
     * 将Spark SQL语法转换为DuckDB语法
     */
    public static String convertSparkToDuckDb(String sparkSql) {
        String duckDbSql = sparkSql;

        // 1. 去除反引号（`），仅保留标识符名称
        // 使用正则表达式匹配反引号包围的标识符，并去除反引号
        duckDbSql = duckDbSql.replaceAll("`([^`]+)`", "$1");

        // 2. 处理常见的函数名称差异
        Map<String, String> functionMap = new HashMap<>();
        functionMap.put("date_format", "strftime");
        functionMap.put("current_timestamp\\(\\)", "now()");
        //snowflake() 转换成duckdb的uuid
        functionMap.put("snowflake\\(\\)", "uuidv7()");
        functionMap.put("unix_timestamp\\(\\)", "unixepoch()");
        functionMap.put("unix_timestamp\\(([^)]+)\\)", "unixepoch($1)");
        functionMap.put("from_unixtime\\(([^)]+)\\)", "epoch_ms_to_datetime($1 * 1000)");
        functionMap.put("from_unixtime\\(([^,]+),([^)]+)\\)", "strftime(epoch_ms_to_datetime($1 * 1000), $2)");
        functionMap.put("concat_ws\\(([^,]+),([^)]+)\\)", "string_agg($2, $1)");
        functionMap.put("collect_list\\(([^)]+)\\)", "list_agg($1)");
        functionMap.put("collect_set\\(([^)]+)\\)", "set_agg($1)");
        functionMap.put("array_contains\\(([^,]+),([^)]+)\\)", "list_contains($1, $2)");
        functionMap.put("size\\(([^)]+)\\)", "len($1)");
        functionMap.put("substr\\(", "substring(");
        functionMap.put("lower\\(", "lcase(");
        functionMap.put("upper\\(", "ucase(");
        functionMap.put("lpad\\(([^,]+),([^,]+),([^)]+)\\)", "lpad($1, $2::INTEGER, $3)");
        functionMap.put("rpad\\(([^,]+),([^,]+),([^)]+)\\)", "rpad($1, $2::INTEGER, $3)");
        // ========== 新增：日期函数转换 ==========
        // 先处理复杂的strftime嵌套函数转换
        // 处理 strftime(add_months(current_date(), 1), 'yyyy-MM') -> date_format(current_date() + INTERVAL '1' MONTH, 'yyyy-MM')
        functionMap.put("strftime\\(add_months\\(([^,]+),\\s*([^)]+)\\),\\s*'([^']+)'\\)", "date_format($1 + INTERVAL '$2' MONTH, '$3')");
        functionMap.put("strftime\\(add_months\\(current_date\\(\\),\\s*([^)]+)\\),\\s*'([^']+)'\\)", "date_format(current_date() + INTERVAL '$1' MONTH, '$2')");
        functionMap.put("strftime\\(add_months\\(now\\(\\),\\s*([^)]+)\\),\\s*'([^']+)'\\)", "date_format(now() + INTERVAL '$1' MONTH, '$2')");
        
        // 处理其他常见的日期函数嵌套
        functionMap.put("strftime\\(date_add\\(([^,]+),\\s*([^)]+)\\),\\s*'([^']+)'\\)", "date_format($1 + INTERVAL '$2' DAY, '$3')");
        functionMap.put("strftime\\(date_sub\\(([^,]+),\\s*([^)]+)\\),\\s*'([^']+)'\\)", "date_format($1 - INTERVAL '$2' DAY, '$3')");
        
        // 处理简单的strftime函数转换（Spark的strftime转换为DuckDB的date_format）
        functionMap.put("strftime\\(([^,]+),\\s*'([^']+)'\\)", "date_format($1, '$2')");
        
        // 处理add_months函数单独使用的情况
        functionMap.put("add_months\\(([^,]+),\\s*([^)]+)\\)", "$1 + INTERVAL '$2' MONTH");
        functionMap.put("add_months\\(current_date\\(\\),\\s*([^)]+)\\)", "current_date() + INTERVAL '$1' MONTH");
        functionMap.put("add_months\\(now\\(\\),\\s*([^)]+)\\)", "now() + INTERVAL '$1' MONTH");
        
        // 处理其他日期函数
        functionMap.put("date_add\\(([^,]+),\\s*([^)]+)\\)", "$1 + INTERVAL '$2' DAY");
        functionMap.put("date_sub\\(([^,]+),\\s*([^)]+)\\)", "$1 - INTERVAL '$2' DAY");
        // ========== 新增：处理 'true'/'false' 字符串布尔比较 ==========
        // 规则5：col = 'true'（忽略大小写、空格、反向比较）-> col = TRUE
        functionMap.put(
                "(\\S+)\\s*=\\s*'([Tt][Rr][Uu][Ee])'|'([Tt][Rr][Uu][Ee])'\\s*=\\s*(\\S+)",
                "$1$4 = TRUE"
        );
        // 规则6：col = 'false'（忽略大小写、空格、反向比较）-> col = FALSE
        functionMap.put(
                "(\\S+)\\s*=\\s*'([Ff][Aa][Ll][Ss][Ee])'|'([Ff][Aa][Ll][Ss][Ee])'\\s*=\\s*(\\S+)",
                "$1$4 = FALSE"
        );
        // 规则7：col != 'true' / col <> 'true'（忽略大小写）-> col != TRUE / col <> TRUE
        functionMap.put(
                "(\\S+)\\s*(!=|<>|!=)\\s*'([Tt][Rr][Uu][Ee])'|'([Tt][Rr][Uu][Ee])'\\s*(!=|<>|!=)\\s*(\\S+)",
                "$1$6 $2$5 TRUE"
        );
        // 规则8：col != 'false' / col <> 'false'（忽略大小写）-> col != FALSE / col <> FALSE
        functionMap.put(
                "(\\S+)\\s*(!=|<>|!=)\\s*'([Ff][Aa][Ll][Ss][Ee])'|'([Ff][Aa][Ll][Ss][Ee])'\\s*(!=|<>|!=)\\s*(\\S+)",
                "$1$6 $2$5 FALSE"
        );

        // 应用函数转换
        for (Map.Entry<String, String> entry : functionMap.entrySet()) {
            String sparkFunc = entry.getKey();
            String duckDbFunc = entry.getValue();
            duckDbSql = duckDbSql.replaceAll("(?i)" + sparkFunc, duckDbFunc);
        }

//        // 4. 处理数据类型差异
//        Map<String, String> typeMap = new HashMap<>();
//        typeMap.put("INT", "INTEGER");
//        typeMap.put("BIGINT", "BIGINT");
//        typeMap.put("DOUBLE", "DOUBLE");
//        typeMap.put("STRING", "VARCHAR");
//        typeMap.put("BOOLEAN", "BOOLEAN");
//        typeMap.put("DATE", "DATE");
//        typeMap.put("TIMESTAMP", "TIMESTAMP");
//        typeMap.put("ARRAY<([^>]+)>", "LIST<$1>");
//        typeMap.put("MAP<([^,]+),([^>]+)>", "MAP<$1, $2>");
//        typeMap.put("STRUCT<([^>]+)>", "STRUCT<$1>");
//
//        // 应用数据类型转换
//        for (Map.Entry<String, String> entry : typeMap.entrySet()) {
//            String sparkType = entry.getKey();
//            String duckDbType = entry.getValue();
//            duckDbSql = duckDbSql.replaceAll("(?i)" + sparkType, duckDbType);
//        }

        // 5. 处理LATERAL VIEW explode语法
        // Spark: LATERAL VIEW explode(array_col) AS exploded_col
        // DuckDB: CROSS JOIN UNNEST(array_col) AS t(exploded_col)
        duckDbSql = duckDbSql.replaceAll("(?i)LATERAL VIEW explode\\(([^)]+)\\)\\s+AS\\s+([a-zA-Z0-9_]+)",
                "CROSS JOIN UNNEST($1) AS t($2)");

        // 处理窗口函数的ROWS BETWEEN语法（DuckDB默认支持相同语法，但确保兼容性）
        // 处理OVER (PARTITION BY ... ORDER BY ...) 语法（通常两者兼容）

        return duckDbSql;
    }

    //去除sql中的注释
    public static String removeComments(String sql) {
        // 第一步：按行处理，去除行注释（-- 开始到行尾）
        StringBuilder cleanedSql = new StringBuilder();
        String[] lines = sql.split("\n");

        for (String line : lines) {
            // 查找行注释开始位置
            int commentStart = -1;
            boolean inSingleQuote = false;
            boolean inDoubleQuote = false;

            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);

                // 处理引号状态
                if (c == '\'' && !inDoubleQuote) {
                    inSingleQuote = !inSingleQuote;
                } else if (c == '"' && !inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote;
                }

                // 检查行注释开始（--），但不在引号内
                if (!inSingleQuote && !inDoubleQuote && i < line.length() - 1 &&
                        c == '-' && line.charAt(i + 1) == '-') {
                    commentStart = i;
                    break;
                }
            }

            // 如果找到行注释，只保留注释之前的部分
            if (commentStart >= 0) {
                cleanedSql.append(line.substring(0, commentStart).trim());
            } else {
                cleanedSql.append(line);
            }
            cleanedSql.append("\n");
        }

        // 第二步：处理多行注释（/* */）
        StringBuilder finalSql = new StringBuilder();
        boolean inMultiLineComment = false;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        String processedSql = cleanedSql.toString();
        for (int i = 0; i < processedSql.length(); i++) {
            char c = processedSql.charAt(i);

            // 处理多行注释开始（/*）
            if (!inSingleQuote && !inDoubleQuote && !inMultiLineComment &&
                    i < processedSql.length() - 1 && c == '/' && processedSql.charAt(i + 1) == '*') {
                inMultiLineComment = true;
                i++; // 跳过下一个字符 '*'
                continue;
            }

            // 处理多行注释结束（*/）
            if (inMultiLineComment && i < processedSql.length() - 1 &&
                    c == '*' && processedSql.charAt(i + 1) == '/') {
                inMultiLineComment = false;
                i++; // 跳过下一个字符 '/'
                continue;
            }

            // 处理引号状态（不在多行注释中）
            if (!inMultiLineComment) {
                if (c == '\'' && !inDoubleQuote) {
                    inSingleQuote = !inSingleQuote;
                } else if (c == '"' && !inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote;
                }
            }

            // 只添加不在多行注释中的字符
            if (!inMultiLineComment) {
                finalSql.append(c);
            }
        }

        return finalSql.toString();
    }


    /**
     * 分割SQL语句（按分号分割）
     */
    public static List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        if (sql == null || sql.trim().isEmpty()) {
            return statements;
        }

        // 第三步：按分号分割语句，忽略引号内的分号
        StringBuilder currentStatement = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        String cleanedSql = removeComments(sql);
        for (int i = 0; i < cleanedSql.length(); i++) {
            char c = cleanedSql.charAt(i);

            // 处理引号状态
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            }

            // 如果是分号且不在引号内，则分割语句
            if (c == ';' && !inSingleQuote && !inDoubleQuote) {
                String statement = currentStatement.toString().trim();
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
                currentStatement.setLength(0);
            } else {
                currentStatement.append(c);
            }
        }

        // 处理最后一条语句（如果没有分号结尾）
        String lastStatement = currentStatement.toString().trim();
        if (!lastStatement.isEmpty()) {
            statements.add(lastStatement);
        }

        return statements;
    }
}