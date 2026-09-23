package com.data.datafusion.service.metric;

/**
 * 指标业务限定运算符。
 */
public enum MetricFilterOperator {
    EQ("=", false, false),
    NE("<>", false, false),
    GT(">", false, false),
    GE(">=", false, false),
    LT("<", false, false),
    LE("<=", false, false),
    IN("IN", false, false),
    NOT_IN("NOT IN", false, false),
    LIKE("LIKE", false, false),
    IS_NULL("IS NULL", true, false),
    IS_NOT_NULL("IS NOT NULL", true, false),
    BETWEEN("BETWEEN", false, true);

    private final String symbol;
    private final boolean unary;
    private final boolean range;

    MetricFilterOperator(String symbol, boolean unary, boolean range) {
        this.symbol = symbol;
        this.unary = unary;
        this.range = range;
    }

    public String getSymbol() {
        return symbol;
    }

    /** 是否为一元运算符（无需取值，如 IS NULL）。 */
    public boolean isUnary() {
        return unary;
    }

    /** 是否为区间运算符（需要起止两个值，如 BETWEEN）。 */
    public boolean isRange() {
        return range;
    }

    public static MetricFilterOperator fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (MetricFilterOperator op : values()) {
            if (op.name().equalsIgnoreCase(code.trim())) {
                return op;
            }
        }
        return null;
    }
}
