package com.data.datafusion.service.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.springframework.test.util.ReflectionTestUtils;

class QuartzServiceTest {

    @Test
    void addJobReplacesExistingTriggerInsteadOfFailingSilently() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        QuartzService service = new QuartzService();
        ReflectionTestUtils.setField(service, "scheduler", scheduler);

        service.addJob(SchedulerQuartzTask.class, "job-1", QuartzService.GROUP_DATAFUSION, "0 30 1 * * ?", null);

        verify(scheduler).scheduleJob(any(JobDetail.class), anySet(), eq(true));
    }

    @Test
    void addJobWithInvalidCronDoesNotBreakSchedulerInitialization() {
        Scheduler scheduler = mock(Scheduler.class);
        QuartzService service = new QuartzService();
        ReflectionTestUtils.setField(service, "scheduler", scheduler);

        assertThatCode(() ->
            service.addJob(SchedulerQuartzTask.class, "job-1", QuartzService.GROUP_DATAFUSION, "invalid-cron", null)
        ).doesNotThrowAnyException();
    }
}
