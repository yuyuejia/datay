package com.data.datafusion.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link DeploymentProperties} 部署角色判定的单元测试。
 */
class DeploymentPropertiesTest {

    /**
     * 按配置值构造部署配置：mode 为 null 时不调用 setter，走字段默认值 standalone。
     */
    private DeploymentProperties of(String mode) {
        DeploymentProperties properties = new DeploymentProperties();
        if (mode != null) {
            properties.setMode(mode);
        }
        return properties;
    }

    @Test
    void defaultModeIsStandalone() {
        DeploymentProperties properties = new DeploymentProperties();
        assertThat(properties.getMode()).isEqualTo(DeploymentProperties.MODE_STANDALONE);
        assertThat(properties.isSchedulerEnabled()).isTrue();
        assertThat(properties.isWorkerEnabled()).isTrue();
        assertThat(properties.isClusterEventChannel()).isFalse();
    }

    @Test
    void standaloneHasBothSchedulerAndWorkerCapabilities() {
        DeploymentProperties properties = of(DeploymentProperties.MODE_STANDALONE);
        assertThat(properties.isSchedulerEnabled()).isTrue();
        assertThat(properties.isWorkerEnabled()).isTrue();
        assertThat(properties.isClusterEventChannel()).isFalse();
    }

    @Test
    void masterOnlyHasSchedulerCapabilityAndNeedsRedis() {
        DeploymentProperties properties = of(DeploymentProperties.MODE_MASTER);
        assertThat(properties.isSchedulerEnabled()).isTrue();
        assertThat(properties.isWorkerEnabled()).isFalse();
        assertThat(properties.isClusterEventChannel()).isTrue();
        assertThat(properties.hasRole(DeploymentProperties.MODE_MASTER)).isTrue();
    }

    @Test
    void workerOnlyHasWorkerCapabilityAndNeedsRedis() {
        DeploymentProperties properties = of(DeploymentProperties.MODE_WORKER);
        assertThat(properties.isSchedulerEnabled()).isFalse();
        assertThat(properties.isWorkerEnabled()).isTrue();
        assertThat(properties.isClusterEventChannel()).isTrue();
    }

    @Test
    void masterAndWorkerInOneServiceHasBothCapabilities() {
        DeploymentProperties properties = of("master,worker");
        assertThat(properties.getMode()).isEqualTo("master,worker");
        assertThat(properties.getRoles()).containsExactlyInAnyOrder(DeploymentProperties.MODE_MASTER, DeploymentProperties.MODE_WORKER);
        assertThat(properties.isSchedulerEnabled()).isTrue();
        assertThat(properties.isWorkerEnabled()).isTrue();
        assertThat(properties.isClusterEventChannel()).isTrue();
    }

    @Test
    void rolesSupportAlternativeSeparatorsAndOrder() {
        assertThat(of(" worker  master ").getMode()).isEqualTo("worker,master");
        assertThat(of("master;worker").getMode()).isEqualTo("master,worker");
        assertThat(of("MASTER,Worker").getMode()).isEqualTo("master,worker");
    }

    @Test
    void duplicatedRolesAreDeduplicated() {
        assertThat(of("master,worker,master").getMode()).isEqualTo("master,worker");
    }

    @Test
    void standaloneMixedWithRolesKeepsAllDeclaredCapabilities() {
        // standalone 是部署形态，worker 是额外承担的职能，两种能力都要保留
        DeploymentProperties worker = of("standalone,worker");
        assertThat(worker.getMode()).isEqualTo("standalone,worker");
        assertThat(worker.getRoles()).containsExactlyInAnyOrder(DeploymentProperties.MODE_STANDALONE, DeploymentProperties.MODE_WORKER);
        assertThat(worker.isSchedulerEnabled()).isTrue();
        assertThat(worker.isWorkerEnabled()).isTrue();
        // 额外承担 worker 职能后进程内不再只有本机事件，需要走 Redis 事件通道
        assertThat(worker.isClusterEventChannel()).isTrue();

        DeploymentProperties master = of("master,standalone");
        assertThat(master.getMode()).isEqualTo("master,standalone");
        assertThat(master.isSchedulerEnabled()).isTrue();
        assertThat(master.isWorkerEnabled()).isTrue();
        assertThat(master.isClusterEventChannel()).isTrue();
    }

    @Test
    void unknownModeFallsBackToStandalone() {
        assertThat(of("cluster").getMode()).isEqualTo(DeploymentProperties.MODE_STANDALONE);
        assertThat(of("unknown").getMode()).isEqualTo(DeploymentProperties.MODE_STANDALONE);
    }

    @Test
    void modeIsCaseInsensitiveAndTrimmed() {
        assertThat(of(" WORKER ").getMode()).isEqualTo(DeploymentProperties.MODE_WORKER);
        assertThat(of("Master").getMode()).isEqualTo(DeploymentProperties.MODE_MASTER);
    }

    @Test
    void blankModeFallsBackToStandalone() {
        assertThat(of("").getMode()).isEqualTo(DeploymentProperties.MODE_STANDALONE);
        assertThat(of(null).getMode()).isEqualTo(DeploymentProperties.MODE_STANDALONE);
    }
}
