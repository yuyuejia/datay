package com.data.job.quality;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 数据质量规则 SQL 编译器。
 *
 * <p>把任务/组件中的规则配置编译为可在 DuckDB 中执行的检核 SQL。每条检核 SQL 返回一行两列：
 * {@code checked_rows}（参与检核的行数）与 {@code invalid_rows}（不满足规则的行数）。</p>
 *
 * <p>规则配置 JSON 示例：</p>
 * <pre>
 * {
 *   "ruleCode": "FIELD_NOT_NULL",
 *   "ruleName": "订单号非空",
 *   "ruleLevel": "FIELD",
 *   "ruleType": "NOT_NULL",
 *   "column": "order_id",
 *   "config": { "trimWhitespace": true },
 *   "severity": "ERROR",
 *   "maxInvalidRows": 0,
 *   "maxInvalidRatio": 0
 * }
 * </pre>
 */
public class DataQualityRuleCompiler {

    private static final Pattern IDENTIFIER = Pattern.compile("^[\\p{L}_][\\p{L}\\p{N}_$]*$");

    private static final String DEFAULT_SEVERITY = "ERROR";

    /**
     * 编译规则列表。
     *
     * @param rules       规则配置，支持 JSON 字符串、JSONArray、Collection
     * @param sourceTable DuckDB 源表，例如 {@code main.dq_source}
     * @return 编译后的规则列表
     */
    public List<CompiledQualityRule> compile(Object rules, String sourceTable) {
        JSONArray array = resolveRules(rules);
        String table = quoteTable(sourceTable);
        List<CompiledQualityRule> compiled = new ArrayList<>();
        Set<String> usedKeys = new LinkedHashSet<>();
        for (int i = 0; i < array.size(); i++) {
            JSONObject rule = array.getJSONObject(i);
            if (rule == null) {
                continue;
            }
            CompiledQualityRule item = compileRule(rule, i, table, usedKeys);
            compiled.add(item);
        }
        if (compiled.isEmpty()) {
            throw new IllegalArgumentException("请至少配置一条数据质量规则");
        }
        return compiled;
    }

    /**
     * 编译规则列表（JSON 字符串重载）。
     */
    public List<CompiledQualityRule> compile(String rulesJson, String sourceTable) {
        return compile((Object) rulesJson, sourceTable);
    }

    /**
     * 解析规则列表，兼容 JSON 字符串、JSONArray、普通集合。
     */
    public JSONArray resolveRules(Object rules) {
        if (rules == null) {
            return new JSONArray();
        }
        if (rules instanceof JSONArray) {
            return (JSONArray) rules;
        }
        if (rules instanceof String) {
            String text = ((String) rules).trim();
            return text.isEmpty() ? new JSONArray() : JSONArray.parseArray(text);
        }
        if (rules instanceof Collection<?>) {
            JSONArray array = new JSONArray();
            for (Object item : (Collection<?>) rules) {
                if (item == null) {
                    continue;
                }
                if (item instanceof JSONObject) {
                    array.add(item);
                } else {
                    array.add(JSON.parseObject(JSON.toJSONString(item)));
                }
            }
            return array;
        }
        throw new IllegalArgumentException("规则配置格式错误: " + rules.getClass().getName());
    }

