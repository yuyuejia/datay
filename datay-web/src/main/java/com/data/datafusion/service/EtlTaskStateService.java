package com.data.datafusion.service;

import com.data.datafusion.domain.ETLNode;
import com.data.datafusion.domain.ETLTask;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.ETLNodeRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.status.StatusStorageStrategy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * ETL 任务状态管理服务。
 *
 * <p>状态以 jobCode（即 {@code ETLTask.jobId}）为维度存储，包含有状态组件写入的
 * 增量水位、binlog 位点等。通过 {@link StatusStorageConfigService} 解析存储后端：
 * 单机为 local 文件，master/worker 分离部署为 minio，从而支持跨节点查询、修改与删除。
 */
@Service
public class EtlTaskStateService {

    private static final Logger LOG = LoggerFactory.getLogger(EtlTaskStateService.class);

    private final ETLTaskRepository etlTaskRepository;

    private final ETLNodeRepository etlNodeRepository;

    private final StatusStorageConfigService statusStorageConfigService;

    private final JobInstanceService jobInstanceService;

    private final ConcurrentHashMap<String, Object> jobLocks = new ConcurrentHashMap<>();

    public EtlTaskStateService(
        ETLTaskRepository etlTaskRepository,
        ETLNodeRepository etlNodeRepository,
        StatusStorageConfigService statusStorageConfigService,
        JobInstanceService jobInstanceService
    ) {
        this.etlTaskRepository = etlTaskRepository;
        this.etlNodeRepository = etlNodeRepository;
        this.statusStorageConfigService = statusStorageConfigService;
        this.jobInstanceService = jobInstanceService;
    }

    /**
     * 获取任务的节点编码到节点名称（label）的映射，用于状态按节点分组展示。
     */
    public Map<String, String> getNodeNames(String taskId) {
        Map<String, String> names = new LinkedHashMap<>();
        List<ETLNode> nodes = etlNodeRepository.findAllByTaskId(String.valueOf(taskId)).orElse(Collections.emptyList());
        for (ETLNode node : nodes) {
            if (node.getCode() == null) {
                continue;
            }
            String label = node.getLabel();
            names.put(node.getCode(), (label == null || label.trim().isEmpty()) ? node.getCode() : label);
        }
        return names;
    }

    public StatusStorageConfigService.StatusStorageConfig getConfig() {
        return statusStorageConfigService.getConfig();
    }

    public StatusStorageStrategy updateConfig(StatusStorageConfigService.StatusStorageConfig config) {
        return statusStorageConfigService.updateAndReload(config);
    }

    public StatusStorageStrategy testStrategy() {
        return statusStorageConfigService.buildStrategy(statusStorageConfigService.getConfig());
    }

    public Optional<ETLTask> findTask(String taskId) {
        return etlTaskRepository.findById(taskId);
    }

    /**
     * 解析任务对应的状态存储 key（jobCode）。jobId 为空时返回 null。
     */
    public String resolveJobCode(ETLTask task) {
        return task.getJobId() == null ? null : String.valueOf(task.getJobId());
    }

