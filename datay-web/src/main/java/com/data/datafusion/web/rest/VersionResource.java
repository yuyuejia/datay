package com.data.datafusion.web.rest;

import com.data.datafusion.web.rest.vm.VersionInfoVM;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 版本信息接口，返回应用构建版本、构建时间以及 Git 提交信息。
 */
@RestController
@RequestMapping("/api")
public class VersionResource {

    private static final Logger LOG = LoggerFactory.getLogger(VersionResource.class);

    private static final String UNKNOWN = "unknown";

    private final String applicationName;

    private final ObjectProvider<BuildProperties> buildPropertiesProvider;

    private final ObjectProvider<GitProperties> gitPropertiesProvider;

    public VersionResource(
        @Value("${jhipster.clientApp.name:datay}") String applicationName,
        ObjectProvider<BuildProperties> buildPropertiesProvider,
        ObjectProvider<GitProperties> gitPropertiesProvider
    ) {
        this.applicationName = applicationName;
        this.buildPropertiesProvider = buildPropertiesProvider;
        this.gitPropertiesProvider = gitPropertiesProvider;
    }

    /**
     * {@code GET /api/version} : 获取当前应用的版本信息。
     *
     * @return 版本信息，缺失的构建/提交信息以 {@code unknown} 或 {@code null} 返回。
     */
    @GetMapping("/version")
    public ResponseEntity<VersionInfoVM> getVersion() {
        LOG.debug("REST request to get application version information");
        BuildProperties build = buildPropertiesProvider.getIfAvailable();
        GitProperties git = gitPropertiesProvider.getIfAvailable();
        String version = build != null && build.getVersion() != null ? build.getVersion() : UNKNOWN;

        return ResponseEntity.ok(
            new VersionInfoVM(
                applicationName,
                version,
                build != null ? build.getArtifact() : null,
                build != null ? build.getGroup() : null,
                build != null && build.getTime() != null ? DateTimeFormatter.ISO_INSTANT.format(build.getTime()) : null,
                git != null ? git.getShortCommitId() : null,
                git != null ? git.getBranch() : null,
                git != null ? git.get("commit.id.describe") : null,
                System.getProperty("java.version")
            )
        );
    }
}
