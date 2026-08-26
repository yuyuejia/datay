package com.data.datafusion.service;

import com.data.datafusion.service.jobevent.StartJobEventHandler;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 任务管理服务 - 提供任务执行和停止功能
 */
@Service
public class TaskManagementService {

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
