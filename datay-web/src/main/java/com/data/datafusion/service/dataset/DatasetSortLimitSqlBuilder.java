package com.data.datafusion.service.dataset;

import com.data.datafusion.service.dto.DataSourceDTO;
import java.util.Collection;

/**
 * 数据集排序、分页与 Top N SQL 构建。
 *
 * <p>在数据集原始/筛选 SQL 外层追加 ORDER BY、LIMIT/OFFSET，兼容常见数据库分页语法。
 */
public final class DatasetSortLimitSqlBuilder {

    private static final String SORT_ALIAS = "dp_sort";
    private static final int MAX_LIMIT = 10000;

    private DatasetSortLimitSqlBuilder() {}

    public static String apply(
        DataSourceDTO dataSource,
        String baseSql,
        String sortField,
        String sortOrder,
        Integer limit,
        Integer offset,
        Collection<String> allowedFields
    ) {
        String field = sortField == null || sortField.isBlank() ? null : DatasetScopeSqlBuilder.requireIdentifier(sortField);
        if (field != null && allowedFields != null && !allowedFields.isEmpty() && !allowedFields.contains(field)) {
            throw new IllegalArgumentException("数据集排序字段不存在：" + field);
        }
        String direction = "desc".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        Integer rowLimit = limit == null ? null : Math.max(1, Math.min(limit, MAX_LIMIT));
        int rowOffset = offset == null ? 0 : Math.max(0, offset);
        if (rowOffset > 0 && rowLimit == null) {
            throw new IllegalArgumentException("分页查询必须同时提供 limit");
        }
        if (field == null && rowLimit == null && rowOffset == 0) {
            return baseSql;
        }
        String orderBy = field == null ? "" : " ORDER BY " + field + " " + direction;
        String sql = DatasetScopeSqlBuilder.stripTrailingSemicolon(baseSql);
        String dbType = dataSource == null ? "" : String.valueOf(dataSource.getType()).toUpperCase();
        if ("SQLSERVER".equals(dbType)) {
            if (rowOffset == 0) {
                String head = rowLimit == null ? "SELECT *" : "SELECT TOP " + rowLimit + " *";
                return head + " FROM (" + sql + ") " + SORT_ALIAS + orderBy;
            }
            String effectiveOrder = field == null ? " ORDER BY (SELECT NULL)" : orderBy;
            return (
                "SELECT * FROM (" +
                sql +
                ") " +
                SORT_ALIAS +
                effectiveOrder +
                " OFFSET " +
                rowOffset +
                " ROWS FETCH NEXT " +
                rowLimit +
                " ROWS ONLY"
            );
        }
        if ("ORACLE".equals(dbType)) {
            if (rowOffset == 0) {
                String limited = rowLimit == null
                    ? "SELECT * FROM (" + sql + ") " + SORT_ALIAS + orderBy
                    : "SELECT * FROM (SELECT * FROM (" + sql + ") " + SORT_ALIAS + orderBy + ") WHERE ROWNUM <= " + rowLimit;
                return limited;
            }
            int end = rowOffset + rowLimit;
            return (
                "SELECT * FROM (SELECT dp_page.*, ROWNUM rn FROM (SELECT * FROM (" +
                sql +
                ") " +
                SORT_ALIAS +
                orderBy +
                ") dp_page WHERE ROWNUM <= " +
                end +
                ") WHERE rn > " +
                rowOffset
            );
        }
        return (
            "SELECT * FROM (" +
            sql +
            ") " +
            SORT_ALIAS +
            orderBy +
            (rowLimit == null ? "" : " LIMIT " + rowLimit) +
            (rowOffset > 0 ? " OFFSET " + rowOffset : "")
        );
    }
}
