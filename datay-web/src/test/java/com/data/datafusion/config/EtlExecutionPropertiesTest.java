package com.data.datafusion.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link EtlExecutionProperties} 的单元测试。
 */
class EtlExecutionPropertiesTest {

    @Test
    void defaultModeIsLocal() {
        EtlExecutionProperties properties = new EtlExecutionProperties();
        assertThat(properties.getMode()).isEqualTo(EtlExecutionProperties.MODE_LOCAL);
        assertThat(properties.isDockerMode()).isFalse();
    }

    @Test
    void dockerModeRecognizedCaseInsensitively() {
        EtlExecutionProperties properties = new EtlExecutionProperties();
        properties.setMode("Docker");
        assertThat(properties.isDockerMode()).isTrue();
    }

    @Test
    void nullOrBlankModeIsNotDocker() {
        EtlExecutionProperties properties = new EtlExecutionProperties();
        properties.setMode(null);
        assertThat(properties.isDockerMode()).isFalse();
        properties.setMode("  ");
        assertThat(properties.isDockerMode()).isFalse();
    }

    @Test
    void dockerDefaults() {
        EtlExecutionProperties.Docker docker = new EtlExecutionProperties.Docker();
        assertThat(docker.getImage()).isEqualTo("eclipse-temurin:17-jre");
        assertThat(docker.getBinary()).isEqualTo("docker");
        assertThat(docker.getContainerWorkDir()).isEqualTo("/app");
        assertThat(docker.getContainerNamePrefix()).isEqualTo("datay-etl-");
        assertThat(docker.getContainerDriverDir()).isEqualTo("/app/drivers");
        assertThat(docker.isHostAccess()).isTrue();
        assertThat(docker.getHostAccessAlias()).isEqualTo("host.docker.internal");
        assertThat(docker.isAddHostGateway()).isTrue();
        assertThat(docker.getLibDirs()).isEmpty();
        assertThat(docker.getExtraMounts()).isEmpty();
        assertThat(docker.getExtraEnv()).isEmpty();
    }

    @Test
    void nullCollectionsAreNormalizedToEmpty() {
        EtlExecutionProperties.Docker docker = new EtlExecutionProperties.Docker();
        docker.setLibDirs(null);
        docker.setExtraMounts(null);
        docker.setExtraEnv(null);
        assertThat(docker.getLibDirs()).isNotNull().isEmpty();
        assertThat(docker.getExtraMounts()).isNotNull().isEmpty();
        assertThat(docker.getExtraEnv()).isNotNull().isEmpty();
    }
}
