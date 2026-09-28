package com.data.datafusion.job.etl;

import com.data.datafusion.config.SpringUtil;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.AbstractTask;
import com.data.datafusion.service.StatusStorageConfigService;
import com.data.job.ETLFlowTask;
import com.data.job.TaskInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ETLTask extends AbstractTask {

    private static final Logger LOG = LoggerFactory.getLogger(ETLTask.class);

    public ETLTask(JobInstance jobInstance) {
        super(jobInstance);
    }

    @Override
    public String doExecute() throws Exception {
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setId(getJobInstance().getId());
        taskInstance.setJobCode(getJobInstance().getJobCode());
        taskInstance.setInstanceCode(getJobInstance().getInstanceCode());
        taskInstance.setJobContext(getJobInstance().getJobContext());
        taskInstance.setType(getJobInstance().getType());
        taskInstance.setParentInstanceCode(getJobInstance().getParentInstanceCode());
        ETLFlowTask etlFlowTask = new ETLFlowTask(taskInstance, getTaskLogger());
        applyStatusStorageStrategy(etlFlowTask);
        etlFlowTask.runJob(taskInstance.getJobContext());
        return "";
    }

    /**
     * 根据状态存储配置注入策略：单机 local，master/worker 分离部署 minio，
     * 保证增量状态在节点间共享，并可从 master 端查询/修改/删除。
     */
    private void applyStatusStorageStrategy(ETLFlowTask etlFlowTask) {
        try {
            StatusStorageConfigService configService = SpringUtil.getBean(StatusStorageConfigService.class);
            etlFlowTask.setStatusStorageStrategy(configService.getActiveStrategy());
        } catch (Exception e) {
            LOG.warn("加载状态存储配置失败，使用默认策略: {}", e.getMessage());
        }
    }
}
