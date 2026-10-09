package com.data.datafusion.domain;

import com.data.datafusion.util.entity.UuidV7Id;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import org.hibernate.annotations.Filter;

/**
 * 分析看板：由 AI 助手生成、可在独立页签打开的数据分析仪表盘。
 *
 * <p>{@link #spec} 保存完整看板定义 JSON，包含数据集（指标 / 物理表 SQL）、
 * 组件（ECharts option / HTML）、布局与筛选器。查询由服务端按数据集定义执行，
 * 客户端不直接提交任意 SQL。
 */
@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "dp_analysis_dashboard")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AnalysisDashboard implements Serializable, TenantAware {

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

    @Column(name = "data_source_id", length = 36)
    private String dataSourceId;

    @Column(name = "spec")
    private String spec;

    @Column(name = "status")
    private String status;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "update_time")
    private ZonedDateTime updateTime;

    public String getId() {
        return this.id;
    }

    public AnalysisDashboard id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public AnalysisDashboard tenantId(String tenantId) {
        this.setTenantId(tenantId);
        return this;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return this.name;
    }

    public AnalysisDashboard name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return this.code;
    }

    public AnalysisDashboard code(String code) {
        this.setCode(code);
        return this;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return this.description;
    }

    public AnalysisDashboard description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDataSourceId() {
        return this.dataSourceId;
    }

    public AnalysisDashboard dataSourceId(String dataSourceId) {
        this.setDataSourceId(dataSourceId);
        return this;
    }

    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getSpec() {
        return this.spec;
    }

    public AnalysisDashboard spec(String spec) {
        this.setSpec(spec);
        return this;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public String getStatus() {
        return this.status;
    }

    public AnalysisDashboard status(String status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ZonedDateTime getCreateTime() {
        return this.createTime;
    }

    public AnalysisDashboard createTime(ZonedDateTime createTime) {
        this.setCreateTime(createTime);
        return this;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public ZonedDateTime getUpdateTime() {
        return this.updateTime;
    }

    public AnalysisDashboard updateTime(ZonedDateTime updateTime) {
        this.setUpdateTime(updateTime);
        return this;
    }

    public void setUpdateTime(ZonedDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AnalysisDashboard)) {
            return false;
        }
        return getId() != null && getId().equals(((AnalysisDashboard) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "AnalysisDashboard{" +
            "id=" +
            getId() +
            ", name='" +
            getName() +
            "'" +
            ", code='" +
            getCode() +
            "'" +
            ", status='" +
            getStatus() +
            "'" +
            ", tenantId='" +
            getTenantId() +
            "'" +
            "}"
        );
    }
}
