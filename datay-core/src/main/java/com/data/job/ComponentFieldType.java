package com.data.job;

/**
 * ETL 组件配置字段的前端展示类型。
 * <p>
 * 由 {@link ComponentField#type()} 指定，前端根据该类型选择对应的表单控件。
 */
public enum ComponentFieldType {

    /** 单行输入框。 */
    INPUT,

    /** 密码输入框。 */
    PASSWORD,

    /** 多行文本域。 */
    TEXTAREA,

    /** 数字输入框。 */
    NUMBER,

    /** 下拉框。 */
    SELECT,

    /** 多选下拉框。 */
    MULTI_SELECT,

    /** 单选按钮组。 */
    RADIO,

    /** 多选复选框组。 */
    CHECKBOX_GROUP,

    /** 开关。 */
    SWITCH,

    /** 数据源选择。 */
    DATASOURCE,

    /** Schema 选择。 */
    SCHEMA,

    /** 数据表选择。 */
    TABLE,

    /** 数据表字段选择。 */
    COLUMN,

    /** 文件选择。 */
    FILE,

    /** 自定义前端字段组件，通过 {@link ComponentField#customComponent()} 指定组件名。 */
    CUSTOM
}
