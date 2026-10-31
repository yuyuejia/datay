package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 数据比对结果视图 DTO。由 JobInstance、JobContext 中的文件元数据和 summary 文件实时组装。
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DataCompareResultDTO implements Serializable {

    private String id;

    private String taskId;

    private String taskName;

    private String jobId;

    private String jobInstanceId;

    private String instanceCode;

    private String status;

    private ZonedDateTime startTime;

    private ZonedDateTime endTime;

    private Long leftRowCount;

    private Long rightRowCount;

    private Long sameRowCount;

    private Long diffRowCount;

    private Long onlyLeftCount;

    private Long onlyRightCount;

    private Long valueDiffCount;

    private String outputDir;

    private String resultPath;

    private String summaryPath;

    private String resultFormat;

    private String jobMessage;

    private ZonedDateTime createTime;

    private String tenantId;

    private String downloadUrl;

    private Boolean compareRecordCount;

    private Boolean compareDetail;

    private List<DataCompareTableResultDTO> tables = new ArrayList<>();

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

    public Long getLeftRowCount() {
        return leftRowCount;
    }

    public void setLeftRowCount(Long leftRowCount) {
        this.leftRowCount = leftRowCount;
    }

    public Long getRightRowCount() {
        return rightRowCount;
    }

    public void setRightRowCount(Long rightRowCount) {
        this.rightRowCount = rightRowCount;
    }

    public Long getSameRowCount() {
        return sameRowCount;
    }

    public void setSameRowCount(Long sameRowCount) {
        this.sameRowCount = sameRowCount;
    }

    public Long getDiffRowCount() {
        return diffRowCount;
    }

    public void setDiffRowCount(Long diffRowCount) {
        this.diffRowCount = diffRowCount;
    }

    public Long getOnlyLeftCount() {
        return onlyLeftCount;
    }

    public void setOnlyLeftCount(Long onlyLeftCount) {
        this.onlyLeftCount = onlyLeftCount;
    }

    public Long getOnlyRightCount() {
        return onlyRightCount;
    }

    public void setOnlyRightCount(Long onlyRightCount) {
        this.onlyRightCount = onlyRightCount;
    }

    public Long getValueDiffCount() {
        return valueDiffCount;
    }

    public void setValueDiffCount(Long valueDiffCount) {
        this.valueDiffCount = valueDiffCount;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public String getResultPath() {
        return resultPath;
    }

    public void setResultPath(String resultPath) {
        this.resultPath = resultPath;
    }

    public String getSummaryPath() {
        return summaryPath;
    }

    public void setSummaryPath(String summaryPath) {
        this.summaryPath = summaryPath;
    }

    public String getResultFormat() {
        return resultFormat;
    }

    public void setResultFormat(String resultFormat) {
        this.resultFormat = resultFormat;
    }

    public String getJobMessage() {
        return jobMessage;
    }

    public void setJobMessage(String jobMessage) {
        this.jobMessage = jobMessage;
    }

    public ZonedDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public Boolean getCompareRecordCount() {
        return compareRecordCount;
    }

    public void setCompareRecordCount(Boolean compareRecordCount) {
        this.compareRecordCount = compareRecordCount;
    }

    public Boolean getCompareDetail() {
        return compareDetail;
    }

    public void setCompareDetail(Boolean compareDetail) {
        this.compareDetail = compareDetail;
    }

    public List<DataCompareTableResultDTO> getTables() {
        return tables;
    }

    public void setTables(List<DataCompareTableResultDTO> tables) {
        this.tables = tables;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataCompareResultDTO)) {
            return false;
        }
        return this.id != null && Objects.equals(this.id, ((DataCompareResultDTO) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }
}
