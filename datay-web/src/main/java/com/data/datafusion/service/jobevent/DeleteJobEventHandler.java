package com.data.datafusion.service.jobevent;

import com.data.datafusion.domain.Job;
import com.data.datafusion.service.scheduler.QuartzService;
import com.data.datafusion.util.SpringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteJobEventHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(DeleteJobEventHandler.class);

    @Override
    public void run() {
        while (true) {
            Job job = EventServiceFactory.getEventService().takeDeleteJobEvent();
            log.info("删除任务事件接收id {}", job.getId());
            QuartzService quartzService = SpringUtils.getBean(QuartzService.class);
            quartzService.deleteJob(job.getId().toString(), QuartzService.GROUP_DATAFUSION);
        }
    }
}
