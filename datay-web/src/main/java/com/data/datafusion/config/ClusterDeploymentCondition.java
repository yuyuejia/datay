package com.data.datafusion.config;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

/**
 * 条件匹配：集群类部署角色（master / worker，可同时具备）。
 *
 * <p>多角色用逗号分隔配置，如 {@code development.mode=master,worker}，
 * 因此用 {@link ConditionalOnExpression} 判断角色集合中是否包含 master 或 worker。
 * standalone 为单机部署，不注册 Redis 与 Leader 选举相关 Bean，避免引入 Redis 强依赖。</p>
 */
public class ClusterDeploymentCondition extends AnyNestedCondition {

    public ClusterDeploymentCondition() {
        super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnExpression("'${development.mode:standalone}'.contains('master')")
    static class MasterRole {}

    @ConditionalOnExpression("'${development.mode:standalone}'.contains('worker')")
    static class WorkerRole {}
}
