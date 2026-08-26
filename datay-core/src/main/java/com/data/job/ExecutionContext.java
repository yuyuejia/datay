package com.data.job;

import com.data.status.StatusStorageStrategy;

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
        Map<String, Object> currentStatus = getStatus();
        if (currentStatus.isEmpty()) {
            return true;
        }
        return statusStorageStrategy.saveStatus(currentStatus, getJobCode());
    }

    public void loadStatus() {
        Map<String, Object> savedStatus = statusStorageStrategy.loadStatus(getJobCode());
        if (savedStatus != null) {
            // 将恢复的状态设置到ExecutionContext中
            setStatus(savedStatus);
        }
    }

    public void setStatusStorageStrategy(StatusStorageStrategy statusStorageStrategy) {
        this.statusStorageStrategy = statusStorageStrategy;
    }
}
