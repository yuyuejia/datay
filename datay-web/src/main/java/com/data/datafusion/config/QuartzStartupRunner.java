package com.data.datafusion.config;

import com.data.datafusion.service.scheduler.QuartzService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class QuartzStartupRunner implements CommandLineRunner {

    private final Logger log = LoggerFactory.getLogger(QuartzStartupRunner.class);

    @Autowired
    QuartzService quartzService;

    @Override
    public void run(String... args) throws Exception {
        String property = SpringUtil.getEnvironment().getProperty("development.mode");
        //        String property = System.getProperty("development.mode");
        if (!"cluster".equals(property)) {
            log.info("########################使用Standalone 模式");
            quartzService.initJobScheduler();
        } else {
            log.info("########################使用Cluster 模式");
        }
    }
}