    /**
     * 加载任务状态，不存在时返回空 Map。
     */
    public Map<String, Object> loadState(String taskId) {
        ETLTask task = requireTask(taskId);
        String jobCode = resolveJobCode(task);
        if (jobCode == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> state = statusStorageConfigService.getActiveStrategy().loadStatus(jobCode);
        return state == null ? new LinkedHashMap<>() : state;
    }

    /**
     * 按键合并（Patch）状态：值为 null 表示删除该 key，其余覆盖写入。
     *
     * @return 合并后的完整状态
     */
    public Map<String, Object> patchState(String taskId, Map<String, Object> patch) {
        ETLTask task = requireTask(taskId);
        String jobCode = requireJobCode(task);
        synchronized (lockOf(jobCode)) {
            StatusStorageStrategy strategy = statusStorageConfigService.getActiveStrategy();
            Map<String, Object> state = strategy.loadStatus(jobCode);
            Map<String, Object> merged = state == null ? new LinkedHashMap<>() : new LinkedHashMap<>(state);
            if (patch != null) {
                patch.forEach((key, value) -> {
                    if (value == null) {
                        merged.remove(key);
                    } else {
                        merged.put(key, value);
                    }
                });
            }
            strategy.saveStatus(merged, jobCode);
            return merged;
        }
    }

    /**
     * 删除状态：key 为空时删除整个任务状态，否则只删除指定 key。
     *
     * @return 删除后的状态（整任务删除时为空 Map）
     */
    public Map<String, Object> deleteState(String taskId, String key) {
        ETLTask task = requireTask(taskId);
        String jobCode = requireJobCode(task);
        StatusStorageStrategy strategy = statusStorageConfigService.getActiveStrategy();
        if (key == null || key.trim().isEmpty()) {
            strategy.deleteStatus(jobCode);
            return new LinkedHashMap<>();
        }
        synchronized (lockOf(jobCode)) {
            Map<String, Object> state = strategy.loadStatus(jobCode);
            if (state == null) {
                return new LinkedHashMap<>();
            }
            Map<String, Object> merged = new LinkedHashMap<>(state);
            merged.remove(key);
            strategy.saveStatus(merged, jobCode);
            return merged;
        }
    }

    /**
     * 列出所有存在状态的任务。
     */
    public List<StateSummary> listStates() {
        StatusStorageStrategy strategy = statusStorageConfigService.getActiveStrategy();
        List<StateSummary> summaries = new ArrayList<>();
        for (String jobCode : strategy.listJobCodes()) {
            StateSummary summary = new StateSummary();
            summary.setJobCode(jobCode);
            Map<String, Object> state = strategy.loadStatus(jobCode);
            summary.setKeys(state == null ? new ArrayList<>() : new ArrayList<>(state.keySet()));
            resolveTask(jobCode).ifPresent(task -> {
                summary.setTaskId(task.getId());
                summary.setTaskName(task.getTaskName());
                summary.setTaskCode(task.getTaskCode());
            });
            summaries.add(summary);
        }
        return summaries;
    }

    /**
     * 任务当前是否有运行中的实例。
     */
    public boolean isRunning(String taskId) {
        ETLTask task = requireTask(taskId);
        String jobCode = resolveJobCode(task);
        if (jobCode == null) {
            return false;
        }
        return !jobInstanceService.findByJobCodeAndStatus(jobCode, TaskConstants.TASK_STATUS_RUNNING).isEmpty();
    }

    private Optional<ETLTask> resolveTask(String jobCode) {
        try {
            return etlTaskRepository.findByJobId(jobCode);
        } catch (NumberFormatException e) {
            LOG.debug("状态 jobCode 非任务 ID：{}", jobCode);
            return Optional.empty();
        }
    }

    private ETLTask requireTask(String taskId) {
        return etlTaskRepository
            .findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("ETL 任务不存在: " + taskId));
    }

    private String requireJobCode(ETLTask task) {
        String jobCode = resolveJobCode(task);
        if (jobCode == null) {
            throw new IllegalStateException("ETL 任务尚未生成调度作业，暂无可管理的状态: " + task.getId());
        }
        return jobCode;
    }

    private Object lockOf(String jobCode) {
        return jobLocks.computeIfAbsent(jobCode, k -> new Object());
    }

    public static class StateSummary {

        private String taskId;
        private String taskName;
        private String taskCode;
        private String jobCode;
        private List<String> keys = new ArrayList<>();

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getTaskName() {
            return taskName;
        }

        public void setTaskName(String taskName) {
            this.taskName = taskName;
        }

        public String getTaskCode() {
            return taskCode;
        }

        public void setTaskCode(String taskCode) {
            this.taskCode = taskCode;
        }

        public String getJobCode() {
            return jobCode;
        }

        public void setJobCode(String jobCode) {
            this.jobCode = jobCode;
        }

        public List<String> getKeys() {
            return keys;
        }

        public void setKeys(List<String> keys) {
            this.keys = keys;
        }
    }
}
