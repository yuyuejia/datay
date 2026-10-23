package com.data.datafusion.service.dataset;

/**
 * 数据集行级数据权限的单个条件。
 *
 * <p>条件作用于数据集输出字段，运算符取值见 {@link com.data.datafusion.service.metric.MetricFilterOperator}。
 */
public class DatasetScopeCondition {

    /** 数据集输出字段名。 */
    private String fieldName;

    /** 运算符，取值见 {@link com.data.datafusion.service.metric.MetricFilterOperator}。 */
    private String operator;

    /** 条件值；IN / NOT_IN 时可用英文逗号分隔多个值。 */
    private String value;

    /** BETWEEN 时的结束值。 */
    private String valueEnd;

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
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
}
