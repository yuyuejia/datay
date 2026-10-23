package com.data.datafusion.service.dataset;

/**
 * 数据集字段取值查询 SQL 构建。
 *
 * <p>在数据集原始 SQL 外层做 {@code DISTINCT} 取值，供数据权限条件值选择、筛选器候选值等场景使用。
 */
public final class DatasetFieldValueSqlBuilder {

    /** 取值子查询别名。 */
    private static final String VALUE_ALIAS = "dp_values";

    private DatasetFieldValueSqlBuilder() {}

    /**
     * 构建去重取值分页 SQL。
     *
     * @param baseSql   数据集原始 SQL（可包含末尾分号）
     * @param fieldName 输出字段名
     * @param keyword   模糊过滤关键字，可为空
     * @param limit     每页条数
     * @param offset    偏移量
     */
    public static String buildValueQuery(String baseSql, String fieldName, String keyword, int limit, long offset) {
        String column = DatasetScopeSqlBuilder.requireIdentifier(fieldName);
        return (
            "SELECT DISTINCT " +
            column +
            " AS dp_value FROM (" +
            DatasetScopeSqlBuilder.stripTrailingSemicolon(baseSql) +
            ") " +
            VALUE_ALIAS +
            buildWhereClause(column, keyword) +
            " ORDER BY dp_value LIMIT " +
            limit +
            " OFFSET " +
            offset
        );
    }

    /**
     * 构建去重取值总数 SQL。
     */
    public static String buildCountQuery(String baseSql, String fieldName, String keyword) {
        String column = DatasetScopeSqlBuilder.requireIdentifier(fieldName);
        return (
            "SELECT COUNT(DISTINCT " +
            column +
            ") AS cnt FROM (" +
            DatasetScopeSqlBuilder.stripTrailingSemicolon(baseSql) +
            ") " +
            VALUE_ALIAS +
            buildWhereClause(column, keyword)
        );
    }

    private static String buildWhereClause(String column, String keyword) {
        StringBuilder where = new StringBuilder(" WHERE ").append(column).append(" IS NOT NULL");
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND ").append(column).append(" LIKE ").append(quote("%" + keyword.trim() + "%"));
        }
        return where.toString();
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
