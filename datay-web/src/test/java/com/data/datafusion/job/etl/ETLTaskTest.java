package com.data.datafusion.job.etl;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * {@link ETLTask}（local 进程内执行模式）的单元测试。
 *
 * <p>验证默认模式下 ETL 任务仍在 worker 进程内执行并写出任务日志，不受 docker 模式影响。</p>
 */
class ETLTaskTest {

    private static final String SIMPLE_JOB = """
        {
          "units": [
            { ".id": "gen", ".name": "GenerateFlowFile", "inputData": "local-mode-verify", "dataFormat": "TEXT", "loopCount": "2" },
            { ".id": "log_out", ".name": "LogFlowFile", "logDataContent": "true", "maxDataLength": "200" }
          ],
          "connections": [ { "sourceId": "gen", "targetId": "log_out" } ],
          "version": "1.0.0"
        }
        """;

    private JobInstance jobInstance(String instanceCode) {
        JobInstance jobInstance = new JobInstance();
        jobInstance.setJobCode("local-mode-job");
        jobInstance.setInstanceCode(instanceCode);
        jobInstance.setType(TaskConstants.TASK_TYPE_ETL);
        jobInstance.setJobContext(SIMPLE_JOB);
        return jobInstance;
    }

    @Test
    @Timeout(30000)
    void localModeRunsJobInProcessAndWritesLog() {
        ETLTask task = new ETLTask(jobInstance("inst-local-1"));
        String result = task.execute();
        assertThat(result).isEqualTo(TaskConstants.TASK_STATUS_SUCCESSFUL);

        Path logFile = Paths.get(System.getProperty("user.dir"), "log", "local-mode-job", "inst-local-1.log");
        assertThat(logFile).exists();
    }

    @Test
    @Timeout(30000)
    void localModeMapsInvalidJobToFailed() {
        JobInstance jobInstance = jobInstance("inst-local-bad");
        jobInstance.setJobContext("{ not-a-valid-job }");
        ETLTask task = new ETLTask(jobInstance);
        assertThat(task.execute()).isEqualTo(TaskConstants.TASK_STATUS_FAILED);
    }
}
