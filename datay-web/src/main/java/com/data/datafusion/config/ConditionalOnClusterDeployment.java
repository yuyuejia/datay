package com.data.datafusion.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Conditional;

/**
 * 条件注解：仅在集群类部署角色（master / worker）下生效。
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Conditional(ClusterDeploymentCondition.class)
public @interface ConditionalOnClusterDeployment {
}
