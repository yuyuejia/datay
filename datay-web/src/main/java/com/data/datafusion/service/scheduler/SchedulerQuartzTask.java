package com.data.datafusion.service.scheduler;

import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.security.TenantContext;
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
        JobInstance jobInstance = jobInstanceService.buildJobInstance(context.getJobDetail().getKey().getName());
        jobInstance.setCreateTime(ZonedDateTime.now());
        // 调度线程无请求上下文，需按任务所属租户设置租户上下文，否则租户过滤器会以 dummy 租户拦截。
        Long tenantId = parseTenantId(jobInstance.getTenantId());
        if (tenantId != null) {
            TenantContext.setTenantId(tenantId);
        }
        try {
            if (jobInstanceService.checkDependIsNullOrSuccess(jobInstance)) {
                jobInstanceService.startInstance(jobInstance);
            } else {
                jobInstanceService.waitingInstance(jobInstance);
            }
        } finally {
            TenantContext.clear();
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
