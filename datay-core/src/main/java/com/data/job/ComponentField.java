package com.data.job;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ETL 组件配置字段注解。
 * <p>
 * 标记在组件实现类的配置字段上，由 {@link ComponentMetadataExtractor} 在组件注册时扫描，
 * 生成前端动态表单所需的字段元数据。只有声明了该注解的组件才会使用前端动态表单，
 * 未声明的组件继续使用设计器原有的 Vue 配置页面。
 * <p>
 * 示例：
 * <pre>{@code
 * @ComponentField(
 *     key = "sourceId",
 *     label = "目标数据源",
 *     type = ComponentFieldType.DATASOURCE,
 *     dataSourceType = "JDBC",
 *     required = true,
 *     group = "基础配置",
 *     order = 10
 * )
 * private DatasourceInfo datasource;
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ComponentField {

    /**
     * 配置 key。默认使用 Java 字段名。
     * <p>
     * 用于字段名与配置 key 不一致的场景，例如组件内部字段为 {@code datasource}，
     * 而任务 config 中的 key 为 {@code sourceId}。
     */
    String key() default "";

    /** 字段显示名称。为空时使用配置 key。 */
    String label() default "";

    /**
     * 前端控件类型。
     * <p>
     * {@link ComponentFieldType#DATASOURCE} 字段会在任务保存或调试时由平台解析为完整数据源
     * 连接信息，并写回该字段的配置 key。
     */
    ComponentFieldType type() default ComponentFieldType.INPUT;

    /** 表单分组名称，同组字段会渲染在一起。 */
    String group() default "基础配置";

    /** 同组内排序值，值越小越靠前。 */
    int order() default 0;

    /** 是否必填。 */
    boolean required() default false;

    /** 输入框占位提示。 */
    String placeholder() default "";

    /** 字段下方的帮助说明。 */
    String help() default "";

    /** 正则校验表达式，为空表示不校验。 */
    String pattern() default "";

    /** 正则校验失败提示。 */
    String patternMessage() default "";

    /** 默认值，使用字符串表示，由前端按字段类型转换。 */
    String defaultValue() default "";

    /** 选项列表，用于 SELECT / MULTI_SELECT / RADIO / CHECKBOX_GROUP。 */
    ComponentFieldOption[] options() default {};

    /** 是否多选（SELECT / TABLE / COLUMN 等类型使用）。 */
    boolean multiple() default false;

    /** 是否为敏感字段（密码类）。 */
    boolean secret() default false;

    /** 字段值在任务 config JSON 中的格式。 */
    ComponentFieldValueFormat valueFormat() default ComponentFieldValueFormat.AUTO;

    /** 显隐依赖的配置 key。为空表示始终显示。 */
    String dependsOn() default "";

    /** 依赖字段的值命中这些值时显示当前字段。为空表示不限制依赖值。 */
    String[] visibleWhen() default {};

    /** 数据源类型过滤，例如 JDBC、INFLUXDB、MONGODB。 */
    String dataSourceType() default "";

    /** 关联的数据源配置 key。 */
    String dataSourceField() default "sourceId";

    /** 关联的 Schema 配置 key。 */
    String schemaField() default "schema";

    /** 关联的表配置 key。 */
    String tableField() default "table";

    /** 数字最小值，{@link Double#NaN} 表示不限制。 */
    double min() default Double.NaN;

    /** 数字最大值，{@link Double#NaN} 表示不限制。 */
    double max() default Double.NaN;

    /** 数字步长，0 表示不限制。 */
    double step() default 0;

    /** type 为 CUSTOM 时使用的前端自定义字段组件名。 */
    String customComponent() default "";
}