    private CompiledQualityRule compileRule(JSONObject rule, int index, String table, Set<String> usedKeys) {
        QualityRuleType type = QualityRuleType.of(rule.getString("ruleType"));
        String column = trimToNull(rule.getString("column"));
        String ruleKey = firstNonBlank(rule.getString("ruleCode"), rule.getString("ruleId"), type.getCode() + "_" + (index + 1));
        ruleKey = uniqueKey(ruleKey, usedKeys);
        String ruleName = firstNonBlank(rule.getString("ruleName"), type.getLabel());
        String severity = normalizeSeverity(rule.getString("severity"));
        long maxInvalidRows = longValue(rule.get("maxInvalidRows"), 0L);
        double maxInvalidRatio = doubleValue(rule.get("maxInvalidRatio"), 0.0D);
        JSONObject config = rule.getJSONObject("config");
        if (config == null) {
            config = new JSONObject();
        }

        String columnLabel = column;
        String checkSql;
        String sampleCondition = null;
        switch (type) {
            case ROW_COUNT_MIN -> {
                long minRows = longValue(config.get("minRows"), 1L);
                checkSql =
                    "SELECT COUNT(*) AS checked_rows, " +
                    "CASE WHEN COUNT(*) < " +
                    minRows +
                    " THEN 1 ELSE 0 END AS invalid_rows FROM " +
                    table;
            }
            case ROW_COUNT_RANGE -> {
                long minRows = longValue(config.get("minRows"), 0L);
                Long maxRows = optionalLong(config.get("maxRows"));
                StringBuilder condition = new StringBuilder("COUNT(*) < ").append(minRows);
                if (maxRows != null) {
                    condition.append(" OR COUNT(*) > ").append(maxRows);
                }
                checkSql =
                    "SELECT COUNT(*) AS checked_rows, CASE WHEN " +
                    condition +
                    " THEN 1 ELSE 0 END AS invalid_rows FROM " +
                    table;
            }
            case COMPOSITE_UNIQUE -> {
                List<String> columns = parseColumns(rule, config);
                if (columns.size() < 2) {
                    throw new IllegalArgumentException("组合字段唯一规则至少需要选择两个字段");
                }
                String columnList = joinQuoted(columns);
                String distinctQuery = "SELECT COUNT(*) FROM (SELECT DISTINCT " + columnList + " FROM " + table + ") dq_distinct";
                checkSql =
                    "SELECT COUNT(*) AS checked_rows, COUNT(*) - (" +
                    distinctQuery +
                    ") AS invalid_rows FROM " +
                    table;
                sampleCondition =
                    "(" +
                    columnList +
                    ") IN (SELECT " +
                    columnList +
                    " FROM " +
                    table +
                    " GROUP BY " +
                    columnList +
                    " HAVING COUNT(*) > 1)";
                columnLabel = String.join(",", columns);
            }
            case NOT_NULL -> {
                requireColumn(column, type);
                boolean trim = boolValue(config.get("trimWhitespace"), false);
                List<String> conditions = new ArrayList<>();
                conditions.add(q(column) + " IS NULL");
                if (trim) {
                    conditions.add("TRIM(CAST(" + q(column) + " AS VARCHAR)) = ''");
                }
                sampleCondition = "(" + String.join(" OR ", conditions) + ")";
                checkSql = checkSql(table, sampleCondition);
            }
            case UNIQUE -> {
                requireColumn(column, type);
                checkSql =
                    "SELECT COUNT(*) AS checked_rows, COUNT(*) - COUNT(DISTINCT " +
                    q(column) +
                    ") AS invalid_rows FROM " +
                    table;
                sampleCondition =
                    q(column) +
                    " IS NOT NULL AND " +
                    q(column) +
                    " IN (SELECT " +
                    q(column) +
                    " FROM " +
                    table +
                    " GROUP BY " +
                    q(column) +
                    " HAVING COUNT(*) > 1)";
            }
            case RANGE -> {
                requireColumn(column, type);
                sampleCondition = buildRangeCondition(column, config);
                checkSql = checkSql(table, sampleCondition);
            }
            case ENUM -> {
                requireColumn(column, type);
                sampleCondition = buildEnumCondition(column, config);
                checkSql = checkSql(table, sampleCondition);
            }
            case REGEX -> {
                requireColumn(column, type);
                String pattern = trimToNull(config.getString("pattern"));
                if (pattern == null) {
                    throw new IllegalArgumentException("字段正则规则必须配置正则表达式");
                }
                sampleCondition =
                    q(column) + " IS NOT NULL AND NOT regexp_matches(CAST(" + q(column) + " AS VARCHAR), " + literal(pattern) + ")";
                checkSql = checkSql(table, sampleCondition);
            }
            case LENGTH -> {
                requireColumn(column, type);
                sampleCondition = buildLengthCondition(column, config);
                checkSql = checkSql(table, sampleCondition);
            }
            case FRESHNESS -> {
                requireColumn(column, type);
                long maxAgeHours = longValue(config.get("maxAgeHours"), 24L);
                if (maxAgeHours <= 0) {
                    throw new IllegalArgumentException("数据时效规则的最大时效小时数必须大于 0");
                }
                sampleCondition =
                    q(column) +
                    " IS NOT NULL AND (TRY_CAST(" +
                    q(column) +
                    " AS TIMESTAMP) IS NULL OR TRY_CAST(" +
                    q(column) +
                    " AS TIMESTAMP) < now() - INTERVAL '" +
                    maxAgeHours +
                    " hours')";
                checkSql = checkSql(table, sampleCondition);
            }
            case CUSTOM_SQL -> {
                String sql = requireSingleSelect(config.getString("sql"), "自定义 SQL");
                checkSql = customSqlCheck(table, sql);
            }
            default -> throw new IllegalArgumentException("不支持的数据质量规则类型: " + type.getCode());
        }

        return new CompiledQualityRule(
            ruleKey,
            ruleName,
            type.getLevel().name(),
            columnLabel,
            type.getCode(),
            severity,
            maxInvalidRows,
            maxInvalidRatio,
            checkSql,
            sampleCondition
        );
    }

