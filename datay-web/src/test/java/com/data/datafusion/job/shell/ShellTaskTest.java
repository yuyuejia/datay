package com.data.datafusion.job.shell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ShellTaskTest {

    private JobInstance createJobInstance(String instanceCode, String scriptContent) {
        JobInstance jobInstance = new JobInstance();
        jobInstance.setJobCode("test-job");
        jobInstance.setInstanceCode(instanceCode);
        jobInstance.setType(TaskConstants.TASK_TYPE_SHELL);
        jobInstance.setJobContext(scriptContent);
        return jobInstance;
    }

    @Test
    @Timeout(30000)
    void testSimpleEchoCommand() throws Exception {
        ShellTask shellTask = new ShellTask(createJobInstance("test-simple-echo", "echo 'hello world'\nexit 0"));
        String result = shellTask.doExecute();
        assertThat(result).isEqualTo("0");
    }

    @Test
    @Timeout(30000)
    void testExitCodeZero() throws Exception {
        ShellTask shellTask = new ShellTask(createJobInstance("test-exit-zero", "exit 0"));
        String result = shellTask.doExecute();
        assertThat(result).isEqualTo("0");
    }

    @Test
    @Timeout(30000)
    void testNonZeroExitCode() {
        ShellTask shellTask = new ShellTask(createJobInstance("test-exit-one", "exit 1"));
        Exception exception = assertThrows(Exception.class, shellTask::doExecute);
        assertThat(exception.getMessage()).isEqualTo("job exec error!");
    }

    @Test
    @Timeout(30000)
    void testMultiLineScript() throws Exception {
        String script = "name='world'\necho \"hello $name\"\nexit 0";
        ShellTask shellTask = new ShellTask(createJobInstance("test-multiline", script));
        String result = shellTask.doExecute();
        assertThat(result).isEqualTo("0");
    }

    @Test
    @Timeout(30000)
    void testScriptWithPipe() throws Exception {
        String script = "echo 'test123' | grep 'test'\nexit 0";
        ShellTask shellTask = new ShellTask(createJobInstance("test-pipe", script));
        String result = shellTask.doExecute();
        assertThat(result).isEqualTo("0");
    }

    @Test
    @Timeout(30000)
    void testScriptWithVariableExpansion() throws Exception {
        String script = "MSG='hello'\necho \"$MSG\"\nexit 0";
        ShellTask shellTask = new ShellTask(createJobInstance("test-variable", script));
        String result = shellTask.doExecute();
        assertThat(result).isEqualTo("0");
    }

    @Test
    @Timeout(30000)
    void testScriptFileCreatedInLogDirectory() throws Exception {
        String instanceCode = "test-file-location";
        ShellTask shellTask = new ShellTask(createJobInstance(instanceCode, "echo 'file location test'\nexit 0"));
        shellTask.doExecute();

        // 验证脚本文件已被清理（不在临时目录中）
        Path tempScript = Paths.get(System.getProperty("java.io.tmpdir"), "shell-task-" + instanceCode + ".sh");
        assertThat(tempScript).doesNotExist();

        // 验证脚本文件不在日志目录中（已被 finally 清理）
        Path logScript = Paths.get(System.getProperty("user.dir"), "log", "test-job", instanceCode + ".sh");
        assertThat(logScript).doesNotExist();
    }

    @Test
    @Timeout(30000)
    void testLogDirectoryCreated() throws Exception {
        String instanceCode = "test-log-dir";
        ShellTask shellTask = new ShellTask(createJobInstance(instanceCode, "echo 'log dir test'\nexit 0"));
        shellTask.doExecute();

        // 验证日志目录确实被创建过（脚本运行期间存在，之后被清理）
        Path logDir = Paths.get(System.getProperty("user.dir"), "log", "test-job");
        assertThat(logDir).isDirectory();
    }

    @Test
    @Timeout(30000)
    void testCancelBeforeExecution() {
        JobInstance jobInstance = createJobInstance("test-cancel", "sleep 30\nexit 0");
        ShellTask shellTask = new ShellTask(jobInstance);
        shellTask.cancel();

        assertThrows(InterruptedException.class, shellTask::doExecute);
    }
}