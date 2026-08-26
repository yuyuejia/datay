package com.data.datafusion.job;

import com.data.datafusion.domain.JobInstance;
import com.data.job.TaskLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//基础类
public abstract class AbstractTask implements ITask {

    private static final Logger log = LoggerFactory.getLogger(AbstractTask.class);

    private final JobInstance jobInstance;

    private TaskLogger taskLogger;

    protected AbstractTask(JobInstance jobInstance) {
        this.jobInstance = jobInstance;
        initLogFile();
    }

    public String execute() {
        try {
            before();
            doExecute();
            after();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                log.error("任务执行被中断 ：", e);
                return TaskConstants.TASK_STATUS_INTERRUPTED;
            } else {
                log.error("任务执行异常 ：", e);
                return TaskConstants.TASK_STATUS_FAILED;
            }
        }
        return TaskConstants.TASK_STATUS_SUCCESSFUL;
    }

    protected void before() {}

    protected void after() {
        if (taskLogger != null) {
            taskLogger.closeLogFile();
        }
    }

    public void initLogFile() {
        // 单元测试时，JobInstance 为 null，需要初始化一个测试用的 logger
        if (getJobInstance() == null) {
            taskLogger = new TaskLogger("test", String.valueOf(System.currentTimeMillis()));
            return;
        }
        if (getJobInstance().getParentInstanceCode() != null) {
            taskLogger = new TaskLogger(getJobInstance().getJobCode(), getJobInstance().getParentInstanceCode());
        } else {
            taskLogger = new TaskLogger(getJobInstance().getJobCode(), getJobInstance().getInstanceCode());
        }
    }

    public void log(String logMessage) {
        taskLogger.writeLog(logMessage);
    }

    public TaskLogger getTaskLogger() {
        return taskLogger;
    }

    public abstract String doExecute() throws Exception;

    public boolean cancel() {
        return true;
    }

    public String getInstanceCode() {
        if (jobInstance == null) {
            return null;
        }
        return jobInstance.getInstanceCode();
    }

    public JobInstance getJobInstance() {
        return jobInstance;
    }

    public String getJobCode() {
        if (jobInstance == null) {
            return null;
        }
        return jobInstance.getJobCode();
    }
}
