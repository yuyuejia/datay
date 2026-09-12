package com.data.datafusion.config;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 条件匹配：集群类部署模式（master / worker / cluster）。
 *
 * <p>standalone 为单机部署，不注册 Redis 与 Leader 选举相关 Bean，避免引入 Redis 强依赖。</p>
 */
public class ClusterDeploymentCondition extends AnyNestedCondition {

    public ClusterDeploymentCondition() {
        super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(name = DeploymentProperties.MODE_KEY, havingValue = DeploymentProperties.MODE_MASTER)
    static class MasterMode {}

    @ConditionalOnProperty(name = DeploymentProperties.MODE_KEY, havingValue = DeploymentProperties.MODE_WORKER)
    static class WorkerMode {}

    @ConditionalOnProperty(name = DeploymentProperties.MODE_KEY, havingValue = DeploymentProperties.MODE_CLUSTER)
    static class ClusterMode {}
}
