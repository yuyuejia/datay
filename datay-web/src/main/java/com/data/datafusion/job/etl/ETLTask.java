package com.data.datafusion.job.etl;

import com.data.datafusion.config.SpringUtil;
import com.data.datafusion.config.StatusStorageConfig;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.AbstractTask;
import com.data.datafusion.service.StatusStorageConfigService;
import com.data.job.ETLFlowTask;
import com.data.job.TaskInstance;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ETLTask extends AbstractTask {

    private static final Logger LOG = LoggerFactory.getLogger(ETLTask.class);

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private volatile ETLFlowTask etlFlowTask;

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
        ETLFlowTask flowTask = new ETLFlowTask(taskInstance, getTaskLogger());
        this.etlFlowTask = flowTask;
        applyStatusStorageStrategy(flowTask);
        if (cancelled.get()) {
            flowTask.cancel();
            throw new InterruptedException("ETL task was cancelled before execution");
        }
        try {
            flowTask.runJob(taskInstance.getJobContext());
            return "";
        } finally {
            this.etlFlowTask = null;
        }
    }

    @Override
    public boolean cancel() {
        cancelled.set(true);
        ETLFlowTask flowTask = this.etlFlowTask;
        if (flowTask != null) {
            flowTask.cancel();
        }
        return true;
    }

    /**
     * 根据状态存储配置注入策略：优先使用 master 随任务事件下发的配置，
     * 旧事件没有该配置时回退到共享数据库读取。
     */
    private void applyStatusStorageStrategy(ETLFlowTask etlFlowTask) {
        StatusStorageConfig config = getJobInstance() == null ? null : getJobInstance().getStatusStorageConfig();
        if (config != null) {
            try {
                StatusStorageConfigService configService = SpringUtil.getBean(StatusStorageConfigService.class);
                LOG.debug("使用任务事件下发的状态存储配置: type={}", config.getType());
                etlFlowTask.setStatusStorageStrategy(configService.getStrategy(config));
                return;
            } catch (Exception e) {
                // 配置明确下发了 minio 时不能静默回退 local，否则状态会写到节点本地，跨节点不可见。
                throw new IllegalStateException("初始化任务状态存储失败: " + e.getMessage(), e);
            }
        }
        try {
            StatusStorageConfigService configService = SpringUtil.getBean(StatusStorageConfigService.class);
            etlFlowTask.setStatusStorageStrategy(configService.getActiveStrategy());
        } catch (Exception e) {
            LOG.warn("加载状态存储配置失败，使用默认策略: {}", e.getMessage());
        }
    }
}
