package com.data.datafusion.job;

import com.data.datafusion.config.EtlExecutionProperties;
import com.data.datafusion.config.SpringUtil;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.dag.DAGTask;
import com.data.datafusion.job.etl.DockerEtlTask;
import com.data.datafusion.job.etl.ETLTask;
import com.data.datafusion.job.shell.ShellTask;
import com.data.datafusion.job.sql.SqlTask;

public class TaskFactory {

    public static ITask getTaskProcessor(JobInstance jobInstance) {
        if (TaskConstants.TASK_TYPE_DEMO.equals(jobInstance.getType())) {
            return new DemoTask(jobInstance);
        } else if (TaskConstants.TASK_TYPE_SHELL.equals(jobInstance.getType())) {
            return new ShellTask(jobInstance);
        } else if (TaskConstants.TASK_TYPE_DAG.equals(jobInstance.getType())) {
            return new DAGTask(jobInstance);
        } else if (TaskConstants.TASK_TYPE_SQL.equals(jobInstance.getType())) {
            return new SqlTask(jobInstance);
        } else if (TaskConstants.TASK_TYPE_ETL.equals(jobInstance.getType())) {
            if (isDockerEtlMode()) {
                return new DockerEtlTask(jobInstance);
            }
            return new ETLTask(jobInstance);
        }
        return new DemoTask(jobInstance);
    }

    /**
     * ETL 是否配置为 docker 执行模式。无法获取配置（如单元测试未启动 Spring 上下文）时退回进程内执行。
     */
    private static boolean isDockerEtlMode() {
        try {
            EtlExecutionProperties properties = SpringUtil.getBean(EtlExecutionProperties.class);
            return properties != null && properties.isDockerMode();
        } catch (Exception e) {
            return false;
        }
    }
}