    private String buildRangeCondition(String column, JSONObject config) {
        String valueType = firstNonBlank(config.getString("valueType"), "NUMBER").toUpperCase(Locale.ROOT);
        boolean number = !"DATE".equals(valueType) && !"TIMESTAMP".equals(valueType);
        String cast = number ? "TRY_CAST(" + q(column) + " AS DOUBLE)" : "TRY_CAST(" + q(column) + " AS TIMESTAMP)";
        List<String> conditions = new ArrayList<>();
        conditions.add(q(column) + " IS NOT NULL AND " + cast + " IS NULL");
        BigDecimal minNumber = number ? optionalDecimal(config.get("min")) : null;
        BigDecimal maxNumber = number ? optionalDecimal(config.get("max")) : null;
        String minDate = number ? null : trimToNull(config.getString("min"));
        String maxDate = number ? null : trimToNull(config.getString("max"));
        if (number) {
            if (minNumber != null) {
                conditions.add(cast + " < " + minNumber.toPlainString());
            }
            if (maxNumber != null) {
                conditions.add(cast + " > " + maxNumber.toPlainString());
            }
            if (minNumber == null && maxNumber == null) {
                throw new IllegalArgumentException("字段值范围规则至少需要配置最小值或最大值");
            }
        } else {
            if (minDate != null) {
                conditions.add(cast + " < TIMESTAMP " + literal(minDate));
            }
            if (maxDate != null) {
                conditions.add(cast + " > TIMESTAMP " + literal(maxDate));
            }
            if (minDate == null && maxDate == null) {
                throw new IllegalArgumentException("字段值范围规则至少需要配置最小值或最大值");
            }
        }
        return "(" + String.join(" OR ", conditions) + ")";
    }

    private String buildEnumCondition(String column, JSONObject config) {
        List<String> values = parseValueList(config.get("allowedValues"));
        if (values.isEmpty()) {
            throw new IllegalArgumentException("字段枚举规则必须配置允许值");
        }
        boolean ignoreCase = boolValue(config.get("ignoreCase"), false);
        List<String> literals = new ArrayList<>(values.size());
        for (String value : values) {
            literals.add(literal(ignoreCase ? value.toLowerCase(Locale.ROOT) : value));
        }
        String expression = "CAST(" + q(column) + " AS VARCHAR)";
        if (ignoreCase) {
            expression = "LOWER(" + expression + ")";
        }
        return q(column) + " IS NOT NULL AND " + expression + " NOT IN (" + String.join(", ", literals) + ")";
    }

    private String buildLengthCondition(String column, JSONObject config) {
        Long minLength = optionalLong(config.get("minLength"));
        Long maxLength = optionalLong(config.get("maxLength"));
        if (minLength == null && maxLength == null) {
            throw new IllegalArgumentException("字段长度规则至少需要配置最小长度或最大长度");
        }
        List<String> conditions = new ArrayList<>();
        String length = "LENGTH(CAST(" + q(column) + " AS VARCHAR))";
        if (minLength != null) {
            conditions.add(length + " < " + Math.max(0L, minLength));
        }
        if (maxLength != null) {
            conditions.add(length + " > " + Math.max(0L, maxLength));
        }
        return q(column) + " IS NOT NULL AND (" + String.join(" OR ", conditions) + ")";
    }

    private String checkSql(String table, String invalidCondition) {
        return "SELECT COUNT(*) AS checked_rows, COUNT(*) FILTER (WHERE " + invalidCondition + ") AS invalid_rows FROM " + table;
    }

    private String customSqlCheck(String table, String rawSql) {
        String sql = requireSingleSelect(rawSql, "自定义 SQL");
        return (
            "SELECT (SELECT COUNT(*) FROM " +
            table +
            ") AS checked_rows, (SELECT invalid_rows FROM (" +
            sql +
            ") dq_custom LIMIT 1) AS invalid_rows"
        );
    }

