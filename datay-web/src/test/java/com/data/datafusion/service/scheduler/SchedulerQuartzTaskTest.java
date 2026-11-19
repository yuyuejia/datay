package com.data.datafusion.service.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.JobInstanceService;
import com.data.datafusion.util.SpringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;

class SchedulerQuartzTaskTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void recordsFailedInstanceWhenDispatchThrows() throws Exception {
        JobInstanceService jobInstanceService = mock(JobInstanceService.class);
        JobExecutionContext context = context();
        when(jobInstanceService.buildJobInstance("job-1")).thenThrow(new IllegalStateException("编排解析失败"));

        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class)) {
            springUtils.when(() -> SpringUtils.getBean(JobInstanceService.class)).thenReturn(jobInstanceService);

            new SchedulerQuartzTask().execute(context);
        }

        verify(jobInstanceService).recordDispatchFailure(eq("job-1"), isNull(), any(IllegalStateException.class));
        verify(jobInstanceService, never()).startInstance(any(JobInstance.class));
    }

    @Test
    void startsInstanceWhenDispatchSucceeds() throws Exception {
        JobInstanceService jobInstanceService = mock(JobInstanceService.class);
        JobExecutionContext context = context();
        JobInstance instance = new JobInstance();
        instance.setJobCode("job-1");
        instance.setType(TaskConstants.TASK_TYPE_SQL);
        instance.setTenantId("9");
        when(jobInstanceService.buildJobInstance("job-1")).thenReturn(instance);
        when(jobInstanceService.checkDependIsNullOrSuccess(instance)).thenReturn(true);

        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class)) {
            springUtils.when(() -> SpringUtils.getBean(JobInstanceService.class)).thenReturn(jobInstanceService);

            new SchedulerQuartzTask().execute(context);
        }

        verify(jobInstanceService).startInstance(instance);
        verify(jobInstanceService, never()).recordDispatchFailure(any(), any(), any());
    }

    private static JobExecutionContext context() {
        JobExecutionContext context = mock(JobExecutionContext.class);
        JobDetail jobDetail = mock(JobDetail.class);
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getKey()).thenReturn(JobKey.jobKey("job-1", QuartzService.GROUP_DATAFUSION));
        return context;
    }
}
