package com.data.datafusion.ai;

/**
 * AI SQL 生成场景模式。
 *
 * <p>不同入口对 SQL 的安全边界不同：数据查询只允许只读 SELECT，
 * 而 SQL 任务用于数据加工与调度，需要生成 DDL / DML 语句。
 */
public enum AiSqlMode {
    /** 数据查询：仅生成单条只读 SELECT。 */
    QUERY,

    /** SQL 任务：允许生成 DDL、DML 或多条语句。 */
    TASK;

    /**
     * 解析前端传入的模式标识，无法识别时回退到最安全的 QUERY。
     */
    public static AiSqlMode from(String value) {
        if (value != null && "task".equalsIgnoreCase(value.trim())) {
            return TASK;
        }
        return QUERY;
    }

    public boolean isTask() {
        return this == TASK;
    }
}
