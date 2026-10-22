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
