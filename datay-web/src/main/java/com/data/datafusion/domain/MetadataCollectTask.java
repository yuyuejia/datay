package com.data.datafusion.domain;

import com.data.datafusion.util.entity.UuidV7Id;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import org.hibernate.annotations.Filter;

/**
 * 数据源元数据采集配置。
 *
 * <p>每个数据源最多一条配置。定时执行时生成 {@code MetadataCollect} ETL 任务，
 * 将表与字段元数据写入文件存储。</p>
 */
@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "dp_metadata_collect_task")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class MetadataCollectTask implements Serializable, TenantAware {

    private static final long serialVersionUID = 1L;

    @Id
    @UuidV7Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "data_source_id", length = 36, nullable = false)
    private String dataSourceId;

    @Column(name = "task_name", nullable = false)
    private String taskName;

    @Column(name = "schema_names")
    private String schemaNamesJson;

    @Column(name = "cron", length = 100)
    private String cron;

    @Column(name = "enabled")
    private Boolean enabled = Boolean.FALSE;

    @Column(name = "output_dir", length = 500)
    private String outputDir = "metadata";

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

    public String getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getSchemaNamesJson() {
        return schemaNamesJson;
    }

    public void setSchemaNamesJson(String schemaNamesJson) {
        this.schemaNamesJson = schemaNamesJson;
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
}
