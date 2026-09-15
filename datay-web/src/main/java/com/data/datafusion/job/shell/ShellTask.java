package com.data.datafusion.job.shell;

import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.AbstractTask;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShellTask extends AbstractTask {

    private final Logger log = LoggerFactory.getLogger(ShellTask.class);

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private volatile Process process;

    private volatile ExecuteWatchdog watchdog;

    public ShellTask(JobInstance jobInstance) {
        super(jobInstance);
    }

    @Override
    public String doExecute() throws Exception {
        String scriptContent = getJobInstance().getJobContext();
        Path tempScript = null;
        try {
            // 根据操作系统确定 shell 和脚本文件扩展名
            String shell;
            String extension;
            if (SystemUtils.IS_OS_WINDOWS) {
                shell = "cmd.exe";
                extension = ".bat";
            } else {
                shell = "/bin/sh";
                extension = ".sh";
            }

            // 在日志目录下创建临时脚本文件，方便排查问题
            String jobCode = getJobCode();
            String instanceCode = getInstanceCode();
            Path logDir = Path.of(System.getProperty("user.dir"), "log", jobCode);
            Files.createDirectories(logDir);
            tempScript = logDir.resolve(instanceCode + extension);
            Files.writeString(tempScript, scriptContent, StandardCharsets.UTF_8);
            File scriptFile = tempScript.toFile();

            // Unix 系统需要设置可执行权限
            if (!SystemUtils.IS_OS_WINDOWS) {
                scriptFile.setExecutable(true);
            }

            // 通过系统 shell 执行临时脚本文件
            CommandLine commandLine = new CommandLine(shell);
            if (SystemUtils.IS_OS_WINDOWS) {
                commandLine.addArgument("/c");
            }
            commandLine.addArgument(scriptFile.getAbsolutePath());

            DefaultExecutor exec = new DefaultExecutor() {
                @Override
                protected Process launch(CommandLine cmd, Map<String, String> env, File dir) throws IOException {
                    Process p = super.launch(cmd, env, dir);
                    process = p;
                    if (cancelled.get()) {
                        p.destroyForcibly();
                    }
                    return p;
                }
            };

            watchdog = new ExecuteWatchdog(60 * 1000);
            exec.setWatchdog(watchdog);
            CollectingLogOutputStream collectingLogOutputStream = new CollectingLogOutputStream(this.getTaskLogger().getLogWriter());
            exec.setStreamHandler(collectingLogOutputStream);
            int code = 0;
            try {
                log.info("开始执行ShellTask组件");
                code = exec.execute(commandLine);
                log.info("ShellTask组件执行完成，返回码：" + code);
            } catch (ExecuteException e) {
                if (watchdog != null && watchdog.killedProcess()) {
                    log.error("超时了");
                }
                if (cancelled.get()) {
                    throw new InterruptedException("Task was cancelled manually");
                }
                code = 1;
            } catch (IOException e) {
                if (cancelled.get()) {
                    throw new InterruptedException("Task was cancelled manually");
                }
                throw e;
            }
            if (code != 0) {
                if (cancelled.get()) {
                    throw new InterruptedException("Task was cancelled manually");
                }
                throw new Exception("job exec error!");
            }
            return String.valueOf(code);
        } finally {
            // 清理临时脚本文件
            if (tempScript != null) {
                try {
                    Files.deleteIfExists(tempScript);
                } catch (IOException e) {
                    log.warn("临时脚本文件删除失败: " + tempScript, e);
                }
            }
        }
    }

    @Override
    public boolean cancel() {
        cancelled.set(true);
        if (process != null) {
            process.destroyForcibly();
        }
        if (watchdog != null) {
            watchdog.destroyProcess();
        }
        return true;
    }
}