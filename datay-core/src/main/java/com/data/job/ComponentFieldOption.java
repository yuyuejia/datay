package com.data.job;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 组件配置字段的下拉选项定义。
 * <p>
 * 与 {@link ComponentField#options()} 配合使用，用于 {@link ComponentFieldType#SELECT}、
 * {@link ComponentFieldType#MULTI_SELECT}、{@link ComponentFieldType#RADIO}、
 * {@link ComponentFieldType#CHECKBOX_GROUP} 等选项类控件。
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface ComponentFieldOption {

    /** 选项值。 */
    String value();

    /** 选项显示名称，为空时使用 value。 */
    String label() default "";

    /** 选项说明。 */
    String description() default "";
}
