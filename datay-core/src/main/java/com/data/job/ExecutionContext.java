package com.data.job;

import com.data.status.StatusStorageStrategy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class ExecutionContext {

    private String jobCode;
    private String jobInstanceCode;

    private StatusStorageStrategy statusStorageStrategy;
    //先进先出堵塞队列
    private Map<String, LinkedBlockingQueue<Object>> connections = new ConcurrentHashMap<>();

    private Map<String, Object> status = new ConcurrentHashMap<>();

    // ===== 调试模式相关 =====
    /** 是否处于调试模式 */
    private boolean debugMode = false;
    /** 调试模式下每个源组件采样的最大行数 */
    private int debugRowLimit = 100;
    /** 调试运行的目标节点（为空表示整图运行） */
    private String debugTargetNodeId;
    /** 各组件输出的采样结果，key 为组件 id */
    private final Map<String, DebugResult> debugResults = new ConcurrentHashMap<>();

    public void put(String key, Object value) {
        this.status.put(key, value);
    }

    public Object get(String key) {
        return this.status.get(key);
    }

    public Map<String, LinkedBlockingQueue<Object>> getConnections() {
        return connections;
    }

    public Map<String, Object> getStatus() {
        return status;
    }

    public void setStatus(Map<String, Object> status) {
        this.status = status;
    }

    public Map<String, Object> getStatusMap() {
        return status;
    }

    public void setConnections(Map<String, LinkedBlockingQueue<Object>> connections) {
        this.connections = connections;
    }

    public String getJobInstanceCode() {
        return jobInstanceCode;
    }

    public void setJobInstanceCode(String jobInstanceCode) {
        this.jobInstanceCode = jobInstanceCode;
    }

    public String getJobCode() {
        return jobCode;
    }

    public void setJobCode(String jobCode) {
        this.jobCode = jobCode;
    }

    public boolean saveStatus() {
        // 调试模式下不持久化增量状态，避免污染正式运行的水位
        if (debugMode) {
            return true;
        }
        Map<String, Object> currentStatus = getStatus();
        if (currentStatus.isEmpty()) {
            return true;
        }
        return statusStorageStrategy.saveStatus(currentStatus, getJobCode());
    }

    public void loadStatus() {
        // 调试模式下不加载历史增量状态，始终按初次的样本量读取
        if (debugMode) {
            return;
        }
        Map<String, Object> savedStatus = statusStorageStrategy.loadStatus(getJobCode());
        if (savedStatus != null) {
            // 将恢复的状态设置到ExecutionContext中
            setStatus(savedStatus);
        }
    }

    public void setStatusStorageStrategy(StatusStorageStrategy statusStorageStrategy) {
        this.statusStorageStrategy = statusStorageStrategy;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public int getDebugRowLimit() {
        return debugRowLimit <= 0 ? 100 : debugRowLimit;
    }

    public void setDebugRowLimit(int debugRowLimit) {
        this.debugRowLimit = debugRowLimit;
    }

    public String getDebugTargetNodeId() {
        return debugTargetNodeId;
    }

    public void setDebugTargetNodeId(String debugTargetNodeId) {
        this.debugTargetNodeId = debugTargetNodeId;
    }

    public Map<String, DebugResult> getDebugResults() {
        return debugResults;
    }

    /**
     * 记录组件的输出采样数据（流式路径）。
     */
    public void captureDebugOutput(String nodeId, FlowFile flowFile) {
        if (!debugMode || nodeId == null) {
            return;
        }
        DebugResult result = debugResults.computeIfAbsent(nodeId, key -> new DebugResult());
        result.append(flowFile, getDebugRowLimit());
        // 达到采样上限即视为截断，前端据此提示用户
        if (result.getRows() != null && result.getRows().size() >= getDebugRowLimit()) {
            result.setTruncated(true);
        }
    }

    /**
     * 记录组件的输出采样数据（DuckDB 物化路径：当前节点执行后 DuckDB 中存在的所有表）。
     */
    public void captureDebugOutputTables(String nodeId, List<DebugResult.DebugTable> tables) {
        if (!debugMode || nodeId == null) {
            return;
        }
        DebugResult result = debugResults.computeIfAbsent(nodeId, key -> new DebugResult());
        result.setTablesIfEmpty(tables);
    }

    /**
     * 判断组件是否已经采集到输出数据。
     */
    public boolean hasDebugOutput(String nodeId) {
        DebugResult result = debugResults.get(nodeId);
        if (result == null) {
            return false;
        }
        boolean hasRows = result.getRows() != null && !result.getRows().isEmpty();
        boolean hasTables = result.getTables() != null && !result.getTables().isEmpty();
        return hasRows || hasTables;
    }
}
