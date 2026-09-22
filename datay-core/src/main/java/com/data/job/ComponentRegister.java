package com.data.job;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ETL 组件注解，用于标记需要自动注册到 ComponentFactory 的组件类。
 * <p>
 * 组件的编码、显示名称、分类与描述等元数据均在组件实现类上通过该注解定义，
 * 由 {@link ComponentFactory} 自动扫描发现并对外提供组件目录，无需再手工注册到数据库。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ComponentRegister {

    /**
     * 组件编码，用于在 ComponentFactory 中注册及 ETL 节点引用。为空时使用类名。
     */
    String value() default "";

    /**
     * 组件显示名称（前端组件面板展示用），为空时使用 value。
     */
    String name() default "";

    /**
     * 组件分类（前端组件面板分组展示用）。
     */
    String group() default "其他";

    /**
     * 组件描述。
     */
    String desc() default "";

    /**
     * 同分类内的排序值，值越小越靠前。
     */
    int order() default 0;
}
