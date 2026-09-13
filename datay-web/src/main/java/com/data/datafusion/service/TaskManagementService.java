package com.data.datafusion.service;

import com.data.datafusion.config.DeploymentProperties;
import com.data.datafusion.service.jobevent.StartJobEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 任务管理服务 - 提供任务执行和停止功能。
 *
 * <p>任务停止依赖本节点正在执行的任务（worker 职责），master 独立部署时该节点不持有任务实例，
 * 此时停止请求返回 false，由调度侧/前端选择实际持有任务的 worker 节点处理。</p>
 */
@Service
public class TaskManagementService {

    private static final Logger log = LoggerFactory.getLogger(TaskManagementService.class);

    public TaskManagementService() {}

    /**
     * 停止指定任务
     * @param instanceCode 任务实例代码
     * @return 是否成功停止
     */
    public boolean stopTask(String instanceCode) {
        return StartJobEventHandler.stopTask(instanceCode);
    }

    /**
     * 关闭任务管理服务
     */
    public void shutdown() {
        //        startJobEventHandler.shutdown();
    }
}
