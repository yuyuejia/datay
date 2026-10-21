package com.data.datafusion.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.domain.JobInstance;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class TaskExecutionSupportTest {

    @BeforeEach
    void setUp() {
        TaskExecutionSupport.retryIntervalMillis = 1L;
    }

    @AfterEach
    void tearDown() {
        TaskExecutionSupport.retryIntervalMillis = 10_000L;
    }

    @Test
    void shouldReturnSuccessWithoutRetry() {
        FakeTask task = new FakeTask(0, 0, null);

        String status = TaskExecutionSupport.execute(jobInstance(3, 0), () -> task, null, () -> false);

        assertThat(status).isEqualTo(TaskConstants.TASK_STATUS_SUCCESSFUL);
        assertThat(task.getCalls()).isEqualTo(1);
    }

    @Test
    void shouldRetryFailedTaskUntilSuccess() {
        AtomicInteger created = new AtomicInteger();
        FakeTask[] tasks = new FakeTask[] { new FakeTask(1, 0, null), new FakeTask(1, 0, null), new FakeTask(0, 0, null) };

        String status = TaskExecutionSupport.execute(jobInstance(3, 0), () -> tasks[created.getAndIncrement()], null, () -> false);

        assertThat(status).isEqualTo(TaskConstants.TASK_STATUS_SUCCESSFUL);
        assertThat(created.get()).isEqualTo(3);
        assertThat(tasks[2].getCalls()).isEqualTo(1);
    }

    @Test
    void shouldNotRetryWhenRetryCountIsZero() {
        FakeTask task = new FakeTask(1, 0, null);

        String status = TaskExecutionSupport.execute(jobInstance(0, 0), () -> task, null, () -> false);

        assertThat(status).isEqualTo(TaskConstants.TASK_STATUS_FAILED);
        assertThat(task.getCalls()).isEqualTo(1);
    }

    @Test
    void shouldNotRetryAfterManualStop() {
        AtomicBoolean stopRequested = new AtomicBoolean(false);
        FakeTask task = new FakeTask(1, 0, stopRequested);

        String status = TaskExecutionSupport.execute(jobInstance(3, 0), () -> task, null, stopRequested::get);

        assertThat(status).isEqualTo(TaskConstants.TASK_STATUS_INTERRUPTED);
        assertThat(task.getCalls()).isEqualTo(1);
    }

    @Test
    @Timeout(10)
    void shouldCancelAndReturnTimeout() {
        FakeTask task = new FakeTask(0, 5_000, null);

        String status = TaskExecutionSupport.execute(jobInstance(0, 1), () -> task, null, () -> false);

        assertThat(status).isEqualTo(TaskConstants.TASK_STATUS_TIMEOUT);
        assertThat(task.getCalls()).isEqualTo(1);
        assertThat(task.getCancels()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Timeout(15)
    void shouldRetryAfterTimeout() {
        AtomicInteger created = new AtomicInteger();
        FakeTask slowTask = new FakeTask(0, 5_000, null);
        FakeTask fastTask = new FakeTask(0, 0, null);

        String status = TaskExecutionSupport.execute(
            jobInstance(1, 1),
            () -> created.getAndIncrement() == 0 ? slowTask : fastTask,
            null,
            () -> false
        );

        assertThat(status).isEqualTo(TaskConstants.TASK_STATUS_SUCCESSFUL);
        assertThat(created.get()).isEqualTo(2);
        assertThat(slowTask.getCancels()).isGreaterThanOrEqualTo(1);
        assertThat(fastTask.getCalls()).isEqualTo(1);
    }

    @Test
    @Timeout(10)
    void zeroTimeoutShouldNotLimitExecution() {
        FakeTask task = new FakeTask(0, 50, null);

        String status = TaskExecutionSupport.execute(jobInstance(0, 0), () -> task, null, () -> false);

        assertThat(status).isEqualTo(TaskConstants.TASK_STATUS_SUCCESSFUL);
        assertThat(task.getCalls()).isEqualTo(1);
    }

    private JobInstance jobInstance(int retryCount, int timeoutSeconds) {
        JobInstance jobInstance = new JobInstance();
        jobInstance.setInstanceCode("test-instance");
        jobInstance.setRetryCount(retryCount);
        jobInstance.setTimeoutSeconds(timeoutSeconds);
        return jobInstance;
    }

    private static class FakeTask implements ITask {

        private final AtomicInteger calls = new AtomicInteger();

        private final AtomicInteger cancels = new AtomicInteger();

        private final int failuresBeforeSuccess;

        private final long sleepMillis;

        private final AtomicBoolean stopAfterFailure;

        FakeTask(int failuresBeforeSuccess, long sleepMillis, AtomicBoolean stopAfterFailure) {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
            this.sleepMillis = sleepMillis;
            this.stopAfterFailure = stopAfterFailure;
        }

        @Override
        public String execute() {
            int call = calls.incrementAndGet();
            if (sleepMillis > 0) {
                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return TaskConstants.TASK_STATUS_INTERRUPTED;
                }
            }
            if (call <= failuresBeforeSuccess) {
                if (stopAfterFailure != null) {
                    stopAfterFailure.set(true);
                }
                return TaskConstants.TASK_STATUS_FAILED;
            }
            return TaskConstants.TASK_STATUS_SUCCESSFUL;
        }

        @Override
        public boolean cancel() {
            cancels.incrementAndGet();
            return true;
        }

        int getCalls() {
            return calls.get();
        }

        int getCancels() {
            return cancels.get();
        }
    }
}
