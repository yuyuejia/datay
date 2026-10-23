package com.data.datafusion.service.dataset;

import com.data.datafusion.service.metric.MetricFilterOperator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 数据集行级数据权限 SQL 构建。
 *
 * <p>权限条件作用于数据集输出列：多条规则的条件以 OR 组合。字段名必须是合法的 SQL 标识符，
 * 且（若提供）必须存在于数据集的输出字段中；取值按字符串字面量转义，数字保持裸值。
 */
public final class DatasetScopeSqlBuilder {

    /** 合法字段标识符：字母或下划线开头，仅包含字母、数字、下划线。 */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    /** 数字字面量，保持裸值，避免被数据库按字符串比较。 */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");

    /** 数据集子查询别名。 */
    private static final String DATASET_ALIAS = "dp_dataset";

    private DatasetScopeSqlBuilder() {}

    /**
     * 构建权限谓词；无条件时返回空字符串。
     *
     * @param conditions    解析后的角色数据集范围条件
     * @param allowedFields 数据集当前输出字段；为空表示不做字段存在性校验
     */
    public static String buildPredicate(List<DatasetScopeCondition> conditions, Collection<String> allowedFields) {
        if (conditions == null || conditions.isEmpty()) {
            return "";
        }
        Set<String> allowed = allowedFields == null
            ? Set.of()
            : allowedFields.stream().filter(name -> name != null && !name.isBlank()).collect(Collectors.toSet());
        List<String> predicates = new ArrayList<>();
        for (DatasetScopeCondition condition : conditions) {
            String column = requireColumn(condition, allowed);
            String predicate = buildCondition(condition, column);
            if (predicate != null && !predicate.isBlank()) {
                predicates.add("(" + predicate + ")");
            }
        }
        if (predicates.isEmpty()) {
            return "";
        }
        return "(" + String.join(" OR ", predicates) + ")";
    }

    /**
     * 用权限谓词包裹数据集原始 SQL；无条件时原样返回。
     */
    public static String wrap(String baseSql, List<DatasetScopeCondition> conditions, Collection<String> allowedFields) {
        String predicate = buildPredicate(conditions, allowedFields);
        if (predicate.isEmpty()) {
            return baseSql;
        }
        return "SELECT * FROM (" + stripTrailingSemicolon(baseSql) + ") " + DATASET_ALIAS + " WHERE " + predicate;
    }

    private static String requireColumn(DatasetScopeCondition condition, Set<String> allowedFields) {
        String trimmed = requireIdentifier(condition.getFieldName());
        if (!allowedFields.isEmpty() && !allowedFields.contains(trimmed)) {
            throw new IllegalArgumentException("数据集权限字段不存在：" + trimmed);
        }
        return trimmed;
    }

    /**
     * 校验字段名是合法的 SQL 标识符，并返回去掉首尾空白后的字段名。
     */
    public static String requireIdentifier(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("数据集字段不能为空");
        }
        String trimmed = fieldName.trim();
        if (!IDENTIFIER_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("数据集字段名不合法：" + fieldName);
        }
        return trimmed;
    }

    private static String buildCondition(DatasetScopeCondition condition, String column) {
        MetricFilterOperator operator = MetricFilterOperator.fromCode(condition.getOperator());
        if (operator == null) {
            throw new IllegalArgumentException("不支持的数据集权限运算符：" + condition.getOperator());
        }
        if (operator.isUnary()) {
            return column + " " + operator.getSymbol();
        }
        if (operator.isRange()) {
            if (isBlank(condition.getValue()) || isBlank(condition.getValueEnd())) {
                throw new IllegalArgumentException("BETWEEN 条件需要提供起止值");
            }
            return column + " BETWEEN " + quote(condition.getValue()) + " AND " + quote(condition.getValueEnd());
        }
        if (operator == MetricFilterOperator.IN || operator == MetricFilterOperator.NOT_IN) {
            List<String> values = splitValues(condition.getValue());
            if (values.isEmpty()) {
                // IN 空集合恒为假，NOT IN 空集合恒为真：分别用安全形式表达，避免生成非法 SQL
                return operator == MetricFilterOperator.IN ? "1=0" : "1=1";
            }
            return column + " " + operator.getSymbol() + " (" + values.stream().map(DatasetScopeSqlBuilder::quote).collect(Collectors.joining(", ")) + ")";
        }
        if (isBlank(condition.getValue())) {
            throw new IllegalArgumentException("数据集权限条件缺少取值：" + condition.getFieldName());
        }
        return column + " " + operator.getSymbol() + " " + quote(condition.getValue());
    }

    private static List<String> splitValues(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(item -> !item.isEmpty())
            .collect(Collectors.toList());
    }

    private static String quote(String value) {
        String trimmed = value == null ? null : value.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            return "NULL";
        }
        if (NUMBER_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }
        return "'" + trimmed.replace("'", "''") + "'";
    }

    /**
     * 去掉 SQL 末尾多余的分号，便于把原始 SQL 作为子查询使用。
     */
    public static String stripTrailingSemicolon(String sql) {
        if (sql == null) {
            return "";
        }
        String trimmed = sql.trim();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
