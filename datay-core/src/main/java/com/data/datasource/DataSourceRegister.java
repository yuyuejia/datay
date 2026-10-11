package com.data.datasource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据源提供者注解，用于标记需要自动注册到 {@link DataSourceProviderFactory} 的
 * {@link DataSourceProvider} 实现类。
 *
 * <p>与 ETL 组件通过 {@code @ComponentRegister} + 包扫描自动注册的机制保持一致，
 * 新增数据源类型只需在 {@code com.data.datasource.provider} 包下实现 provider 并标注本注解。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataSourceRegister {

    /**
     * provider 标识，为空时使用类名。
     */
    String value() default "";

    /**
     * provider 显示名称，为空时使用 value。
     */
    String name() default "";
}
