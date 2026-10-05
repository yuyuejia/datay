package com.data.datafusion.service.scheduler;

import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.JobInstanceService;
import com.data.datafusion.util.SpringUtils;
import java.util.List;
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
            // 调度线程无请求上下文，按等待任务所属租户设置租户上下文，避免租户过滤器拦截依赖检查。
            Long tenantId = parseTenantId(jobInstance.getTenantId());
            if (tenantId != null) {
                TenantContext.setTenantId(tenantId);
            }
            try {
                if (jobInstanceService.checkDependIsNullOrSuccess(jobInstance)) {
                    log.info("waiting jobInstance {} is ready to start", jobInstance);
                    jobInstanceService.startInstance(jobInstance);
                }
            } finally {
                TenantContext.clear();
            }
        }
    }

    private static Long parseTenantId(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(tenantId.trim());
        } catch (NumberFormatException e) {
            log.warn("任务租户 id 非法，忽略租户上下文: {}", tenantId);
            return null;
        }
    }
}
