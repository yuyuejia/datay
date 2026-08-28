package com.data.datafusion.job.shell;

import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.AbstractTask;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
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
        String command = getJobInstance().getJobContext();

        CommandLine commandLine = CommandLine.parse(command);
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
        }
        if (code != 0) {
            if (cancelled.get()) {
                throw new InterruptedException("Task was cancelled manually");
            }
            throw new Exception("job exec error!");
        }
        return String.valueOf(code);
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