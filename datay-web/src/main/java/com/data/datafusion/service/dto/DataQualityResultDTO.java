package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据质量任务/组件执行结果。
 */
public class DataQualityResultDTO implements Serializable {

    private String id;

    private String taskId;

    private String taskName;

    private String jobId;

    private String jobInstanceId;

    private String instanceCode;

    private String status;

    private String overallStatus;

    private ZonedDateTime startTime;

    private ZonedDateTime endTime;

    private ZonedDateTime createTime;

    private String jobMessage;

    private String tenantId;

    private String dataSourceId;

    private String schemaName;

    private String tableName;

    private String outputDir;

    private String resultFormat;

    private String summaryPath;

    private String downloadUrl;

    private Integer totalRules;

    private Integer passRules;

    private Integer warnRules;

    private Integer failRules;

    private Long checkedRows;

    private List<DataQualityRuleResultDTO> rules = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getJobInstanceId() {
        return jobInstanceId;
    }

    public void setJobInstanceId(String jobInstanceId) {
        this.jobInstanceId = jobInstanceId;
    }

    public String getInstanceCode() {
        return instanceCode;
    }

    public void setInstanceCode(String instanceCode) {
        this.instanceCode = instanceCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(String overallStatus) {
        this.overallStatus = overallStatus;
    }

    public ZonedDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(ZonedDateTime startTime) {
        this.startTime = startTime;
    }

    public ZonedDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(ZonedDateTime endTime) {
        this.endTime = endTime;
    }

    public ZonedDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public String getJobMessage() {
        return jobMessage;
    }

    public void setJobMessage(String jobMessage) {
        this.jobMessage = jobMessage;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
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

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public String getResultFormat() {
        return resultFormat;
    }

    public void setResultFormat(String resultFormat) {
        this.resultFormat = resultFormat;
    }

    public String getSummaryPath() {
        return summaryPath;
    }

    public void setSummaryPath(String summaryPath) {
        this.summaryPath = summaryPath;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public Integer getTotalRules() {
        return totalRules;
    }

    public void setTotalRules(Integer totalRules) {
        this.totalRules = totalRules;
    }

    public Integer getPassRules() {
        return passRules;
    }

    public void setPassRules(Integer passRules) {
        this.passRules = passRules;
    }

    public Integer getWarnRules() {
        return warnRules;
    }

    public void setWarnRules(Integer warnRules) {
        this.warnRules = warnRules;
    }

    public Integer getFailRules() {
        return failRules;
    }

    public void setFailRules(Integer failRules) {
        this.failRules = failRules;
    }

    public Long getCheckedRows() {
        return checkedRows;
    }

    public void setCheckedRows(Long checkedRows) {
        this.checkedRows = checkedRows;
    }

    public List<DataQualityRuleResultDTO> getRules() {
        return rules;
    }

    public void setRules(List<DataQualityRuleResultDTO> rules) {
        this.rules = rules == null ? new ArrayList<>() : rules;
    }
}
