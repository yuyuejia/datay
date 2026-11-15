package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A DTO for the {@link com.data.datafusion.domain.MetadataCollectTask} entity.
 */
public class MetadataCollectTaskDTO implements Serializable {

    private String id;

    private String dataSourceId;

    private String dataSourceName;

    private String taskName;

    private List<String> schemaNames = new ArrayList<>();

    private String cron;

    private Boolean enabled;

    private String jobId;

    private String status;

    private String lastStatus;

    private ZonedDateTime lastRunTime;

    private ZonedDateTime createTime;

    private ZonedDateTime updateTime;

    private String creater;

    private String tenantId;

    private MetadataCollectRunDTO lastRun;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public List<String> getSchemaNames() {
        return schemaNames;
    }

    public void setSchemaNames(List<String> schemaNames) {
        this.schemaNames = schemaNames;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }

    public ZonedDateTime getLastRunTime() {
        return lastRunTime;
    }

    public void setLastRunTime(ZonedDateTime lastRunTime) {
        this.lastRunTime = lastRunTime;
    }

    public ZonedDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public ZonedDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(ZonedDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public String getCreater() {
        return creater;
    }

    public void setCreater(String creater) {
        this.creater = creater;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public MetadataCollectRunDTO getLastRun() {
        return lastRun;
    }

    public void setLastRun(MetadataCollectRunDTO lastRun) {
        this.lastRun = lastRun;
    }
}