    private List<String> parseColumns(JSONObject rule, JSONObject config) {
        List<String> columns = parseValueList(config.get("columns"));
        if (columns.isEmpty()) {
            columns.addAll(parseValueList(rule.get("columns")));
        }
        if (columns.isEmpty()) {
            columns.addAll(parseValueList(rule.get("column")));
        }
        if (columns.isEmpty()) {
            String text = config.getString("columns");
            if (text != null && !text.trim().isEmpty()) {
                columns = splitCsv(text);
            }
        }
        for (String column : columns) {
            validateIdentifier(column, "字段名");
        }
        return columns;
    }

    private List<String> parseValueList(Object value) {
        List<String> values = new ArrayList<>();
        if (value == null) {
            return values;
        }
        if (value instanceof Collection<?>) {
            for (Object item : (Collection<?>) value) {
                if (item != null && !item.toString().trim().isEmpty()) {
                    values.add(item.toString().trim());
                }
            }
            return values;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.size(); i++) {
                Object item = array.get(i);
                if (item != null && !item.toString().trim().isEmpty()) {
                    values.add(item.toString().trim());
                }
            }
            return values;
        }
        if (value instanceof String) {
            return splitCsv((String) value);
        }
        values.add(value.toString());
        return values;
    }

    private List<String> splitCsv(String text) {
        List<String> values = new ArrayList<>();
        for (String item : text.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private String quoteTable(String sourceTable) {
        if (sourceTable == null || sourceTable.trim().isEmpty()) {
            throw new IllegalArgumentException("数据质量检核源表不能为空");
        }
        String normalized = sourceTable.trim();
        String[] parts = normalized.split("\\.");
        List<String> quoted = new ArrayList<>(parts.length);
        for (String part : parts) {
            validateIdentifier(part, "表名");
            quoted.add(q(part));
        }
        return String.join(".", quoted);
    }

    private void validateIdentifier(String value, String label) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(label + "包含非法字符: " + value);
        }
    }

    private void requireColumn(String column, QualityRuleType type) {
        if (column == null || column.trim().isEmpty()) {
            throw new IllegalArgumentException(type.getLabel() + "规则必须选择字段");
        }
    }

    private String requireSingleSelect(String sql, String label) {
        String normalized = sql == null ? "" : sql.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String withoutTrailingSemicolon = normalized.endsWith(";") ? normalized.substring(0, normalized.length() - 1).trim() : normalized;
        if (withoutTrailingSemicolon.contains(";")) {
            throw new IllegalArgumentException(label + "仅支持单条 SELECT 语句");
        }
        if (!withoutTrailingSemicolon.toLowerCase(Locale.ROOT).startsWith("select")) {
            throw new IllegalArgumentException(label + "必须以 SELECT 开头");
        }
        return withoutTrailingSemicolon;
    }

    private String joinQuoted(List<String> columns) {
        List<String> quoted = new ArrayList<>(columns.size());
        for (String column : columns) {
            quoted.add(q(column));
        }
        return String.join(", ", quoted);
    }

    private String uniqueKey(String key, Set<String> usedKeys) {
        String normalized = key == null || key.trim().isEmpty() ? "rule" : key.trim();
        String candidate = normalized;
        int suffix = 2;
        while (!usedKeys.add(candidate)) {
            candidate = normalized + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String normalizeSeverity(String severity) {
        String normalized = severity == null ? "" : severity.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return DEFAULT_SEVERITY;
        }
        if (!"ERROR".equals(normalized) && !"WARN".equals(normalized)) {
            throw new IllegalArgumentException("规则严重级别仅支持 ERROR 或 WARN: " + severity);
        }
        return normalized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private long longValue(Object value, long defaultValue) {
        Long parsed = optionalLong(value);
        return parsed == null ? defaultValue : parsed;
    }

    private Long optionalLong(Object value) {
        BigDecimal decimal = optionalDecimal(value);
        return decimal == null ? null : decimal.longValue();
    }

    private double doubleValue(Object value, double defaultValue) {
        BigDecimal decimal = optionalDecimal(value);
        return decimal == null ? defaultValue : decimal.doubleValue();
    }

    private BigDecimal optionalDecimal(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("数值配置格式错误: " + text);
        }
    }

    private boolean boolValue(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private String q(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private String literal(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
