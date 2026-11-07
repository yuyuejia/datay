package com.data.job.quality;

/**
 * 编译后的数据质量规则。
 *
 * <p>{@link #checkSql} 必须返回单行两列：{@code checked_rows} 与 {@code invalid_rows}。
 * {@link #sampleCondition} 可用于抽样失败数据，为空表示该规则不支持抽样。</p>
 */
public class CompiledQualityRule {

    private final String key;

    private final String name;

    private final String level;

    private final String column;

    private final String type;

    private final String severity;

    private final long maxInvalidRows;

    private final double maxInvalidRatio;

    private final String checkSql;

    private final String sampleCondition;

    public CompiledQualityRule(
        String key,
        String name,
        String level,
        String column,
        String type,
        String severity,
        long maxInvalidRows,
        double maxInvalidRatio,
        String checkSql,
        String sampleCondition
    ) {
        this.key = key;
        this.name = name;
        this.level = level;
        this.column = column;
        this.type = type;
        this.severity = severity;
        this.maxInvalidRows = maxInvalidRows;
        this.maxInvalidRatio = maxInvalidRatio;
        this.checkSql = checkSql;
        this.sampleCondition = sampleCondition;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getLevel() {
        return level;
    }

    public String getColumn() {
        return column;
    }

    public String getType() {
        return type;
    }

    public String getSeverity() {
        return severity;
    }

    public long getMaxInvalidRows() {
        return maxInvalidRows;
    }

    public double getMaxInvalidRatio() {
        return maxInvalidRatio;
    }

    public String getCheckSql() {
        return checkSql;
    }

    public String getSampleCondition() {
        return sampleCondition;
    }
}
