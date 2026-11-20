package com.data.datafusion.service.dataset;

import com.data.datafusion.service.dashboard.DatasetQueryOptions.Aggregation;
import com.data.datafusion.service.dto.DataSourceDTO;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 数据集服务端聚合 SQL 构建。
 *
 * <p>把数据集原始/筛选 SQL 包成子查询，在外层执行 GROUP BY 与聚合函数，
 * 再复用 {@link DatasetSortLimitSqlBuilder} 处理排序与 Top N。
 */
public final class DatasetAggregationSqlBuilder {

    private static final String AGG_ALIAS = "dp_agg";
    private static final Set<String> AGGREGATIONS = Set.of("sum", "avg", "min", "max", "count", "none");

    private DatasetAggregationSqlBuilder() {}

    public static String apply(
        DataSourceDTO dataSource,
        String baseSql,
        List<String> groupFields,
        List<Aggregation> aggregations,
        String sortField,
        String sortOrder,
        Integer limit,
        Integer offset,
        Collection<String> allowedFields
    ) {
        if (aggregations == null || aggregations.isEmpty()) {
            return DatasetSortLimitSqlBuilder.apply(
                dataSource,
                baseSql,
                sortField,
                sortOrder,
                limit,
                offset,
                allowedFields
            );
        }
        // 分组字段为空表示全局聚合（如 KPI / 仪表盘单值），直接在子查询上执行聚合函数。
        List<String> groups = groupFields == null ? List.of() : validatedFields(groupFields, allowedFields, "分组字段");
        List<String> selectItems = new ArrayList<>(groups);
        Set<String> usedAliases = new LinkedHashSet<>(groups);
        String dbType = dataSource == null ? "" : String.valueOf(dataSource.getType()).toUpperCase(Locale.ROOT);
        for (Aggregation aggregation : aggregations) {
            String field = DatasetScopeSqlBuilder.requireIdentifier(aggregation.field());
            if (allowedFields != null && !allowedFields.isEmpty() && !allowedFields.contains(field)) {
                throw new IllegalArgumentException("数据集聚合字段不存在：" + field);
            }
            if (!usedAliases.add(field)) {
                throw new IllegalArgumentException("数据集聚合字段重复：" + field);
            }
            String agg = aggregation.agg() == null ? "sum" : aggregation.agg().trim().toLowerCase();
            if (!AGGREGATIONS.contains(agg)) {
                throw new IllegalArgumentException("不支持的聚合方式：" + agg);
            }
            selectItems.add(accessor(agg, field, dbType) + " AS " + field);
        }
        String groupedSql =
            "SELECT " +
            String.join(", ", selectItems) +
            " FROM (" +
            DatasetScopeSqlBuilder.stripTrailingSemicolon(baseSql) +
            ") " +
            AGG_ALIAS +
            (groups.isEmpty() ? "" : " GROUP BY " + String.join(", ", groups));
        return DatasetSortLimitSqlBuilder.apply(
            dataSource,
            groupedSql,
            sortField,
            sortOrder,
            limit,
            offset,
            allowedFields
        );
    }

    private static List<String> validatedFields(List<String> fields, Collection<String> allowedFields, String label) {
        List<String> result = new ArrayList<>();
        for (String field : fields) {
            String validated = DatasetScopeSqlBuilder.requireIdentifier(field);
            if (allowedFields != null && !allowedFields.isEmpty() && !allowedFields.contains(validated)) {
                throw new IllegalArgumentException("数据集" + label + "不存在：" + validated);
            }
            if (!result.contains(validated)) {
                result.add(validated);
            }
        }
        return result;
    }

    private static String accessor(String agg, String field, String dbType) {
        return switch (agg) {
            // 先转 DECIMAL 再取整，避免浮点 AVG（如 1.005）向下舍入成 1.00。
            // TDengine 的 DECIMAL 支持有限，保留原生 ROUND 形式。
            case "avg" -> "TDENGINE".equals(dbType)
                ? "ROUND(AVG(" + field + "), 2)"
                : "ROUND(CAST(AVG(" + field + ") AS DECIMAL(38,10)), 2)";
            case "min" -> "MIN(" + field + ")";
            case "max" -> "MAX(" + field + ")";
            case "count" -> "COUNT(" + field + ")";
            // none 在前端等价于取首值；服务端聚合场景使用 MIN 作为确定性回退。
            case "none" -> "MIN(" + field + ")";
            default -> "SUM(" + field + ")";
        };
    }
}
