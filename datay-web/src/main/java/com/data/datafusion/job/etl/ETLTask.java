package com.data.datafusion.job.etl;

import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.AbstractTask;
import com.data.job.ETLFlowTask;
import com.data.job.TaskInstance;

public class ETLTask extends AbstractTask {

    public ETLTask(JobInstance jobInstance) {
        super(jobInstance);
    }

    @Override
    public String doExecute() throws Exception {
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setId(getJobInstance().getId());
        taskInstance.setJobCode(getJobInstance().getJobCode());
        taskInstance.setInstanceCode(getJobInstance().getInstanceCode());
        taskInstance.setJobContext(getJobInstance().getJobContext());
        taskInstance.setType(getJobInstance().getType());
        taskInstance.setParentInstanceCode(getJobInstance().getParentInstanceCode());
        ETLFlowTask etlFlowTask = new ETLFlowTask(taskInstance, getTaskLogger());
        etlFlowTask.runJob(taskInstance.getJobContext());
        return "";
    }
}
