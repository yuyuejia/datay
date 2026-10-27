package com.data.datafusion.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 验证 development.scheduler.thread-count 能真正作用到 Quartz 线程池。
 */
class QuartzThreadPoolIntegrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(QuartzAutoConfiguration.class))
        .withUserConfiguration(TestConfiguration.class)
        .withPropertyValues("development.scheduler.thread-count=25");

    @Test
    void quartzThreadPoolUsesConfiguredSize() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(Scheduler.class);
            assertThat(context.getBean(Scheduler.class).getMetaData().getThreadPoolSize()).isEqualTo(25);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SchedulerExecutionProperties.class)
    @Import(QuartzSchedulingConfiguration.class)
    static class TestConfiguration {}
}
