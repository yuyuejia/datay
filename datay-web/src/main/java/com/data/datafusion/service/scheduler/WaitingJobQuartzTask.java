package com.data.datafusion.service.scheduler;

import com.data.datafusion.domain.JobDepend;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.service.JobDependService;
import com.data.datafusion.service.JobInstanceService;
import com.data.datafusion.util.SpringUtils;
import java.time.ZonedDateTime;
import java.util.List;
import org.hibernate.sql.model.ast.builder.TableDeleteBuilderSkipped;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 等待任务处理执行类
 */
public class WaitingJobQuartzTask implements Job {

    private static final Logger log = LoggerFactory.getLogger(WaitingJobQuartzTask.class);

    public static final String TASK_NAME = "waitingJobQuartzTask";
    //每隔5检查一次等待任务
    public static final String CRON = "0 * * * * ?";

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobInstanceService jobInstanceService = SpringUtils.getBean(JobInstanceService.class);
        List<JobInstance> waitingJobList = jobInstanceService.findByStatus(TaskConstants.TASK_STATUS_WAITING);
        for (JobInstance jobInstance : waitingJobList) {
            if (jobInstanceService.checkDependIsNullOrSuccess(jobInstance)) {
                log.info("waiting jobInstance {} is ready to start", jobInstance);
                jobInstanceService.startInstance(jobInstance);
            }
        }
    }
}
