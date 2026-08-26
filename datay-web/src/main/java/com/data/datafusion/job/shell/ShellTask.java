package com.data.datafusion.job.shell;

import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.AbstractTask;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShellTask extends AbstractTask {

    private final Logger log = LoggerFactory.getLogger(ShellTask.class);

    public ShellTask(JobInstance jobInstance) {
        super(jobInstance);
    }

    @Override
    public String doExecute() throws Exception {
        String command = getJobInstance().getJobContext();
        //        ByteArrayOutputStream susStream = new ByteArrayOutputStream();
        //        ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        //        PumpStreamHandler streamHandler = new PumpStreamHandler(susStream, errStream);

        CommandLine commandLine = CommandLine.parse(command);
        DefaultExecutor exec = new DefaultExecutor();
        //设置一分钟超时
        ExecuteWatchdog watchdog = new ExecuteWatchdog(60 * 1000);
        exec.setWatchdog(watchdog);
        CollectingLogOutputStream collectingLogOutputStream = new CollectingLogOutputStream(this.getTaskLogger().getLogWriter());
        exec.setStreamHandler(collectingLogOutputStream);
        int code = 0;
        try {
            log.info("开始执行ShellTask组件");
            code = exec.execute(commandLine);
            log.info("ShellTask组件执行完成，返回码：" + code);
        } catch (ExecuteException e) {
            if (watchdog.killedProcess()) {
                // 被watchdog故意杀死
                log.error("超时了");
            }
            code = 1;
        }
        if (code != 0) {
            throw new Exception("job exec error!");
        }
        return String.valueOf(code);
    }
}
