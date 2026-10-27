package com.data.datafusion.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class ExecutionPropertiesTest {

    @Test
    void workerDefaultsAreApplied() {
        WorkerExecutionProperties properties = new WorkerExecutionProperties();
        assertThat(properties.getThreadCount()).isEqualTo(WorkerExecutionProperties.DEFAULT_THREAD_COUNT);
        assertThat(properties.getQueueCapacity()).isEqualTo(WorkerExecutionProperties.DEFAULT_QUEUE_CAPACITY);
    }

    @Test
    void workerConfiguredValuesAreKept() {
        WorkerExecutionProperties properties = new WorkerExecutionProperties();
        properties.setThreadCount(200);
        properties.setQueueCapacity(2000);
        properties.normalize();
        assertThat(properties.getThreadCount()).isEqualTo(200);
        assertThat(properties.getQueueCapacity()).isEqualTo(2000);
    }

    @Test
    void workerInvalidValuesFallBackToDefaults() {
        WorkerExecutionProperties properties = new WorkerExecutionProperties();
        properties.setThreadCount(0);
        properties.setQueueCapacity(-1);
        properties.normalize();
        assertThat(properties.getThreadCount()).isEqualTo(WorkerExecutionProperties.DEFAULT_THREAD_COUNT);
        assertThat(properties.getQueueCapacity()).isEqualTo(WorkerExecutionProperties.DEFAULT_QUEUE_CAPACITY);
    }

    @Test
    void schedulerDefaultsAreApplied() {
        SchedulerExecutionProperties properties = new SchedulerExecutionProperties();
        assertThat(properties.getThreadCount()).isEqualTo(SchedulerExecutionProperties.DEFAULT_THREAD_COUNT);
    }

    @Test
    void schedulerConfiguredValueIsKept() {
        SchedulerExecutionProperties properties = new SchedulerExecutionProperties();
        properties.setThreadCount(30);
        properties.normalize();
        assertThat(properties.getThreadCount()).isEqualTo(30);
    }

    @Test
    void schedulerInvalidValueFallsBackToDefault() {
        SchedulerExecutionProperties properties = new SchedulerExecutionProperties();
        properties.setThreadCount(-1);
        properties.normalize();
        assertThat(properties.getThreadCount()).isEqualTo(SchedulerExecutionProperties.DEFAULT_THREAD_COUNT);
    }

    @Test
    void workerPropertiesBindFromEnvironment() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("development.worker.thread-count", "200")
            .withProperty("development.worker.queue-capacity", "2000");

        WorkerExecutionProperties properties = Binder.get(environment)
            .bind("development.worker", Bindable.of(WorkerExecutionProperties.class))
            .orElseGet(WorkerExecutionProperties::new);

        assertThat(properties.getThreadCount()).isEqualTo(200);
        assertThat(properties.getQueueCapacity()).isEqualTo(2000);
    }

    @Test
    void schedulerPropertiesBindFromEnvironment() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("development.scheduler.thread-count", "30");

        SchedulerExecutionProperties properties = Binder.get(environment)
            .bind("development.scheduler", Bindable.of(SchedulerExecutionProperties.class))
            .orElseGet(SchedulerExecutionProperties::new);

        assertThat(properties.getThreadCount()).isEqualTo(30);
    }
}
