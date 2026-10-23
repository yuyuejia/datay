package com.data.datafusion.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.service.JobInstanceService;
import com.data.datafusion.service.TaskManagementService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkerResourceFailureLogTest {

    @TempDir
    Path tempDir;

    private final WorkerResource workerResource = new WorkerResource(
        mock(TaskManagementService.class),
        mock(JobInstanceService.class)
    );

    @Test
    void readsErrorLinesFromTaskLog() throws Exception {
        Path logDir = tempDir.resolve("log").resolve("job-1");
        Files.createDirectories(logDir);
        Files.writeString(
            logDir.resolve("instance-1.log"),
            "2026-10-23 10:00:00 - 开始执行\n" +
            "2026-10-23 10:00:01 - [Component] - [ERROR] 组件执行失败：数据库连接超时\n" +
            "2026-10-23 10:00:02 - 执行结束\n",
            StandardCharsets.UTF_8
        );

        String log = withUserDir(tempDir, () -> workerResource.readFailureLog(instance("job-1", "instance-1", TaskConstants.TASK_TYPE_SQL)));

        assertThat(log).contains("[ERROR] 组件执行失败：数据库连接超时").doesNotContain("开始执行");
    }

    @Test
    void aggregatesDagSubTaskLogs() throws Exception {
        Path subTaskLogDir = tempDir.resolve("log").resolve("sub-job-1");
        Files.createDirectories(subTaskLogDir);
        Files.writeString(
            subTaskLogDir.resolve("dag-instance-1.log"),
            "2026-10-23 10:00:00 - 子任务执行失败：目标表不存在\n",
            StandardCharsets.UTF_8
        );

        String log = withUserDir(tempDir, () ->
            workerResource.readFailureLog(instance("dag-job-1", "dag-instance-1", TaskConstants.TASK_TYPE_DAG))
        );

        assertThat(log).contains("子任务执行失败：目标表不存在");
    }

    private JobInstance instance(String jobCode, String instanceCode, String type) {
        JobInstance instance = new JobInstance();
        instance.setJobCode(jobCode);
        instance.setInstanceCode(instanceCode);
        instance.setType(type);
        return instance;
    }

    private String withUserDir(Path dir, Supplier<String> action) {
        String original = System.getProperty("user.dir");
        System.setProperty("user.dir", dir.toString());
        try {
            return action.get();
        } finally {
            System.setProperty("user.dir", original);
        }
    }
}
