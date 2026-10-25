package com.data.datafusion.service.dataset;

import java.util.Collection;
import java.util.List;

/**
 * 数据集筛选条件 SQL 构建。
 *
 * <p>与行级数据权限共用 {@link DatasetScopeSqlBuilder} 的字段白名单、运算符和字面量转义规则，
 * 把看板筛选条件包在数据集原始 SQL 外层，避免修改用户 SQL。
 */
public final class DatasetFilterSqlBuilder {

    private static final String FILTER_ALIAS = "dp_filter";

    private DatasetFilterSqlBuilder() {}

    /** 无条件时原样返回；有条件下包成子查询并追加 WHERE。 */
    public static String wrap(String baseSql, List<DatasetScopeCondition> conditions, Collection<String> allowedFields) {
        String predicate = DatasetScopeSqlBuilder.buildAndPredicate(conditions, allowedFields);
        if (predicate.isEmpty()) {
            return baseSql;
        }
        return (
            "SELECT * FROM (" +
            DatasetScopeSqlBuilder.stripTrailingSemicolon(baseSql) +
            ") " +
            FILTER_ALIAS +
            " WHERE " +
            predicate
        );
    }
}
