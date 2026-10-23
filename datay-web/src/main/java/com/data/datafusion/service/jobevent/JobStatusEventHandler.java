package com.data.datafusion.service.jobevent;

import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.service.JobInstanceService;
import com.data.datafusion.service.alert.AlertEventService;
import com.data.datafusion.util.SpringUtils;
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
                    // 事件中的 JobInstance 会被执行线程复用并继续修改，状态必须以事件快照为准，
                    // 否则 RUNNING 事件可能被读成 FAILED，导致告警重复累计。
                    String eventStatus = event.getStatus() == null ? event.getJobInstance().getStatus() : event.getStatus();
                    latestJobInstance.setStatus(eventStatus);
                    latestJobInstance.setStartTime(event.getJobInstance().getStartTime());
                    latestJobInstance.setEndTime(event.getJobInstance().getEndTime());
                }
                // 失败日志由执行节点写入事件，需要同步落库，供告警内容使用。
                if (event.getJobInstance().getJobMessage() != null && !event.getJobInstance().getJobMessage().isBlank()) {
                    latestJobInstance.setJobMessage(event.getJobInstance().getJobMessage());
                }
                jobInstanceService.save(latestJobInstance);
                try {
                    SpringUtils.getBean(AlertEventService.class).onJobInstanceStatus(latestJobInstance);
                } catch (Exception e) {
                    // 告警是旁路能力，任何异常都不能影响任务状态事件循环。
                    log.error("Failed to submit alert event for instance {}", latestJobInstance.getInstanceCode(), e);
                }
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
