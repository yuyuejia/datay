package com.data.datafusion.config;

import org.springframework.beans.BeansException;
import org.springframework.context.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class SpringUtil implements ApplicationContextAware, ApplicationEventPublisherAware {

    private static ApplicationContext applicationContext;
    private static ApplicationEventPublisher applicationEventPublisher;
    private static Environment environment = null;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (SpringUtil.applicationContext == null) {
            SpringUtil.applicationContext = applicationContext;
            environment = applicationContext.getEnvironment();
        }
    }

    //获取applicationContext
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    //通过name获取 Bean.
    public static Object getBean(String name) {
        return getApplicationContext().getBean(name);
    }

    //通过class获取Bean.
    public static <T> T getBean(Class<T> clazz) {
        return getApplicationContext().getBean(clazz);
    }

    //通过name,以及Clazz返回指定的Bean
    public static <T> T getBean(String name, Class<T> clazz) {
        return getApplicationContext().getBean(name, clazz);
    }

    public static <T> String[] getBeanNamesForType(Class<T> clazz) {
        return getApplicationContext().getBeanNamesForType(clazz);
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        if (SpringUtil.applicationEventPublisher == null) {
            SpringUtil.applicationEventPublisher = applicationEventPublisher;
        }
    }

    public static void publishEvent(ApplicationEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    public static Environment getEnvironment() {
        return environment;
    }

    public static String getString(String key) {
        return environment.getProperty(key);
    }

    public static boolean includeProfile(String profile) {
        String[] profiles = environment.getActiveProfiles();
        for (String p : profiles) {
            if (profile.equals(p)) {
                return true;
            }
        }
        return false;
    }
}
