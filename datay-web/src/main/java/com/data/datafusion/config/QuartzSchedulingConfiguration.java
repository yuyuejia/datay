package com.data.datafusion.config;

import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.quartz.QuartzProperties;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 将 {@code development.scheduler.thread-count} 应用到 Quartz 线程池。
 *
 * <p>Spring 的 {@code SchedulerFactoryBean} 会把 Quartz 线程数默认设为 10；这里通过
 * {@link SchedulerFactoryBeanCustomizer} 覆盖该默认值，同时保留用户在
 * {@code spring.quartz.properties} 中的显式配置（显式配置优先）。</p>
 */
@Configuration
public class QuartzSchedulingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(QuartzSchedulingConfiguration.class);

    /** Quartz 线程数配置项。 */
    public static final String QUARTZ_THREAD_COUNT = "org.quartz.threadPool.threadCount";

    @Bean
    public SchedulerFactoryBeanCustomizer quartzThreadPoolCustomizer(
        SchedulerExecutionProperties schedulerProperties,
        ObjectProvider<QuartzProperties> quartzPropertiesProvider
    ) {
        return schedulerFactoryBean -> {
            QuartzProperties bootProperties = quartzPropertiesProvider.getIfAvailable();
            Properties properties = new Properties();
            if (bootProperties != null) {
                properties.putAll(bootProperties.getProperties());
            }

            boolean explicitlyConfigured = properties.containsKey(QUARTZ_THREAD_COUNT);
            if (!explicitlyConfigured) {
                properties.setProperty(QUARTZ_THREAD_COUNT, String.valueOf(schedulerProperties.getThreadCount()));
            }
            schedulerFactoryBean.setQuartzProperties(properties);

            log.info(
                "Quartz 调度线程数={}（来源：{}）",
                properties.getProperty(QUARTZ_THREAD_COUNT),
                explicitlyConfigured ? "spring.quartz.properties" : "development.scheduler.thread-count"
            );
        };
    }
}
