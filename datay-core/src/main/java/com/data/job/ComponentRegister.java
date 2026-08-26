package com.data.job;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ETL组件注解，用于标记需要自动注册到ComponentFactory的组件类
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ComponentRegister {
    /**
     * 组件名称，用于在ComponentFactory中注册
     */
    String value() default "";
}