package com.data.datafusion.service.jobevent;

import com.data.datafusion.domain.Job;
import com.data.datafusion.service.scheduler.QuartzService;
import com.data.datafusion.service.scheduler.SchedulerQuartzTask;
import com.data.datafusion.util.SpringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddJobEventHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(AddJobEventHandler.class);

    @Override
    public void run() {
        while (true) {
            Job job = EventServiceFactory.getEventService().takeAddJobEvent();
            QuartzService quartzService = SpringUtils.getBean(QuartzService.class);
            quartzService.addJob(SchedulerQuartzTask.class, job.getId().toString(), QuartzService.GROUP_DATAFUSION, job.getCron(), null);
        }
    }
}
