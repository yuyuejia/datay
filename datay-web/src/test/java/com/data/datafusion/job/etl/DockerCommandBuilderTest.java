package com.data.datafusion.job.etl;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.config.EtlExecutionProperties;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link DockerCommandBuilder} 命令拼装的单元测试。
 */
class DockerCommandBuilderTest {

    @TempDir
    Path tempDir;

    private EtlExecutionProperties.Docker baseConfig() {
        EtlExecutionProperties.Docker cfg = new EtlExecutionProperties.Docker();
        cfg.setBinary("/usr/local/bin/docker");
        cfg.setImage("eclipse-temurin:17-jre");
        cfg.setJarPath("/opt/datay/datay-core.jar");
        cfg.setContainerWorkDir("/app");
        return cfg;
    }

    @Test
    void buildsRunCommandWithMountsAndClasspath() throws Exception {
        Path driverDir = Files.createDirectories(tempDir.resolve("drivers"));
        EtlExecutionProperties.Docker cfg = baseConfig();
        cfg.setDriverDir(driverDir.toString());

        List<String> cmd = DockerCommandBuilder.build(
            cfg,
            "datay-etl-inst1",
            "/host/log",
            "/app/log/job1/inst1.job.json",
            List.of("DATAY_JOB_CODE", "DATAY_INSTANCE_CODE"),
            DockerCommandBuilder.coreJarRuntime(cfg, cfg.getJarPath())
        );

        assertThat(cmd).startsWith("/usr/local/bin/docker", "run", "--rm", "--name", "datay-etl-inst1");
        assertThat(cmd).containsSubsequence("-w", "/app");
        assertThat(cmd).contains("-v", "/host/log:/app/log");
        assertThat(cmd).contains("-v", "/opt/datay/datay-core.jar:/app/datay-core.jar:ro");
        assertThat(cmd).contains("-v", driverDir + ":/app/drivers");
        assertThat(cmd).contains("-e", "DATAY_JOB_CODE", "-e", "DATAY_INSTANCE_CODE");
        assertThat(cmd).containsSubsequence("eclipse-temurin:17-jre");
        assertThat(cmd).containsSubsequence("java", "-cp", "/app/datay-core.jar", "com.data.DataY", "/app/log/job1/inst1.job.json");
    }

    @Test
    void libDirsAreMountedAndAddedToClasspath() throws Exception {
        Path libDir = Files.createDirectories(tempDir.resolve("my-lib"));
        EtlExecutionProperties.Docker cfg = baseConfig();
        cfg.setLibDirs(List.of(libDir.toString(), tempDir.resolve("missing").toString()));

        List<String> cmd = DockerCommandBuilder.build(cfg, "c", "/host/log", "/app/log/job/inst.job.json", List.of(), DockerCommandBuilder.coreJarRuntime(cfg, cfg.getJarPath()));

        assertThat(cmd).contains("-v", libDir + ":/app/lib/my-lib:ro");
        // 不存在的目录被忽略
        assertThat(cmd).doesNotContain(tempDir.resolve("missing") + ":/app/lib/missing:ro");
        int cpIndex = cmd.indexOf("-cp");
        assertThat(cmd.get(cpIndex + 1)).isEqualTo("/app/datay-core.jar" + File.pathSeparator + "/app/lib/my-lib/*");
    }

    @Test
    void networkMemoryCpusAndExtraMountsAreApplied() {
        EtlExecutionProperties.Docker cfg = baseConfig();
        cfg.setNetwork("host");
        cfg.setMemory("2g");
        cfg.setCpus("2");
        cfg.setJavaArgs("-Xmx1g -Dfoo=bar");
        cfg.setExtraMounts(List.of("/data/jars:/data/jars:ro"));

        List<String> cmd = DockerCommandBuilder.build(cfg, "c", "/host/log", "/app/log/job/inst.job.json", List.of(), DockerCommandBuilder.coreJarRuntime(cfg, cfg.getJarPath()));

        assertThat(cmd).containsSubsequence("--network", "host");
        assertThat(cmd).containsSubsequence("-m", "2g");
        assertThat(cmd).containsSubsequence("--cpus", "2");
        assertThat(cmd).contains("-v", "/data/jars:/data/jars:ro");
        assertThat(cmd).containsSubsequence("java", "-Xmx1g", "-Dfoo=bar", "-cp");
    }

    @Test
    void trailingSlashInWorkDirIsTrimmed() {
        EtlExecutionProperties.Docker cfg = baseConfig();
        cfg.setContainerWorkDir("/app/");
        List<String> cmd = DockerCommandBuilder.build(cfg, "c", "/host/log", "/app/log/job/inst.job.json", List.of(), DockerCommandBuilder.coreJarRuntime(cfg, cfg.getJarPath()));
        assertThat(cmd).contains("-v", "/host/log:/app/log");
        assertThat(cmd).contains("-v", "/opt/datay/datay-core.jar:/app/datay-core.jar:ro");
    }

