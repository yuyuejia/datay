package com.data.datafusion.job.etl;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.config.EtlExecutionProperties;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link DockerEtlTask} 的单元测试：用假的 docker 脚本替代真实 docker，验证命令透传、
 * 环境变量注入、退出码映射与取消清理。
 */
class DockerEtlTaskTest {

    @TempDir
    Path tempDir;

    /**
     * 测试用任务：直接返回测试配置，避免依赖 Spring 上下文。
     */
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

    private Path writeFakeDocker(Path marker) throws Exception {
        Path script = tempDir.resolve("docker");
        String content = "#!/bin/sh\n" + "echo \"$@\" >> \"" + marker + "\"\n"
            + "echo \"DATAY_JOB_CODE=$DATAY_JOB_CODE\" >> \"" + marker + "\"\n"
            + "echo \"DATAY_STATUS_TYPE=$DATAY_STATUS_TYPE\" >> \"" + marker + "\"\n"
            + "if [ \"$1\" = \"run\" ] && [ -n \"$FAKE_DOCKER_SLEEP\" ]; then\n"
            + "  sleep \"$FAKE_DOCKER_SLEEP\"\n"
            + "fi\n"
            + "exit ${FAKE_DOCKER_EXIT:-0}\n";
        Files.writeString(script, content, StandardCharsets.UTF_8);
        script.toFile().setExecutable(true);
        return script;
    }

    private EtlExecutionProperties properties(Path fakeDocker) throws Exception {
        EtlExecutionProperties properties = new EtlExecutionProperties();
        properties.setMode(EtlExecutionProperties.MODE_DOCKER);
        EtlExecutionProperties.Docker cfg = properties.getDocker();
        cfg.setBinary(fakeDocker.toString());
        // 独立的 core jar 运行时需要一个真实存在的 jar 文件
        Path coreJar = tempDir.resolve("datay-core.jar");
        Files.writeString(coreJar, "stub", StandardCharsets.UTF_8);
        cfg.setJarPath(coreJar.toString());
        cfg.setHostLogDir(tempDir.resolve("log").toString());
        cfg.setDriverDir(tempDir.resolve("drivers").toString());
        return properties;
    }

    private JobInstance jobInstance(String instanceCode) {
        JobInstance jobInstance = new JobInstance();
        jobInstance.setJobCode("job1");
        jobInstance.setInstanceCode(instanceCode);
        jobInstance.setType(TaskConstants.TASK_TYPE_ETL);
        jobInstance.setJobContext("{\"units\":[]}");
        return jobInstance;
    }

    @Test
    @Timeout(30000)
    void executesDockerRunAndInjectsEnv() throws Exception {
        Path marker = tempDir.resolve("marker.txt");
        Path fakeDocker = writeFakeDocker(marker);
        EtlExecutionProperties props = properties(fakeDocker);

        DockerEtlTask task = new TestDockerEtlTask(jobInstance("inst-ok"), props);
        String result = task.execute();

        assertThat(result).isEqualTo(TaskConstants.TASK_STATUS_SUCCESSFUL);
        String content = Files.readString(marker);
        assertThat(content)
            .contains("run")
            .contains("--rm")
            .contains("--name datay-etl-inst-ok")
            .contains("-cp")
            .contains("com.data.DataY")
            .contains("/app/log/job1/inst-ok.job.json")
            .contains("DATAY_JOB_CODE=job1")
            .contains("DATAY_STATUS_TYPE=local");
        // 任务 JSON 执行后被清理
        assertThat(tempDir.resolve("log/job1/inst-ok.job.json")).doesNotExist();
    }

