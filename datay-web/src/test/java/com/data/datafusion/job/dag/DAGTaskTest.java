package com.data.datafusion.job.dag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.service.jobevent.EventServiceFactory;
import com.data.datafusion.service.jobevent.IEventService;
import com.data.datafusion.service.jobevent.JobStatusEvent;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class DAGTaskTest {

    @Test
    void interruptRemainingTasksMarksOnlyUnexecutedSubTasks() throws Exception {
        JobInstance finished = new JobInstance();
        finished.setJobCode("1");
        finished.setStatus(TaskConstants.TASK_STATUS_SUCCESSFUL);

        JobInstance failed = new JobInstance();
        failed.setJobCode("2");
        failed.setStatus(TaskConstants.TASK_STATUS_FAILED);

        JobInstance pending1 = new JobInstance();
        pending1.setJobCode("3");
        pending1.setStatus(TaskConstants.TASK_STATUS_RUNNING);

        JobInstance pending2 = new JobInstance();
        pending2.setJobCode("4");
        pending2.setStatus(TaskConstants.TASK_STATUS_RUNNING);

        List<JobInstance> instances = Arrays.asList(finished, failed, pending1, pending2);

        try (MockedStatic<EventServiceFactory> mocked = Mockito.mockStatic(EventServiceFactory.class)) {
            IEventService eventService = Mockito.mock(IEventService.class);
            mocked.when(EventServiceFactory::getEventService).thenReturn(eventService);

            DAGTask dagTask = new DAGTask(null);
            Method method = DAGTask.class.getDeclaredMethod("interruptRemainingTasks", List.class, int.class);
            method.setAccessible(true);
            method.invoke(dagTask, instances, 2);

            assertThat(finished.getStatus()).isEqualTo(TaskConstants.TASK_STATUS_SUCCESSFUL);
            assertThat(failed.getStatus()).isEqualTo(TaskConstants.TASK_STATUS_FAILED);
            assertThat(pending1.getStatus()).isEqualTo(TaskConstants.TASK_STATUS_INTERRUPTED);
            assertThat(pending2.getStatus()).isEqualTo(TaskConstants.TASK_STATUS_INTERRUPTED);
            assertThat(pending1.getEndTime()).isNotNull();
            assertThat(pending2.getEndTime()).isNotNull();

            ArgumentCaptor<JobStatusEvent> captor = ArgumentCaptor.forClass(JobStatusEvent.class);
            verify(eventService, times(2)).pushJobStatusEvent(captor.capture());
            assertThat(captor.getAllValues())
                .extracting(JobStatusEvent::getStatus)
                .containsOnly(TaskConstants.TASK_STATUS_INTERRUPTED);
            assertThat(captor.getAllValues())
                .extracting(event -> event.getJobInstance().getJobCode())
                .containsExactly("3", "4");
        }
    }
}
