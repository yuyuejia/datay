package com.data.datafusion.job.dag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.alibaba.fastjson2.JSON;
import com.data.datafusion.config.SpringUtil;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.ITask;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.job.TaskExecutionSupport;
import com.data.datafusion.job.TaskFactory;
import com.data.datafusion.service.JobInstanceService;
import com.data.datafusion.service.jobevent.EventServiceFactory;
import com.data.datafusion.service.jobevent.IEventService;
import com.data.datafusion.service.jobevent.JobStatusEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;

class DAGTaskTest {

    @Test
    void timeoutMarksCurrentAndRemainingSubTasksInterrupted() throws Exception {
        JobInstance child = new JobInstance();
        child.setInstanceCode("child-instance");
        child.setJobCode("child-job");
        child.setType(TaskConstants.TASK_TYPE_SHELL);
        child.setStatus(TaskConstants.TASK_STATUS_WAITING);

        JobInstance remainingChild = new JobInstance();
        remainingChild.setInstanceCode("child-instance-2");
        remainingChild.setJobCode("child-job-2");
        remainingChild.setType(TaskConstants.TASK_TYPE_SHELL);
        remainingChild.setStatus(TaskConstants.TASK_STATUS_WAITING);

        FlowInstance flow = new FlowInstance();
        flow.setJobInstances(new LinkedHashSet<>(Arrays.asList(child, remainingChild)));
        flow.setJobDepends(Collections.emptyList());

        JobInstance parent = new JobInstance();
        parent.setInstanceCode("dag-timeout-parent");
        parent.setJobCode("dag-timeout-job");
        parent.setType(TaskConstants.TASK_TYPE_DAG);
        parent.setRetryCount(0);
        parent.setTimeoutSeconds(1);
        parent.setJobContext(JSON.toJSONString(flow));

        CountDownLatch childStarted = new CountDownLatch(1);
        CountDownLatch releaseChild = new CountDownLatch(1);
        ITask slowChild = new ITask() {
            @Override
            public String execute() {
                childStarted.countDown();
                try {
                    releaseChild.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return TaskConstants.TASK_STATUS_INTERRUPTED;
            }

            @Override
            public boolean cancel() {
                releaseChild.countDown();
                return true;
            }
        };

        try (
            MockedStatic<EventServiceFactory> eventFactory = Mockito.mockStatic(EventServiceFactory.class);
            MockedStatic<TaskFactory> taskFactory = Mockito.mockStatic(TaskFactory.class);
            MockedStatic<SpringUtil> springUtil = Mockito.mockStatic(SpringUtil.class)
        ) {
            IEventService eventService = mock(IEventService.class);
            eventFactory.when(EventServiceFactory::getEventService).thenReturn(eventService);
            Environment environment = mock(Environment.class);
            springUtil.when(SpringUtil::getEnvironment).thenReturn(environment);

            AtomicReference<JobInstance> executedChild = new AtomicReference<>();
            taskFactory
                .when(() -> TaskFactory.getTaskProcessor(any(JobInstance.class)))
                .thenAnswer(invocation -> {
                    executedChild.set(invocation.getArgument(0));
                    return slowChild;
                });

            DAGTask dagTask = new DAGTask(parent);
            String status = TaskExecutionSupport.execute(parent, () -> dagTask, task -> {}, () -> false);

            assertThat(childStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(status).isEqualTo(TaskConstants.TASK_STATUS_TIMEOUT);
            assertThat(executedChild.get()).isNotNull();
            assertThat(executedChild.get().getStatus()).isEqualTo(TaskConstants.TASK_STATUS_INTERRUPTED);

            ArgumentCaptor<JobStatusEvent> captor = ArgumentCaptor.forClass(JobStatusEvent.class);
            verify(eventService, times(3)).pushJobStatusEvent(captor.capture());
            assertThat(captor.getAllValues())
                .extracting(JobStatusEvent::getStatus)
                .containsExactly(
                    TaskConstants.TASK_STATUS_RUNNING,
                    TaskConstants.TASK_STATUS_INTERRUPTED,
                    TaskConstants.TASK_STATUS_INTERRUPTED
                );
            assertThat(captor.getAllValues())
                .extracting(event -> event.getJobInstance().getJobCode())
                .containsExactly("child-job", "child-job", "child-job-2");
        }
    }

    @Test
    void retryExecutesAllSubTasksAgain() throws Exception {
        JobInstance firstChild = new JobInstance();
        firstChild.setInstanceCode("retry-child-instance-1");
        firstChild.setJobCode("retry-child-job-1");
        firstChild.setType(TaskConstants.TASK_TYPE_SHELL);
        firstChild.setStatus(TaskConstants.TASK_STATUS_WAITING);

        JobInstance secondChild = new JobInstance();
        secondChild.setInstanceCode("retry-child-instance-2");
        secondChild.setJobCode("retry-child-job-2");
        secondChild.setType(TaskConstants.TASK_TYPE_SHELL);
        secondChild.setStatus(TaskConstants.TASK_STATUS_WAITING);

        FlowInstance flow = new FlowInstance();
        flow.setJobInstances(new LinkedHashSet<>(Arrays.asList(firstChild, secondChild)));
        flow.setJobDepends(Collections.emptyList());

        JobInstance parent = new JobInstance();
        parent.setInstanceCode("dag-retry-parent");
        parent.setJobCode("dag-retry-job");
        parent.setType(TaskConstants.TASK_TYPE_DAG);
        parent.setRetryCount(1);
        parent.setTimeoutSeconds(0);
        parent.setJobContext(JSON.toJSONString(flow));

        Map<String, AtomicInteger> executions = new ConcurrentHashMap<>();
        try (
            MockedStatic<EventServiceFactory> eventFactory = Mockito.mockStatic(EventServiceFactory.class);
            MockedStatic<TaskFactory> taskFactory = Mockito.mockStatic(TaskFactory.class);
            MockedStatic<SpringUtil> springUtil = Mockito.mockStatic(SpringUtil.class)
        ) {
            IEventService eventService = mock(IEventService.class);
            eventFactory.when(EventServiceFactory::getEventService).thenReturn(eventService);
            Environment environment = mock(Environment.class);
            springUtil.when(SpringUtil::getEnvironment).thenReturn(environment);
            JobInstanceService jobInstanceService = mock(JobInstanceService.class);
            springUtil.when(() -> SpringUtil.getBean(JobInstanceService.class)).thenReturn(jobInstanceService);

            taskFactory
                .when(() -> TaskFactory.getTaskProcessor(any(JobInstance.class)))
                .thenAnswer(invocation -> {
                    JobInstance child = invocation.getArgument(0);
                    int attempt = executions
                        .computeIfAbsent(child.getJobCode(), code -> new AtomicInteger())
                        .incrementAndGet();
                    // 首次执行时第二个子任务失败，第二次尝试时所有子任务均应重新执行。
                    if ("retry-child-job-2".equals(child.getJobCode()) && attempt == 1) {
                        return fixedTask(TaskConstants.TASK_STATUS_FAILED);
                    }
                    return fixedTask(TaskConstants.TASK_STATUS_SUCCESSFUL);
                });

            setRetryIntervalMillis(1L);
            try {
                String status = TaskExecutionSupport.execute(parent, () -> new DAGTask(parent), task -> {}, () -> false);

                assertThat(status).isEqualTo(TaskConstants.TASK_STATUS_SUCCESSFUL);
                assertThat(executions.get("retry-child-job-1").get()).isEqualTo(2);
                assertThat(executions.get("retry-child-job-2").get()).isEqualTo(2);
                // 重试过程不得查询数据库获取子任务状态
                verifyNoInteractions(jobInstanceService);
            } finally {
                setRetryIntervalMillis(10_000L);
            }
        }
    }

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

    private void setRetryIntervalMillis(long millis) throws Exception {
        Field field = TaskExecutionSupport.class.getDeclaredField("retryIntervalMillis");
        field.setAccessible(true);
        field.setLong(null, millis);
    }

    private static ITask fixedTask(String status) {
        return new ITask() {
            @Override
            public String execute() {
                return status;
            }

            @Override
            public boolean cancel() {
                return true;
            }
        };
    }
}
