package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A DTO for the {@link com.data.datafusion.domain.DataCompareTask} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DataCompareTaskDTO implements Serializable {

    private String id;

    private String taskName;

    private String taskDesc;

    private String leftDataSourceId;

    private String leftSchema;

    private String leftTable;

    private String leftWhere;

    private String rightDataSourceId;

    private String rightSchema;

    private String rightTable;

    private String rightWhere;

    private String keyColumns;

    private String compareColumns;

    private Boolean compareRecordCount;

    private Boolean compareDetail;

    private Boolean ignoreCase;

    private Boolean trimWhitespace;

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

    private List<DataCompareTableDTO> tables = new ArrayList<>();

    private DataCompareResultDTO lastResult;

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

    public String getLeftDataSourceId() {
        return leftDataSourceId;
    }

    public void setLeftDataSourceId(String leftDataSourceId) {
        this.leftDataSourceId = leftDataSourceId;
    }

    public String getLeftSchema() {
        return leftSchema;
    }

    public void setLeftSchema(String leftSchema) {
        this.leftSchema = leftSchema;
    }

    public String getLeftTable() {
        return leftTable;
    }

    public void setLeftTable(String leftTable) {
        this.leftTable = leftTable;
    }

    public String getLeftWhere() {
        return leftWhere;
    }

    public void setLeftWhere(String leftWhere) {
        this.leftWhere = leftWhere;
    }

    public String getRightDataSourceId() {
        return rightDataSourceId;
    }

    public void setRightDataSourceId(String rightDataSourceId) {
        this.rightDataSourceId = rightDataSourceId;
    }

    public String getRightSchema() {
        return rightSchema;
    }

    public void setRightSchema(String rightSchema) {
        this.rightSchema = rightSchema;
    }

    public String getRightTable() {
        return rightTable;
    }

    public void setRightTable(String rightTable) {
        this.rightTable = rightTable;
    }

    public String getRightWhere() {
        return rightWhere;
    }

    public void setRightWhere(String rightWhere) {
        this.rightWhere = rightWhere;
    }

    public String getKeyColumns() {
        return keyColumns;
    }

    public void setKeyColumns(String keyColumns) {
        this.keyColumns = keyColumns;
    }

    public String getCompareColumns() {
        return compareColumns;
    }

    public void setCompareColumns(String compareColumns) {
        this.compareColumns = compareColumns;
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

    public Boolean getIgnoreCase() {
        return ignoreCase;
    }

    public void setIgnoreCase(Boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }

    public Boolean getTrimWhitespace() {
        return trimWhitespace;
    }

    public void setTrimWhitespace(Boolean trimWhitespace) {
        this.trimWhitespace = trimWhitespace;
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

    public List<DataCompareTableDTO> getTables() {
        return tables;
    }

    public void setTables(List<DataCompareTableDTO> tables) {
        this.tables = tables;
    }

    public DataCompareResultDTO getLastResult() {
        return lastResult;
    }

    public void setLastResult(DataCompareResultDTO lastResult) {
        this.lastResult = lastResult;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataCompareTaskDTO)) {
            return false;
        }
        return this.id != null && Objects.equals(this.id, ((DataCompareTaskDTO) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }
}
