package com.data.datafusion.job;

import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.dag.DAGTask;
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
            return new ETLTask(jobInstance);
        }
        return new DemoTask(jobInstance);
    }
}
