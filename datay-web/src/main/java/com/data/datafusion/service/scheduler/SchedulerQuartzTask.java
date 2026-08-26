package com.data.datafusion.service.scheduler;

import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.service.JobInstanceService;
import com.data.datafusion.util.SpringUtils;
import java.time.ZonedDateTime;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//基础类
public class SchedulerQuartzTask implements Job {

    private static final Logger log = LoggerFactory.getLogger(SchedulerQuartzTask.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobInstanceService jobInstanceService = SpringUtils.getBean(JobInstanceService.class);
        JobInstance jobInstance = jobInstanceService.buildJobInstance(Long.valueOf(context.getJobDetail().getKey().getName()));
        jobInstance.setCreateTime(ZonedDateTime.now());
        if (jobInstanceService.checkDependIsNullOrSuccess(jobInstance)) {
            jobInstanceService.startInstance(jobInstance);
        } else {
            jobInstanceService.waitingInstance(jobInstance);
        }
    }
}
