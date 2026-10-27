package com.data.datafusion.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.quartz.QuartzProperties;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

class QuartzSchedulingConfigurationTest {

    @Test
    void appliesConfiguredThreadCountWhenNotExplicitlySet() throws Exception {
        SchedulerExecutionProperties schedulerProperties = new SchedulerExecutionProperties();
        schedulerProperties.setThreadCount(30);

        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        customizer(schedulerProperties, null).customize(factory);

        assertThat(quartzProperties(factory).getProperty("org.quartz.threadPool.threadCount")).isEqualTo("30");
    }

    @Test
    void explicitSpringQuartzPropertyTakesPrecedence() throws Exception {
        SchedulerExecutionProperties schedulerProperties = new SchedulerExecutionProperties();
        schedulerProperties.setThreadCount(30);

        QuartzProperties bootProperties = new QuartzProperties();
        bootProperties.getProperties().put("org.quartz.threadPool.threadCount", "20");

        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        customizer(schedulerProperties, bootProperties).customize(factory);

        assertThat(quartzProperties(factory).getProperty("org.quartz.threadPool.threadCount")).isEqualTo("20");
    }

    private SchedulerFactoryBeanCustomizer customizer(SchedulerExecutionProperties schedulerProperties, QuartzProperties boot) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        if (boot != null) {
            beanFactory.addBean("quartzProperties", boot);
        }
        ObjectProvider<QuartzProperties> provider = beanFactory.getBeanProvider(QuartzProperties.class);
        return new QuartzSchedulingConfiguration().quartzThreadPoolCustomizer(schedulerProperties, provider);
    }

    private Properties quartzProperties(SchedulerFactoryBean factory) throws Exception {
        Field field = SchedulerFactoryBean.class.getDeclaredField("quartzProperties");
        field.setAccessible(true);
        return (Properties) field.get(factory);
    }
}
