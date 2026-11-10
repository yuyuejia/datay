package com.data.job;

/**
 * 组件配置字段在任务 config JSON 中的值格式。
 * <p>
 * 用于兼容不同组件已有的配置结构，例如多表配置在部分组件中是数组，
 * 在另一些组件中是逗号分隔字符串。
 */
public enum ComponentFieldValueFormat {

    /** 由前端根据字段类型自动推断。 */
    AUTO,

    /** 字符串。 */
    STRING,

    /** 数字。 */
    NUMBER,

    /** 布尔值。 */
    BOOLEAN,

    /** 数组。 */
    ARRAY,

    /** 逗号分隔字符串，例如 {@code "t1,t2"}。 */
    COMMA_STRING
}
