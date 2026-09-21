package com.data.job.component.transform;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 数据转换 SQL 生成器。
 *
 * <p>根据用户配置的转换规则列表生成 DuckDB SELECT 语句。内置了常用的转换规则
 * （类型转换、过滤、字符串/数学/日期函数等），也支持通过
 * {@link #registerHandler(String, RuleHandler)} 注册自定义规则，以及使用
 * {@code custom}/{@code expression} 规则直接书写 DuckDB 表达式。
 *
 * <p>规则统一为 JSON 对象，通用字段：
 * <ul>
 *   <li>{@code type}：规则类型，例如 {@code cast}、{@code filter}、{@code upper}；</li>
 *   <li>{@code column}：待处理的输入字段；</li>
 *   <li>{@code targetColumn}：输出字段别名，缺省与 {@code column} 相同（相同则表示覆盖原字段）；</li>
 *   <li>{@code targetType}：{@code cast} 规则的目标类型；</li>
 *   <li>其余字段随规则类型而定。</li>
 * </ul>
 *
 * <p>示例：
 * <pre>
 * [
 *   {"type": "cast", "column": "age", "targetType": "INTEGER"},
 *   {"type": "upper", "column": "name", "targetColumn": "name_upper"},
 *   {"type": "filter", "column": "status", "operator": "=", "value": "ACTIVE"}
 * ]
 * </pre>
 */
public class TransformSqlBuilder {

    /** 简单标识符，可直接拼接到 SQL 中；其余标识符使用双引号包裹。 */
    private static final Pattern SIMPLE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** 允许的目标类型：类型名，可带精度，例如 DECIMAL(10,2)。 */
    private static final Pattern TYPE_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\s*\\(\\s*\\d+\\s*(,\\s*\\d+\\s*)?\\)?)?");

    /** 整数参数。 */
    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+");

    private static final Map<String, RuleHandler> HANDLERS = new LinkedHashMap<>();

    static {
        // 字段级规则
        registerHandler("cast", rule -> expr(rule, "CAST(" + identifier(column(rule)) + " AS " + targetType(rule) + ")"));
        registerHandler("rename", rule -> RuleResult.expression(identifier(column(rule)), targetColumn(rule, column(rule)), column(rule)));
        registerHandler("drop", rule -> RuleResult.excluded(column(rule)));
        registerHandler("upper", rule -> expr(rule, "UPPER(" + identifier(column(rule)) + ")"));
        registerHandler("lower", rule -> expr(rule, "LOWER(" + identifier(column(rule)) + ")"));
        registerHandler("trim", rule -> expr(rule, "TRIM(" + identifier(column(rule)) + ")"));
        registerHandler("ltrim", rule -> expr(rule, "LTRIM(" + identifier(column(rule)) + ")"));
        registerHandler("rtrim", rule -> expr(rule, "RTRIM(" + identifier(column(rule)) + ")"));
        registerHandler("length", rule -> expr(rule, "LENGTH(" + identifier(column(rule)) + ")"));
        registerHandler("replace", rule -> {
            String replacement = rule.getString("replacement");
            return expr(
                rule,
                "REPLACE(" + identifier(column(rule)) + ", " + literal(rule.get("search")) + ", " + literal(replacement == null ? "" : replacement) + ")"
            );
        });
        registerHandler("substring", rule -> {
            String col = identifier(column(rule));
            String start = integer(rule.get("start"), 1);
            Object length = rule.get("length");
            if (length == null) {
                return expr(rule, "SUBSTRING(" + col + ", " + start + ")");
            }
            return expr(rule, "SUBSTRING(" + col + ", " + start + ", " + integer(length, null) + ")");
        });
        registerHandler("concat", rule -> {
            String col = identifier(column(rule));
            List<String> others = identifierList(rule.get("columns"));
            String separator = rule.getString("separator");
            String expression;
            if (separator != null && !separator.isEmpty()) {
                List<String> parts = new ArrayList<>();
                parts.add(col);
                parts.addAll(others);
                expression = "CONCAT_WS(" + literal(separator) + ", " + String.join(", ", parts) + ")";
            } else {
                List<String> parts = new ArrayList<>();
                parts.add(col);
                parts.addAll(others);
                expression = "CONCAT(" + String.join(", ", parts) + ")";
            }
            return expr(rule, expression);
        });
        registerHandler("coalesce", rule -> {
            List<String> values = new ArrayList<>();
            values.add(identifier(column(rule)));
            Object defaults = rule.get("values");
            if (defaults instanceof Collection) {
                for (Object item : (Collection<?>) defaults) {
                    values.add(literal(item));
                }
            }
            Object defaultValue = rule.get("defaultValue");
            if (defaultValue != null) {
                values.add(literal(defaultValue));
            }
            return expr(rule, "COALESCE(" + String.join(", ", values) + ")");
        });
        registerHandler("round", rule -> {
            String col = identifier(column(rule));
            Object decimals = rule.get("decimals");
            if (decimals == null) {
                return expr(rule, "ROUND(" + col + ")");
            }
            return expr(rule, "ROUND(" + col + ", " + integer(decimals, 0) + ")");
        });
        registerHandler("ceil", rule -> expr(rule, "CEIL(" + identifier(column(rule)) + ")"));
        registerHandler("floor", rule -> expr(rule, "FLOOR(" + identifier(column(rule)) + ")"));
        registerHandler("abs", rule -> expr(rule, "ABS(" + identifier(column(rule)) + ")"));
        registerHandler("sqrt", rule -> expr(rule, "SQRT(" + identifier(column(rule)) + ")"));
        registerHandler("date_format", rule -> expr(rule, "STRFTIME(" + identifier(column(rule)) + ", " + literal(required(rule, "format")) + ")"));
        registerHandler("parse_date", rule -> expr(rule, "STRPTIME(" + identifier(column(rule)) + ", " + literal(required(rule, "format")) + ")"));
        registerHandler("extract", rule -> expr(rule, "DATE_PART(" + literal(required(rule, "part")) + ", " + identifier(column(rule)) + ")"));
        registerHandler("split", rule -> expr(
            rule,
            "SPLIT_PART(" + identifier(column(rule)) + ", " + literal(required(rule, "delimiter")) + ", " + integer(rule.get("index"), 1) + ")"
        ));
        registerHandler("add", rule -> expr(rule, "(" + identifier(column(rule)) + " + " + operand(rule) + ")"));
        registerHandler("subtract", rule -> expr(rule, "(" + identifier(column(rule)) + " - " + operand(rule) + ")"));
        registerHandler("multiply", rule -> expr(rule, "(" + identifier(column(rule)) + " * " + operand(rule) + ")"));
        registerHandler("divide", rule -> expr(rule, "(" + identifier(column(rule)) + " / " + operand(rule) + ")"));
        registerHandler("custom", rule -> {
            String expression = required(rule, "expression");
            expression = expression.replace("{column}", identifier(column(rule)));
            return expr(rule, expression);
        });

        // 过滤规则
        registerHandler("filter", TransformSqlBuilder::buildFilter);

        // 常见别名
        alias("uppercase", "upper");
        alias("lowercase", "lower");
        alias("substr", "substring");
        alias("strftime", "date_format");
        alias("strptime", "parse_date");
        alias("date_part", "extract");
        alias("split_part", "split");
        alias("expression", "custom");
        alias("expr", "custom");
    }

    private TransformSqlBuilder() {
    }

    /**
     * 注册/覆盖一种转换规则。type 不区分大小写。
     */
    public static synchronized void registerHandler(String type, RuleHandler handler) {
        HANDLERS.put(normalizeType(type), handler);
    }

    private static synchronized void alias(String aliasType, String targetType) {
        HANDLERS.put(normalizeType(aliasType), HANDLERS.get(normalizeType(targetType)));
    }

    /**
     * 获取内置支持的规则类型（按注册顺序）。
     */
    public static Set<String> supportedRuleTypes() {
        return Collections.unmodifiableSet(HANDLERS.keySet());
    }

    /**
     * 将配置的 rules 统一解析为 JSONArray，兼容 JSONArray / List / JSON 字符串。
     */
    @SuppressWarnings("unchecked")
    public static JSONArray resolveRules(Object rules) {
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
        if (rules instanceof Collection) {
            JSONArray array = new JSONArray();
            array.addAll((Collection<?>) rules);
            return array;
        }
        throw new IllegalArgumentException("rules 配置格式错误: " + rules.getClass());
    }

    /**
     * 根据规则生成 SELECT 语句（无过滤条件）。
     *
     * @param qualifiedTable 输入表，需已限定 schema，例如 {@code main.tmp_xxx}
     * @param rules          转换规则列表
     * @param selectColumns  基础输出字段，可为空；为空时输出 {@code * EXCLUDE(被覆盖/删除/重命名的字段)}
     */
    public static String buildSelectSql(String qualifiedTable, JSONArray rules, String selectColumns) {
        return buildSelectSql(qualifiedTable, rules, selectColumns, null, null);
    }

    /**
     * 根据规则生成 SELECT 语句。
     *
     * @param qualifiedTable 输入表，需已限定 schema，例如 {@code main.tmp_xxx}
     * @param rules          转换规则列表
     * @param selectColumns  基础输出字段，可为空；为空时输出 {@code * EXCLUDE(被覆盖/删除/重命名的字段)}
     * @param filters        结构化过滤规则列表，可为空
     * @param filterLogic    过滤规则之间的逻辑关系，{@code AND}（默认）或 {@code OR}
     */
    public static String buildSelectSql(
        String qualifiedTable,
        JSONArray rules,
        String selectColumns,
        JSONArray filters,
        String filterLogic
    ) {
        if (qualifiedTable == null || qualifiedTable.trim().isEmpty()) {
            throw new IllegalArgumentException("输入表不能为空");
        }

        List<String> computed = new ArrayList<>();
        Set<String> excluded = new LinkedHashSet<>();
        List<String> whereConditions = new ArrayList<>();

        for (Object item : rules == null ? new JSONArray() : rules) {
            JSONObject rule = toJsonObject(item);
            String type = normalizeType(rule.getString("type"));
            if (type == null) {
                throw new IllegalArgumentException("转换规则缺少 type 字段: " + rule);
            }
            RuleHandler handler = HANDLERS.get(type);
            if (handler == null) {
                throw new IllegalArgumentException("不支持的转换规则类型: " + type + "，支持的类型: " + supportedRuleTypes());
            }
            RuleResult result = handler.build(rule);
            if (result == null) {
                continue;
            }
            if (result.getWhere() != null) {
                whereConditions.add(result.getWhere());
            }
            if (result.getExclude() != null) {
                excluded.add(result.getExclude());
            }
            if (result.getExpression() != null) {
                computed.add(result.getExpression() + " AS " + identifier(result.getAlias()));
            }
        }

        List<String> selectItems = new ArrayList<>();
        String base = baseSelect(selectColumns, excluded);
        selectItems.add(base);
        selectItems.addAll(computed);

        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", selectItems));
        sql.append(" FROM ").append(qualifiedTable.trim());

        List<String> wheres = new ArrayList<>();
        String filterClause = buildFilterClause(filters, filterLogic);
        if (filterClause != null) {
            wheres.add(filterClause);
        }
        wheres.addAll(whereConditions);
        if (!wheres.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", wheres));
        }
        return sql.toString();
    }

    /**
     * 将结构化过滤规则组合为 WHERE 条件：多条规则按 {@code logic}（默认 AND）连接。
     *
     * @return 组合后的条件，filters 为空时返回 null
     */
    public static String buildFilterClause(JSONArray filters, String filterLogic) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        List<String> conditions = new ArrayList<>();
        for (Object item : filters) {
            RuleResult result = buildFilter(toJsonObject(item));
            if (result != null && result.getWhere() != null) {
                conditions.add(result.getWhere());
            }
        }
        if (conditions.isEmpty()) {
            return null;
        }
        String operator = "OR".equalsIgnoreCase(filterLogic == null ? "" : filterLogic.trim()) ? " OR " : " AND ";
        String joined = String.join(operator, conditions);
        return conditions.size() == 1 ? joined : "(" + joined + ")";
    }

    private static String baseSelect(String selectColumns, Set<String> excluded) {
        if (selectColumns != null && !selectColumns.trim().isEmpty()) {
            String text = selectColumns.trim();
            if (text.startsWith("[")) {
                JSONArray columns = JSONArray.parseArray(text);
                List<String> items = new ArrayList<>();
                for (Object column : columns) {
                    items.add(String.valueOf(column));
                }
                return String.join(", ", items);
            }
            return text;
        }
        if (excluded.isEmpty()) {
            return "*";
        }
        List<String> names = new ArrayList<>();
        for (String column : excluded) {
            names.add(identifier(column));
        }
        return "* EXCLUDE (" + String.join(", ", names) + ")";
    }

    private static RuleResult buildFilter(JSONObject rule) {
        String column = rule.getString("column");
        String expression = rule.getString("expression");
        if ((column == null || column.trim().isEmpty())) {
            if (expression == null || expression.trim().isEmpty()) {
                throw new IllegalArgumentException("filter 规则需要 column 或 expression: " + rule);
            }
            return RuleResult.where("(" + expression.trim() + ")");
        }

        String col = identifier(column);
        String operator = rule.getString("operator");
        operator = (operator == null || operator.trim().isEmpty()) ? "=" : operator.trim().toUpperCase();

        if ("IS NULL".equals(operator) || "IS NOT NULL".equals(operator)) {
            return RuleResult.where(col + " " + operator);
        }
        if ("IN".equals(operator) || "NOT IN".equals(operator)) {
            List<String> items = new ArrayList<>();
            for (Object item : filterValues(rule.get("value"))) {
                items.add(literal(item));
            }
            return RuleResult.where(col + " " + operator + " (" + String.join(", ", items) + ")");
        }
        if ("BETWEEN".equals(operator) || "NOT BETWEEN".equals(operator)) {
            List<Object> items = filterValues(rule.get("value"));
            if (items.size() != 2) {
                throw new IllegalArgumentException("BETWEEN 规则需要两个值: " + rule);
            }
            return RuleResult.where(col + " " + operator + " " + literal(items.get(0)) + " AND " + literal(items.get(1)));
        }
        return RuleResult.where(col + " " + operator + " " + literal(rule.get("value")));
    }

    /**
     * 解析过滤值：数组按元素取值，字符串按逗号切分（兼容前端单输入框输入多个值）。
     */
    private static List<Object> filterValues(Object value) {
        List<Object> values = new ArrayList<>();
        if (value instanceof Collection) {
            values.addAll((Collection<?>) value);
        } else if (value != null) {
            for (String item : String.valueOf(value).split(",")) {
                if (!item.trim().isEmpty()) {
                    values.add(item.trim());
                }
            }
        }
        return values;
    }

    private static RuleResult expr(JSONObject rule, String expression) {
        String column = column(rule);
        String alias = targetColumn(rule, column);
        String exclude = alias.equals(column) ? column : null;
        return RuleResult.expression(expression, alias, exclude);
    }

    private static String column(JSONObject rule) {
        String column = rule.getString("column");
        if (column == null || column.trim().isEmpty()) {
            throw new IllegalArgumentException("转换规则缺少 column 字段: " + rule);
        }
        return column.trim();
    }

    private static String targetColumn(JSONObject rule, String sourceColumn) {
        String target = rule.getString("targetColumn");
        if (target == null || target.trim().isEmpty()) {
            return sourceColumn;
        }
        return target.trim();
    }

    private static String targetType(JSONObject rule) {
        String type = rule.getString("targetType");
        if (type == null || type.trim().isEmpty()) {
            type = rule.getString("castType");
        }
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("cast 规则缺少 targetType: " + rule);
        }
        String normalized = type.trim().toUpperCase();
        // DECIMAL/NUMERIC 支持通过 precision/scale 指定精度与小数位
        if (("DECIMAL".equals(normalized) || "NUMERIC".equals(normalized)) && rule.get("precision") != null) {
            normalized = normalized + "(" + integer(rule.get("precision"), null) + "," + integer(rule.get("scale"), 0) + ")";
        }
        if (!TYPE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("非法的目标类型: " + type);
        }
        return normalized;
    }

    private static String required(JSONObject rule, String key) {
        String value = rule.getString(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("转换规则缺少 " + key + " 字段: " + rule);
        }
        return value.trim();
    }

    private static List<String> identifierList(Object value) {
        List<String> columns = new ArrayList<>();
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                columns.add(identifier(String.valueOf(item)));
            }
        } else if (value instanceof String && !((String) value).trim().isEmpty()) {
            for (String item : ((String) value).split(",")) {
                if (!item.trim().isEmpty()) {
                    columns.add(identifier(item.trim()));
                }
            }
        }
        return columns;
    }

    /**
     * 解析四则运算的操作数：{@code valueIsColumn=true} 时作为字段名，否则作为常量。
     */
    private static String operand(JSONObject rule) {
        Object value = rule.get("value");
        if (rule.getBooleanValue("valueIsColumn", false)) {
            return identifier(String.valueOf(value));
        }
        return literal(value);
    }

    static String identifier(String name) {
        String value = name == null ? "" : name.trim();
        if (SIMPLE_IDENTIFIER.matcher(value).matches()) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    static String integer(Object value, Integer defaultValue) {
        if (value == null) {
            if (defaultValue == null) {
                throw new IllegalArgumentException("缺少整数参数");
            }
            return defaultValue.toString();
        }
        String text = value instanceof Number ? value.toString() : value.toString().trim();
        if (!INTEGER_PATTERN.matcher(text).matches()) {
            throw new IllegalArgumentException("非法的整数参数: " + value);
        }
        return text;
    }

    static String literal(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return "'" + value.toString().replace("'", "''") + "'";
    }

    @SuppressWarnings("unchecked")
    private static JSONObject toJsonObject(Object item) {
        if (item instanceof JSONObject) {
            return (JSONObject) item;
        }
        if (item instanceof Map) {
            return new JSONObject((Map<String, Object>) item);
        }
        if (item instanceof String) {
            return JSONObject.parseObject((String) item);
        }
        throw new IllegalArgumentException("转换规则格式错误: " + item);
    }

    private static String normalizeType(String type) {
        if (type == null) {
            return null;
        }
        return type.trim().toLowerCase().replace('-', '_');
    }

    /**
     * 单条转换规则的生成结果。
     */
    public static final class RuleResult {

        private final String expression;
        private final String alias;
        private final String exclude;
        private final String where;

        private RuleResult(String expression, String alias, String exclude, String where) {
            this.expression = expression;
            this.alias = alias;
            this.exclude = exclude;
            this.where = where;
        }

        public static RuleResult expression(String expression, String alias, String exclude) {
            return new RuleResult(expression, alias, exclude, null);
        }

        public static RuleResult excluded(String column) {
            return new RuleResult(null, null, column, null);
        }

        public static RuleResult where(String condition) {
            return new RuleResult(null, null, null, condition);
        }

        public String getExpression() {
            return expression;
        }

        public String getAlias() {
            return alias;
        }

        public String getExclude() {
            return exclude;
        }

        public String getWhere() {
            return where;
        }
    }

    /**
     * 转换规则处理器，返回表达式 / 别名 / 排除字段 / where 条件。
     */
    @FunctionalInterface
    public interface RuleHandler {
        RuleResult build(JSONObject rule);
    }
}