    @Test
    void embeddedWorkerRuntimeUsesSpringBootLauncher() throws Exception {
        Path libDir = Files.createDirectories(tempDir.resolve("extra-lib"));
        EtlExecutionProperties.Docker cfg = baseConfig();
        cfg.setLibDirs(List.of(libDir.toString()));

        List<String> cmd = DockerCommandBuilder.build(
            cfg,
            "c",
            "/host/log",
            "/app/log/job/inst.job.json",
            List.of(),
            DockerCommandBuilder.workerJarRuntime(cfg, "/opt/datay/datay-web.jar")
        );

        // worker fat jar 挂载为 datay-web.jar，用启动器加载内嵌 BOOT-INF 运行时
        assertThat(cmd).contains("-v", "/opt/datay/datay-web.jar:/app/datay-web.jar:ro");
        assertThat(cmd).containsSubsequence("java", "-Dloader.path=/app/lib/extra-lib", "-cp", "/app/datay-web.jar");
        assertThat(cmd).contains("-Dloader.main=com.data.DataY");
        assertThat(cmd).contains("org.springframework.boot.loader.launch.PropertiesLauncher");
        assertThat(cmd).endsWith("/app/log/job/inst.job.json");
        // 主类由启动器参数指定，而非直接作为 java 主类
        assertThat(cmd.get(cmd.size() - 2)).isEqualTo("org.springframework.boot.loader.launch.PropertiesLauncher");
    }

    @Test
    void explodedRuntimeMountsEntriesAtSamePath() throws Exception {
        Path classes = Files.createDirectories(tempDir.resolve("core-classes"));
        Path depJar = tempDir.resolve("dep.jar");
        Files.writeString(depJar, "stub");

        DockerCommandBuilder.DockerRuntime runtime = DockerCommandBuilder.explodedRuntime(
            List.of(classes.toString(), depJar.toString(), "/not/exist/x.jar")
        );
        assertThat(runtime).isNotNull();
        assertThat(runtime.isEmbedded()).isFalse();

        List<String> cmd = DockerCommandBuilder.build(
            baseConfig(),
            "c",
            "/host/log",
            "/app/log/job/inst.job.json",
            List.of(),
            runtime
        );

        assertThat(cmd).contains("-v", classes + ":" + classes + ":ro");
        assertThat(cmd).contains("-v", depJar + ":" + depJar + ":ro");
        assertThat(cmd).doesNotContain("/not/exist/x.jar");
        int cpIndex = cmd.indexOf("-cp");
        assertThat(cmd.get(cpIndex + 1)).contains(classes.toString()).contains(depJar.toString());
        assertThat(cmd.get(cmd.size() - 2)).isEqualTo("com.data.DataY");
    }

    @Test
    void explodedRuntimeReturnsNullWhenNoValidEntries() {
        assertThat(DockerCommandBuilder.explodedRuntime(List.of("/not/exist/a.jar"))).isNull();
    }

    @Test
    void dataDirIsMountedWhenPresent() throws Exception {
        Path dataDir = Files.createDirectories(tempDir.resolve("data"));
        EtlExecutionProperties.Docker cfg = baseConfig();
        cfg.setDataDir(dataDir.toString());

        List<String> cmd = DockerCommandBuilder.build(
            cfg,
            "c",
            "/host/log",
            "/app/log/job/inst.job.json",
            List.of(),
            DockerCommandBuilder.coreJarRuntime(cfg, cfg.getJarPath())
        );

        assertThat(cmd).contains("-v", dataDir + ":/app/data");
    }

    @Test
    void hostAccessAddsHostGatewayByDefault() {
        EtlExecutionProperties.Docker cfg = baseConfig();
        List<String> cmd = DockerCommandBuilder.build(
            cfg,
            "c",
            "/host/log",
            "/app/log/job/inst.job.json",
            List.of(),
            DockerCommandBuilder.coreJarRuntime(cfg, cfg.getJarPath())
        );
        assertThat(cmd).containsSubsequence("--add-host", "host.docker.internal:host-gateway");
    }

    @Test
    void hostAccessCanBeDisabled() {
        EtlExecutionProperties.Docker cfg = baseConfig();
        cfg.setHostAccess(false);
        List<String> cmd = DockerCommandBuilder.build(
            cfg,
            "c",
            "/host/log",
            "/app/log/job/inst.job.json",
            List.of(),
            DockerCommandBuilder.coreJarRuntime(cfg, cfg.getJarPath())
        );
        assertThat(cmd).doesNotContain("--add-host");
    }

    @Test
    void resolveHostLogDirDefaultsToWorkingDirLog() {
        String resolved = DockerCommandBuilder.resolveHostLogDir(null);
        assertThat(resolved).isEqualTo(new File(System.getProperty("user.dir"), "log").getAbsolutePath());
        assertThat(DockerCommandBuilder.resolveHostLogDir("/tmp/x")).isEqualTo(new File("/tmp/x").getAbsolutePath());
    }
}
