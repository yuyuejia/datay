package com.data.datafusion.service.jobevent;

import com.data.datafusion.domain.JobDepend;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.service.JobDependService;
import com.data.datafusion.service.JobInstanceService;
import com.data.datafusion.util.SpringUtils;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobStatusEventHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(JobStatusEventHandler.class);

    @Override
    public void run() {
        while (true) {
            JobStatusEvent event = EventServiceFactory.getEventService().takeJobStatusEvent();
            JobInstanceService jobInstanceService = SpringUtils.getBean(JobInstanceService.class);
            //            jobInstanceService.save(event.getJobInstance());
            //重新从数据库获取最新的JobInstance，避免使用过时的对象
            JobInstance latestJobInstance = jobInstanceService.findOneJobInstanceByInstanceCode(event.getJobInstance().getInstanceCode());
            if (latestJobInstance != null) {
                if (event.getStatus().equals(TaskConstants.TASK_STATUS_INTERRUPTED)) {
                    latestJobInstance.setStatus(event.getStatus());
                    latestJobInstance.setEndTime(String.valueOf(event.getEndTime()));
                } else {
                    latestJobInstance.setExecNode(event.getJobInstance().getExecNode());
                    latestJobInstance.setStatus(event.getJobInstance().getStatus());
                    latestJobInstance.setStartTime(event.getJobInstance().getStartTime());
                    latestJobInstance.setEndTime(event.getJobInstance().getEndTime());
                }
                jobInstanceService.save(latestJobInstance);
            }

            log.info(
                "######TaskStatusEvent: Code:{},Type:{},status:{}",
                event.getJobInstance().getJobCode(),
                event.getJobInstance().getType(),
                event.getJobInstance().getStatus()
            );
            //如果是dag里面的子任务，不去触发弱依赖的任务调度
            //                if(true == event.getJobInstance().getSubJob()){
            //                    continue;
            //                }

        }
    }
}
