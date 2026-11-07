package com.data.datafusion.domain;

import com.data.datafusion.util.entity.UuidV7Id;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import org.hibernate.annotations.Filter;

/**
 * 数据质量检核任务定义。
 *
 * <p>单表任务：一个任务绑定一个数据源、Schema、表和一组规则。执行时生成
 * {@code StreamJdbcInput -> DataQualityCheck} 的 ETL 任务，结果写入文件存储。</p>
 */
@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "dp_data_quality_task")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DataQualityTask implements Serializable, TenantAware {

    private static final long serialVersionUID = 1L;

    @Id
    @UuidV7Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "task_name", nullable = false)
    private String taskName;

    @Column(name = "task_desc", length = 2000)
    private String taskDesc;

    @Column(name = "data_source_id", length = 36, nullable = false)
    private String dataSourceId;

    @Column(name = "schema_name")
    private String schemaName;

    @Column(name = "table_name", nullable = false)
    private String tableName;

    @Column(name = "where_clause")
    private String whereClause;

    @Column(name = "rule_configs")
    private String ruleConfigs;

    @Column(name = "fail_on_warn")
    private Boolean failOnWarn = Boolean.FALSE;

    @Column(name = "sample_limit")
    private Integer sampleLimit = 100;

    @Column(name = "output_format", length = 20)
    private String outputFormat = "CSV";

    @Column(name = "output_dir", length = 500)
    private String outputDir = "data-quality";

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

    public String getRuleConfigs() {
        return ruleConfigs;
    }

    public void setRuleConfigs(String ruleConfigs) {
        this.ruleConfigs = ruleConfigs;
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
        if (!(o instanceof DataQualityTask)) {
            return false;
        }
        return getId() != null && getId().equals(((DataQualityTask) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "DataQualityTask{" +
            "id=" + getId() +
            ", taskName='" + getTaskName() + "'" +
            ", dataSourceId='" + getDataSourceId() + "'" +
            ", tableName='" + getTableName() + "'" +
            ", jobId='" + getJobId() + "'" +
            ", status='" + getStatus() + "'" +
            ", tenantId='" + getTenantId() + "'" +
            "}";
    }
}
