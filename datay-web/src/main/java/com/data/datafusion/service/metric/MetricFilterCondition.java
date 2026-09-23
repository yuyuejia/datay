package com.data.datafusion.service.metric;

/**
 * 单条指标业务限定。
 * <p>
 * 支持三种类型：
 * <ul>
 *     <li>{@link #TYPE_FACT_FIELD} 事实表字段条件：直接作用于事实表字段。</li>
 *     <li>{@link #TYPE_DIMENSION} 维度条件：关联维度模型后作用于维度字段。</li>
 *     <li>{@link #TYPE_TIME} 时间条件：作用于日期字段（事实表日期字段或关联维度日期字段）。</li>
 * </ul>
 */
public class MetricFilterCondition {

    public static final String TYPE_FACT_FIELD = "FACT_FIELD";
    public static final String TYPE_DIMENSION = "DIMENSION";
    public static final String TYPE_TIME = "TIME";

    /** 条件类型：FACT_FIELD / DIMENSION / TIME。 */
    private String type;

    /** 事实表字段名（维度条件时为关联维度模型的外键字段，时间条件时为日期字段）。 */
    private String factFieldName;

    /** 维度模型 id（仅维度条件或维度时间字段时使用）。 */
    private Long dimensionModelId;

    /** 维度字段名（仅维度条件或维度时间字段时使用）。 */
    private String dimensionFieldName;

    /** 运算符，取值见 {@link MetricFilterOperator}。 */
    private String operator;

    /** 条件值；IN 时可用英文逗号分隔多个值。 */
    private String value;

    /** BETWEEN 时的结束值。 */
    private String valueEnd;

    /** 与上一条条件的组合逻辑：AND / OR，默认 AND。 */
    private String logic;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFactFieldName() {
        return factFieldName;
    }

    public void setFactFieldName(String factFieldName) {
        this.factFieldName = factFieldName;
    }

    public Long getDimensionModelId() {
        return dimensionModelId;
    }

    public void setDimensionModelId(Long dimensionModelId) {
        this.dimensionModelId = dimensionModelId;
    }

    public String getDimensionFieldName() {
        return dimensionFieldName;
    }

    public void setDimensionFieldName(String dimensionFieldName) {
        this.dimensionFieldName = dimensionFieldName;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getValueEnd() {
        return valueEnd;
    }

    public void setValueEnd(String valueEnd) {
        this.valueEnd = valueEnd;
    }

    public String getLogic() {
        return logic;
    }

    public void setLogic(String logic) {
        this.logic = logic;
    }
}
