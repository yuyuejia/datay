package com.data.datafusion.service.dashboard;

import java.util.List;

/**
 * 数据集查询的聚合、排序、分页与行数限制。
 *
 * <p>由组件 encode 下推而来；groupFields/aggregations 为空表示不做服务端聚合。
 */
public record DatasetQueryOptions(
    String sortField,
    String sortOrder,
    Integer limit,
    Integer offset,
    List<String> groupFields,
    List<Aggregation> aggregations
) {
    public static final DatasetQueryOptions NONE = new DatasetQueryOptions(null, null, null, null, List.of(), List.of());

    public DatasetQueryOptions(String sortField, String sortOrder, Integer limit, Integer offset) {
        this(sortField, sortOrder, limit, offset, List.of(), List.of());
    }

    public boolean hasAggregation() {
        return groupFields != null && !groupFields.isEmpty() && aggregations != null && !aggregations.isEmpty();
    }

    public boolean isEmpty() {
        return (
            (sortField == null || sortField.isBlank()) &&
            limit == null &&
            (offset == null || offset <= 0) &&
            !hasAggregation()
        );
    }

    public record Aggregation(String field, String agg) {}
}