    @Test
    @Timeout(30000)
    void embeddedWorkerJarRuntimeUsesLauncher() throws Exception {
        Path marker = tempDir.resolve("marker.txt");
        Path fakeDocker = writeFakeDocker(marker);
        EtlExecutionProperties props = properties(fakeDocker);
        props.getDocker().setJarPath(null);
        Path workerJar = tempDir.resolve("datay-web.jar");
        Files.writeString(workerJar, "stub", StandardCharsets.UTF_8);
        props.getDocker().setWorkerJarPath(workerJar.toString());

        DockerEtlTask task = new TestDockerEtlTask(jobInstance("inst-embedded"), props);
        String result = task.execute();

        assertThat(result).isEqualTo(TaskConstants.TASK_STATUS_SUCCESSFUL);
        String content = Files.readString(marker);
        assertThat(content)
            .contains("datay-web.jar:/app/datay-web.jar:ro")
            .contains("-Dloader.main=com.data.DataY")
            .contains("org.springframework.boot.loader.launch.PropertiesLauncher");
    }

    @Test
    @Timeout(30000)
    void devModeAutoDetectsRuntimeWithoutJarConfig() throws Exception {
        Path marker = tempDir.resolve("marker.txt");
        Path fakeDocker = writeFakeDocker(marker);
        EtlExecutionProperties props = properties(fakeDocker);
        // 模拟开发态：只切换为 docker 模式，不配置任何 jar 路径
        props.getDocker().setJarPath(null);
        props.getDocker().setWorkerJarPath(null);

        DockerEtlTask task = new TestDockerEtlTask(jobInstance("inst-dev"), props);
        String result = task.execute();

        assertThat(result).isEqualTo(TaskConstants.TASK_STATUS_SUCCESSFUL);
        assertThat(Files.readString(marker)).contains("com.data.DataY");
    }

    @Test
    void parseCodeSourceUrlHandlesJarAndSpringBootNestedForms() {
        assertThat(DockerEtlTask.parseCodeSourceUrl("file:/opt/app.jar")).hasToString("/opt/app.jar");
        assertThat(DockerEtlTask.parseCodeSourceUrl("jar:file:/opt/datay-web.jar!/BOOT-INF/classes!/")).hasToString(
            "/opt/datay-web.jar"
        );
        assertThat(DockerEtlTask.parseCodeSourceUrl("jar:nested:/opt/datay-web.jar/!BOOT-INF/classes/!/")).hasToString(
            "/opt/datay-web.jar"
        );
        assertThat(DockerEtlTask.parseCodeSourceUrl("nested:/opt/datay-web.jar/!BOOT-INF/classes/")).hasToString(
            "/opt/datay-web.jar"
        );
    }

    @Test
    @Timeout(30000)
    void nonZeroExitCodeMapsToFailed() throws Exception {
        Path marker = tempDir.resolve("marker.txt");
        Path fakeDocker = writeFakeDocker(marker);
        EtlExecutionProperties props = properties(fakeDocker);
        props.getDocker().getExtraEnv().put("FAKE_DOCKER_EXIT", "3");

        DockerEtlTask task = new TestDockerEtlTask(jobInstance("inst-fail"), props);
        String result = task.execute();

        assertThat(result).isEqualTo(TaskConstants.TASK_STATUS_FAILED);
    }

    @Test
    @Timeout(30000)
    void cancelKillsContainerAndInterruptsTask() throws Exception {
        Path marker = tempDir.resolve("marker.txt");
        Path fakeDocker = writeFakeDocker(marker);
        EtlExecutionProperties props = properties(fakeDocker);
        props.getDocker().getExtraEnv().put("FAKE_DOCKER_SLEEP", "3");

        DockerEtlTask task = new TestDockerEtlTask(jobInstance("inst-cancel"), props);

        java.util.concurrent.atomic.AtomicReference<String> status = new java.util.concurrent.atomic.AtomicReference<>();
        Thread runner = new Thread(() -> status.set(task.execute()));
        runner.start();
        // 等待 docker run 启动
        Thread.sleep(500);
        task.cancel();
        runner.join(10000);
        assertThat(runner.isAlive()).isFalse();
        assertThat(status.get()).isEqualTo(TaskConstants.TASK_STATUS_INTERRUPTED);

        // 取消时应执行 docker rm -f 清理容器
        String content = Files.readString(marker);
        assertThat(content).contains("rm -f datay-etl-inst-cancel");
    }
}
