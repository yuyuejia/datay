package com.data.datafusion.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link DeploymentProperties} 部署角色判定的单元测试。
 */
class DeploymentPropertiesTest {

    private DeploymentProperties of(String mode) {
        DeploymentProperties properties = new DeploymentProperties();
        properties.setMode(mode);
        return properties;
    }

    @Test
    void defaultModeIsStandalone() {
        DeploymentProperties properties = new DeploymentProperties();
        assertThat(properties.getMode()).isEqualTo(DeploymentProperties.MODE_STANDALONE);
        assertThat(properties.isSchedulerEnabled()).isTrue();
        assertThat(properties.isWorkerEnabled()).isTrue();
        assertThat(properties.isClusterMode()).isFalse();
    }

    @Test
    void standaloneHasBothSchedulerAndWorkerCapabilities() {
        DeploymentProperties properties = of(DeploymentProperties.MODE_STANDALONE);
        assertThat(properties.isSchedulerEnabled()).isTrue();
        assertThat(properties.isWorkerEnabled()).isTrue();
        assertThat(properties.isClusterMode()).isFalse();
    }

    @Test
    void masterOnlyHasSchedulerCapabilityAndNeedsRedis() {
        DeploymentProperties properties = of(DeploymentProperties.MODE_MASTER);
        assertThat(properties.isSchedulerEnabled()).isTrue();
        assertThat(properties.isWorkerEnabled()).isFalse();
        assertThat(properties.isClusterMode()).isTrue();
        assertThat(properties.hasRole(DeploymentProperties.MODE_MASTER)).isTrue();
    }

    @Test
    void workerOnlyHasWorkerCapabilityAndNeedsRedis() {
        DeploymentProperties properties = of(DeploymentProperties.MODE_WORKER);
        assertThat(properties.isSchedulerEnabled()).isFalse();
        assertThat(properties.isWorkerEnabled()).isTrue();
        assertThat(properties.isClusterMode()).isTrue();
    }

    @Test
    void masterAndWorkerInOneServiceHasBothCapabilities() {
        DeploymentProperties properties = of("master,worker");
        assertThat(properties.getMode()).isEqualTo("master,worker");
        assertThat(properties.getRoles()).containsExactlyInAnyOrder(DeploymentProperties.MODE_MASTER, DeploymentProperties.MODE_WORKER);
        assertThat(properties.isSchedulerEnabled()).isTrue();
        assertThat(properties.isWorkerEnabled()).isTrue();
        assertThat(properties.isClusterMode()).isTrue();
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
    void standaloneMixedWithRolesKeepsOnlyTheExplicitRole() {
        DeploymentProperties worker = of("standalone,worker");
        assertThat(worker.getMode()).isEqualTo("worker");
        assertThat(worker.isSchedulerEnabled()).isFalse();
        assertThat(worker.isWorkerEnabled()).isTrue();

        DeploymentProperties master = of("master,standalone");
        assertThat(master.getMode()).isEqualTo("master");
        assertThat(master.isSchedulerEnabled()).isTrue();
        assertThat(master.isWorkerEnabled()).isFalse();
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
