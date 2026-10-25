package com.data.datafusion.service.dashboard;

/**
 * 数据集查询的排序、分页与行数限制。
 *
 * <p>由组件 encode.sortField/sortOrder/limit 下推而来；为空表示保持数据集原始顺序与全量行。
 */
public record DatasetQueryOptions(String sortField, String sortOrder, Integer limit, Integer offset) {

    public static final DatasetQueryOptions NONE = new DatasetQueryOptions(null, null, null, null);

    public boolean isEmpty() {
        return (sortField == null || sortField.isBlank()) && limit == null && (offset == null || offset <= 0);
    }
}
