package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A DTO for the {@link com.data.datafusion.domain.DataQualityTask} entity.
 */
public class DataQualityTaskDTO implements Serializable {

    private String id;

    private String taskName;

    private String taskDesc;

    private String dataSourceId;

    private String schemaName;

    private String tableName;

    private String whereClause;

    private Boolean failOnWarn;

    private Integer sampleLimit;

    private String outputFormat;

    private String outputDir;

    private String jobId;

    private String status;

    private String lastStatus;

    private ZonedDateTime lastRunTime;

    private ZonedDateTime createTime;

    private ZonedDateTime updateTime;

    private String creater;

    private String tenantId;

    private List<DataQualityRuleBindingDTO> rules = new ArrayList<>();

    private DataQualityResultDTO lastResult;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getTaskDesc() {
        return taskDesc;
    }

    public void setTaskDesc(String taskDesc) {
        this.taskDesc = taskDesc;
    }

    public String getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getWhereClause() {
        return whereClause;
    }

    public void setWhereClause(String whereClause) {
        this.whereClause = whereClause;
    }

    public Boolean getFailOnWarn() {
        return failOnWarn;
    }

    public void setFailOnWarn(Boolean failOnWarn) {
        this.failOnWarn = failOnWarn;
    }

    public Integer getSampleLimit() {
        return sampleLimit;
    }

    public void setSampleLimit(Integer sampleLimit) {
        this.sampleLimit = sampleLimit;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
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

    public List<DataQualityRuleBindingDTO> getRules() {
        return rules;
    }

    public void setRules(List<DataQualityRuleBindingDTO> rules) {
        this.rules = rules == null ? new ArrayList<>() : rules;
    }

    public DataQualityResultDTO getLastResult() {
        return lastResult;
    }

    public void setLastResult(DataQualityResultDTO lastResult) {
        this.lastResult = lastResult;
    }
}
