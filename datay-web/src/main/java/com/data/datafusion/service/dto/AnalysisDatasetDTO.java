package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A DTO for the {@link com.data.datafusion.domain.AnalysisDataset} entity.
 *
 * <p>{@link #definition} 为与类型相关的定义 JSON 字符串；{@link #fields} 为最近一次预览的列信息 JSON。
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AnalysisDatasetDTO implements Serializable {

    private String id;
    private String tenantId;
    private String name;
    private String code;
    private String description;
    private String type;
    private String dataSourceId;
    private String definition;
    private String fields;
    private String status;
    /** 是否启用结果持久化。 */
    private Boolean persistEnabled;
    /** 持久化目标数据源 id。 */
    private String persistTargetDataSourceId;
    /** 持久化目标 schema。 */
    private String persistTargetSchema;
    /** 持久化目标表。 */
    private String persistTargetTable;
    /** 持久化写入模式：REPLACE / APPEND。 */
    private String persistWriteMode;
    /** 持久化任务 cron；为空表示仅手动执行。 */
    private String persistCron;
    /** 持久化结果状态。 */
    private String persistStatus;
    /** 最近一次触发时间。 */
    private java.time.ZonedDateTime persistLastRunTime;
    /** 最近一次成功完成时间。 */
    private java.time.ZonedDateTime persistLastSuccessTime;
    /** 最近一次失败原因。 */
    private String persistLastError;
    /** 产出结果对应的数据集定义摘要。 */
    private String persistDefinitionHash;
    /** 生成的数据集任务 id。 */
    private String persistEtlTaskId;
    /** 生成的数据集调度 Job id。 */
    private String persistJobId;
    /** 最近一次成功产出的行数。 */
    private Long persistRowCount;
    /** 已发布的数据服务 id，仅用于列表展示，不落库。 */
    private String publishedApiId;
    /** 已发布的数据服务编码，仅用于列表展示，不落库。 */
    private String publishedApiCode;
    /** 已发布的数据服务状态，仅用于列表展示，不落库。 */
    private String publishedApiStatus;
    private ZonedDateTime createTime;
    private ZonedDateTime updateTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public String getFields() {
        return fields;
    }

    public void setFields(String fields) {
        this.fields = fields;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPublishedApiId() {
        return publishedApiId;
    }

    public void setPublishedApiId(String publishedApiId) {
        this.publishedApiId = publishedApiId;
    }

    public String getPublishedApiCode() {
        return publishedApiCode;
    }

    public void setPublishedApiCode(String publishedApiCode) {
        this.publishedApiCode = publishedApiCode;
    }

    public String getPublishedApiStatus() {
        return publishedApiStatus;
    }

    public void setPublishedApiStatus(String publishedApiStatus) {
        this.publishedApiStatus = publishedApiStatus;
    }


    public Boolean getPersistEnabled() {
        return persistEnabled;
    }

    public void setPersistEnabled(Boolean persistEnabled) {
        this.persistEnabled = persistEnabled;
    }

    public String getPersistTargetDataSourceId() {
        return persistTargetDataSourceId;
    }

    public void setPersistTargetDataSourceId(String persistTargetDataSourceId) {
        this.persistTargetDataSourceId = persistTargetDataSourceId;
    }

    public String getPersistTargetSchema() {
        return persistTargetSchema;
    }

    public void setPersistTargetSchema(String persistTargetSchema) {
        this.persistTargetSchema = persistTargetSchema;
    }

    public String getPersistTargetTable() {
        return persistTargetTable;
    }

    public void setPersistTargetTable(String persistTargetTable) {
        this.persistTargetTable = persistTargetTable;
    }

    public String getPersistWriteMode() {
        return persistWriteMode;
    }

    public void setPersistWriteMode(String persistWriteMode) {
        this.persistWriteMode = persistWriteMode;
    }

    public String getPersistCron() {
        return persistCron;
    }

    public void setPersistCron(String persistCron) {
        this.persistCron = persistCron;
    }

    public String getPersistStatus() {
        return persistStatus;
    }

    public void setPersistStatus(String persistStatus) {
        this.persistStatus = persistStatus;
    }

    public java.time.ZonedDateTime getPersistLastRunTime() {
        return persistLastRunTime;
    }

    public void setPersistLastRunTime(java.time.ZonedDateTime persistLastRunTime) {
        this.persistLastRunTime = persistLastRunTime;
    }

    public java.time.ZonedDateTime getPersistLastSuccessTime() {
        return persistLastSuccessTime;
    }

    public void setPersistLastSuccessTime(java.time.ZonedDateTime persistLastSuccessTime) {
        this.persistLastSuccessTime = persistLastSuccessTime;
    }

    public String getPersistLastError() {
        return persistLastError;
    }

    public void setPersistLastError(String persistLastError) {
        this.persistLastError = persistLastError;
    }

    public String getPersistDefinitionHash() {
        return persistDefinitionHash;
    }

    public void setPersistDefinitionHash(String persistDefinitionHash) {
        this.persistDefinitionHash = persistDefinitionHash;
    }

    public String getPersistEtlTaskId() {
        return persistEtlTaskId;
    }

    public void setPersistEtlTaskId(String persistEtlTaskId) {
        this.persistEtlTaskId = persistEtlTaskId;
    }

    public String getPersistJobId() {
        return persistJobId;
    }

    public void setPersistJobId(String persistJobId) {
        this.persistJobId = persistJobId;
    }

    public Long getPersistRowCount() {
        return persistRowCount;
    }

    public void setPersistRowCount(Long persistRowCount) {
        this.persistRowCount = persistRowCount;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AnalysisDatasetDTO)) {
            return false;
        }
        return getId() != null && getId().equals(((AnalysisDatasetDTO) o).getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String toString() {
        return (
            "AnalysisDatasetDTO{" +
            "id=" +
            getId() +
            ", name='" +
            getName() +
            "', code='" +
            getCode() +
            "', type='" +
            getType() +
            "', status='" +
            getStatus() +
            "'}"
        );
    }
}
