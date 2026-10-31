package com.data.datafusion.domain;

import com.data.datafusion.util.entity.UuidV7Id;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import org.hibernate.annotations.Filter;

/**
 * 数据比对任务定义。
 *
 * <p>任务通过两侧数据源、表、过滤条件和主键字段定义一次比对；执行时由 ETL 引擎生成
 * 输入、DuckDB 物化、SQL 比对和文件导出节点，结果写入文件存储。</p>
 */
@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "dp_data_compare_task")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DataCompareTask implements Serializable, TenantAware {

    private static final long serialVersionUID = 1L;

    @Id
    @UuidV7Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "task_name", nullable = false)
    private String taskName;

    @Column(name = "task_desc", length = 2000)
    private String taskDesc;

    @Column(name = "left_data_source_id", length = 36, nullable = false)
    private String leftDataSourceId;

    @Column(name = "left_schema")
    private String leftSchema;

    @Column(name = "left_table", nullable = false)
    private String leftTable;

    @Column(name = "left_where")
    private String leftWhere;

    @Column(name = "right_data_source_id", length = 36, nullable = false)
    private String rightDataSourceId;

    @Column(name = "right_schema")
    private String rightSchema;

    @Column(name = "right_table", nullable = false)
    private String rightTable;

    @Column(name = "right_where")
    private String rightWhere;

    @Column(name = "key_columns", length = 2000, nullable = false)
    private String keyColumns;

    @Column(name = "compare_columns")
    private String compareColumns;

    /** 多表比对配置，JSON 数组；为空时兼容旧版单表字段。 */
    @Column(name = "table_configs")
    private String tableConfigs;

    @Column(name = "compare_record_count")
    private Boolean compareRecordCount = Boolean.TRUE;

    @Column(name = "compare_detail")
    private Boolean compareDetail = Boolean.FALSE;

    @Column(name = "ignore_case")
    private Boolean ignoreCase = Boolean.FALSE;

    @Column(name = "trim_whitespace")
    private Boolean trimWhitespace = Boolean.FALSE;

    @Column(name = "output_format", length = 20)
    private String outputFormat = "CSV";

    @Column(name = "output_dir", length = 500)
    private String outputDir = "data-compare";

    @Column(name = "job_id", length = 36)
    private String jobId;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "last_status", length = 50)
    private String lastStatus;

    @Column(name = "last_run_time")
    private ZonedDateTime lastRunTime;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "update_time")
    private ZonedDateTime updateTime;

    @Column(name = "creater")
    private String creater;

    @Column(name = "tenant_id")
    private String tenantId;

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

    public String getTableConfigs() {
        return tableConfigs;
    }

    public void setTableConfigs(String tableConfigs) {
        this.tableConfigs = tableConfigs;
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

    @Override
    public String getTenantId() {
        return tenantId;
    }

    @Override
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataCompareTask)) {
            return false;
        }
        return getId() != null && getId().equals(((DataCompareTask) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "DataCompareTask{" +
            "id=" + getId() +
            ", taskName='" + getTaskName() + "'" +
            ", leftDataSourceId='" + getLeftDataSourceId() + "'" +
            ", leftTable='" + getLeftTable() + "'" +
            ", rightDataSourceId='" + getRightDataSourceId() + "'" +
            ", rightTable='" + getRightTable() + "'" +
            ", keyColumns='" + getKeyColumns() + "'" +
            ", jobId='" + getJobId() + "'" +
            ", status='" + getStatus() + "'" +
            ", tenantId='" + getTenantId() + "'" +
            "}";
    }
}
