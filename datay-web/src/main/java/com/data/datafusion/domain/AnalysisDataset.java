package com.data.datafusion.domain;

import com.data.datafusion.util.entity.UuidV7Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.ZonedDateTime;
import org.hibernate.annotations.Filter;

/**
 * 分析数据集：可被多个分析看板复用的指标数据集或 SQL 数据集。
 *
 * <p>{@link #definition} 保存与类型相关的定义 JSON：
 * <ul>
 *   <li>{@link #TYPE_METRIC}：{@code {"metricQuery": {"metricCodes": [], "dimensions": [], "timeRange": {}}}}</li>
 *   <li>{@link #TYPE_SQL}：{@code {"sql": "SELECT ...", "params": []}}</li>
 * </ul>
 * {@link #fields} 缓存最近一次预览的列信息，供看板设计器做字段选择。
 * 看板只保存数据集 id 引用，查询时由服务端解析最新定义。
 */
@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "dp_analysis_dataset")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AnalysisDataset implements Serializable, TenantAware {

    /** 数据集类型：指标数据集。 */
    public static final String TYPE_METRIC = "METRIC";

    /** 数据集类型：物理表 SQL 数据集。 */
    public static final String TYPE_SQL = "SQL";

    /** 状态：启用。 */
    public static final String STATUS_ENABLED = "ENABLED";

    /** 状态：停用。 */
    public static final String STATUS_DISABLED = "DISABLED";

    /** 持久化状态：未启用。 */
    public static final String PERSIST_STATUS_DISABLED = "DISABLED";

    /** 持久化状态：已配置但尚未生成结果。 */
    public static final String PERSIST_STATUS_PENDING = "PENDING";

    /** 持久化状态：任务执行中。 */
    public static final String PERSIST_STATUS_RUNNING = "RUNNING";

    /** 持久化状态：结果可用。 */
    public static final String PERSIST_STATUS_READY = "READY";

    /** 持久化状态：最近一次执行失败。 */
    public static final String PERSIST_STATUS_FAILED = "FAILED";

    /** 持久化状态：定义已变化或结果过期。 */
    public static final String PERSIST_STATUS_STALE = "STALE";

    /** 写入模式：覆盖目标表。 */
    public static final String PERSIST_MODE_REPLACE = "REPLACE";

    /** 写入模式：追加到目标表。 */
    public static final String PERSIST_MODE_APPEND = "APPEND";

    private static final long serialVersionUID = 1L;

    @Id
    @UuidV7Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "name")
    private String name;

    @Column(name = "code")
    private String code;

    @Column(name = "description")
    private String description;

    @Column(name = "type", length = 16)
    private String type;

    @Column(name = "data_source_id", length = 36)
    private String dataSourceId;

    @Column(name = "definition")
    private String definition;

    @Column(name = "fields")
    private String fields;

    @Column(name = "status", length = 32)
    private String status;

    /** 是否启用结果持久化。 */
    @Column(name = "persist_enabled")
    private Boolean persistEnabled = Boolean.FALSE;

    /** 持久化目标数据源 id。 */
    @Column(name = "persist_target_data_source_id", length = 36)
    private String persistTargetDataSourceId;

    /** 持久化目标 schema。 */
    @Column(name = "persist_target_schema")
    private String persistTargetSchema;

    /** 持久化目标表。 */
    @Column(name = "persist_target_table")
    private String persistTargetTable;

    /** 持久化写入模式：REPLACE / APPEND。 */
    @Column(name = "persist_write_mode", length = 16)
    private String persistWriteMode;

    /** 持久化任务 cron；为空表示仅手动执行。 */
    @Column(name = "persist_cron")
    private String persistCron;

    /** 持久化结果状态。 */
    @Column(name = "persist_status", length = 32)
    private String persistStatus;

    /** 最近一次触发时间。 */
    @Column(name = "persist_last_run_time")
    private ZonedDateTime persistLastRunTime;

    /** 最近一次成功完成时间。 */
    @Column(name = "persist_last_success_time")
    private ZonedDateTime persistLastSuccessTime;

    /** 最近一次失败原因。 */
    @Column(name = "persist_last_error", length = 2000)
    private String persistLastError;

    /** 产出结果对应的数据集定义摘要，用于判断结果是否过期。 */
    @Column(name = "persist_definition_hash", length = 64)
    private String persistDefinitionHash;

    /** 生成的数据集任务 id。 */
    @Column(name = "persist_etl_task_id", length = 36)
    private String persistEtlTaskId;

    /** 生成的数据集调度 Job id。 */
    @Column(name = "persist_job_id", length = 36)
    private String persistJobId;

    /** 最近一次成功产出的行数。 */
    @Column(name = "persist_row_count")
    private Long persistRowCount;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "update_time")
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

    public ZonedDateTime getPersistLastRunTime() {
        return persistLastRunTime;
    }

    public void setPersistLastRunTime(ZonedDateTime persistLastRunTime) {
        this.persistLastRunTime = persistLastRunTime;
    }

    public ZonedDateTime getPersistLastSuccessTime() {
        return persistLastSuccessTime;
    }

    public void setPersistLastSuccessTime(ZonedDateTime persistLastSuccessTime) {
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
        if (!(o instanceof AnalysisDataset)) {
            return false;
        }
        return getId() != null && getId().equals(((AnalysisDataset) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "AnalysisDataset{" +
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
            "', tenantId='" +
            getTenantId() +
            "'}"
        );
    }
}
