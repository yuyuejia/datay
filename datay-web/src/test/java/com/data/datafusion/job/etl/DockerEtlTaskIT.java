package com.data.datafusion.job.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.data.datafusion.config.EtlExecutionProperties;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link DockerEtlTask} 的真实 docker 集成测试。
 *
 * <p>当本机 docker 不可用或未构建 {@code datay-core} 可执行 jar 时自动跳过。</p>
 */
class DockerEtlTaskIT {

    private static final String IMAGE = "eclipse-temurin:17-jre";

    private static final String SIMPLE_JOB = """
        {
          "units": [
            { ".id": "gen", ".name": "GenerateFlowFile", "inputData": "docker-it", "dataFormat": "TEXT", "loopCount": "2" },
            { ".id": "log_out", ".name": "LogFlowFile", "logDataContent": "true", "maxDataLength": "200" }
          ],
          "connections": [ { "sourceId": "gen", "targetId": "log_out" } ],
          "version": "1.0.0"
        }
        """;

    @TempDir
    Path tempDir;

    private static class TestDockerEtlTask extends DockerEtlTask {

        private final EtlExecutionProperties properties;

        TestDockerEtlTask(JobInstance jobInstance, EtlExecutionProperties properties) {
            super(jobInstance);
            this.properties = properties;
        }

        @Override
        protected EtlExecutionProperties getExecutionProperties() {
            return properties;
        }
    }

    private static boolean dockerAvailable() {
        try {
            Process p = new ProcessBuilder("docker", "version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static Path findCoreJar() {
        Path target = Paths.get(System.getProperty("user.dir")).resolve("..").resolve("datay-core").resolve("target");
        if (!Files.isDirectory(target)) {
            return null;
        }
        try (Stream<Path> stream = Files.list(target)) {
            return stream
                .filter(p -> p.getFileName().toString().matches("datay-core-.*-jar-with-dependencies\\.jar"))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private EtlExecutionProperties properties(Path jarPath) {
        EtlExecutionProperties properties = new EtlExecutionProperties();
        properties.setMode(EtlExecutionProperties.MODE_DOCKER);
        EtlExecutionProperties.Docker cfg = properties.getDocker();
        cfg.setBinary("docker");
        cfg.setImage(IMAGE);
        cfg.setJarPath(jarPath.toString());
        cfg.setHostLogDir(tempDir.resolve("log").toString());
        cfg.setDriverDir(tempDir.resolve("drivers").toString());
        return properties;
    }

    private JobInstance jobInstance(String instanceCode, String context) {
        JobInstance jobInstance = new JobInstance();
        jobInstance.setJobCode("docker-it-job");
        jobInstance.setInstanceCode(instanceCode);
        jobInstance.setType(TaskConstants.TASK_TYPE_ETL);
        jobInstance.setJobContext(context);
        return jobInstance;
    }

    @Test
    @Timeout(180000)
    void runsEtlInDockerAndSharesLogToHost() throws Exception {
        assumeTrue(dockerAvailable(), "本机无可用 docker，跳过");
        Path jar = findCoreJar();
        assumeTrue(jar != null && Files.exists(jar), "未找到 datay-core 可执行 jar，跳过");

        DockerEtlTask task = new TestDockerEtlTask(jobInstance("inst-it-1", SIMPLE_JOB), properties(jar));
        String result = task.execute();

        assertThat(result).isEqualTo(TaskConstants.TASK_STATUS_SUCCESSFUL);
        Path logFile = tempDir.resolve("log/docker-it-job/inst-it-1.log");
        assertThat(logFile).exists();
        assertThat(Files.readString(logFile, StandardCharsets.UTF_8)).contains("docker-it");
    }

    @Test
    @Timeout(180000)
    void failingJobMapsToFailed() throws Exception {
        assumeTrue(dockerAvailable(), "本机无可用 docker，跳过");
        Path jar = findCoreJar();
        assumeTrue(jar != null && Files.exists(jar), "未找到 datay-core 可执行 jar，跳过");

        DockerEtlTask task = new TestDockerEtlTask(jobInstance("inst-it-fail", "{ not-a-valid-job }"), properties(jar));
        assertThat(task.execute()).isEqualTo(TaskConstants.TASK_STATUS_FAILED);
    }
}
