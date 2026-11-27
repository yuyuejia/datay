package com.data.datafusion.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.vm.VersionInfoVM;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * {@link VersionResource} 的单元测试。
 */
class VersionResourceTest {

    @Test
    void shouldReturnBuildAndGitInformation() {
        Properties buildEntries = new Properties();
        buildEntries.setProperty("group", "cn.com.yuyuejia");
        buildEntries.setProperty("artifact", "datay-web");
        buildEntries.setProperty("version", "1.2.5");
        buildEntries.setProperty("time", "2026-11-27T13:40:47.970Z");

        Properties gitEntries = new Properties();
        gitEntries.setProperty("branch", "main");
        gitEntries.setProperty("commit.id.abbrev", "b2439f0");
        gitEntries.setProperty("commit.id.describe", "v1.2.5");

        VersionResource resource = createResource(new BuildProperties(buildEntries), new GitProperties(gitEntries));

        ResponseEntity<VersionInfoVM> response = resource.getVersion();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        VersionInfoVM version = response.getBody();
        assertThat(version).isNotNull();
        assertThat(version.application()).isEqualTo("datay");
        assertThat(version.version()).isEqualTo("1.2.5");
        assertThat(version.artifact()).isEqualTo("datay-web");
        assertThat(version.group()).isEqualTo("cn.com.yuyuejia");
        assertThat(version.buildTime()).isEqualTo("2026-11-27T13:40:47.970Z");
        assertThat(version.gitCommit()).isEqualTo("b2439f0");
        assertThat(version.gitBranch()).isEqualTo("main");
        assertThat(version.gitTag()).isEqualTo("v1.2.5");
        assertThat(version.javaVersion()).isNotNull();
    }

    @Test
    void shouldFallbackWhenBuildAndGitInformationIsMissing() {
        VersionResource resource = createResource(null, null);

        ResponseEntity<VersionInfoVM> response = resource.getVersion();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        VersionInfoVM version = response.getBody();
        assertThat(version).isNotNull();
        assertThat(version.version()).isEqualTo("unknown");
        assertThat(version.artifact()).isNull();
        assertThat(version.group()).isNull();
        assertThat(version.buildTime()).isNull();
        assertThat(version.gitCommit()).isNull();
        assertThat(version.gitBranch()).isNull();
        assertThat(version.gitTag()).isNull();
    }

    private VersionResource createResource(BuildProperties buildProperties, GitProperties gitProperties) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        if (buildProperties != null) {
            beanFactory.registerSingleton("buildProperties", buildProperties);
        }
        if (gitProperties != null) {
            beanFactory.registerSingleton("gitProperties", gitProperties);
        }
        return new VersionResource(
            "datay",
            beanFactory.getBeanProvider(BuildProperties.class),
            beanFactory.getBeanProvider(GitProperties.class)
        );
    }
}
