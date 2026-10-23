package com.data.datafusion.domain;

import com.data.datafusion.util.entity.UuidV7Id;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import org.hibernate.annotations.Filter;

/**
 * 角色数据集范围：基于 RBAC，约束某角色可访问的数据集行范围。
 * <p>
 * 一行表示「一个角色的一个数据集」的行级过滤条件；同一数据集内多条规则
 * （序列化为 {@link com.data.datafusion.service.dataset.DatasetScopeCondition} 的 JSON）
 * 在生成 SQL 时以 OR 组合，最终作为强制过滤条件作用于数据集查询。
 * <p>
 * 规则为空（无任何行）表示不限制；{@code ROLE_ADMIN} 默认不受数据范围限制。
 */
@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "dp_role_dataset_scope")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class RoleDatasetScope implements Serializable, TenantAware {

    private static final long serialVersionUID = 1L;

    @Id
    @UuidV7Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "tenant_id")
    private String tenantId;

    /** 角色名，对应 jhi_authority.name，如 ROLE_REGION_EAST。 */
    @Column(name = "role_name")
    private String roleName;

    /** 数据集 id，对应 dp_analysis_dataset.id。 */
    @Column(name = "dataset_id", length = 36)
    private String datasetId;

    /** 行级过滤条件，JSON 序列化的 DatasetScopeFilterConfig。 */
    @Column(name = "filter_config")
    private String filterConfig;

    /** 是否启用。 */
    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "update_time")
    private ZonedDateTime updateTime;

    public String getId() {
        return this.id;
    }

    public RoleDatasetScope id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public RoleDatasetScope tenantId(String tenantId) {
        this.setTenantId(tenantId);
        return this;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getRoleName() {
        return this.roleName;
    }

    public RoleDatasetScope roleName(String roleName) {
        this.setRoleName(roleName);
        return this;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getDatasetId() {
        return this.datasetId;
    }

    public RoleDatasetScope datasetId(String datasetId) {
        this.setDatasetId(datasetId);
        return this;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public String getFilterConfig() {
        return this.filterConfig;
    }

    public RoleDatasetScope filterConfig(String filterConfig) {
        this.setFilterConfig(filterConfig);
        return this;
    }

    public void setFilterConfig(String filterConfig) {
        this.filterConfig = filterConfig;
    }

    public Boolean getEnabled() {
        return this.enabled;
    }

    public RoleDatasetScope enabled(Boolean enabled) {
        this.setEnabled(enabled);
        return this;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public ZonedDateTime getCreateTime() {
        return this.createTime;
    }

    public RoleDatasetScope createTime(ZonedDateTime createTime) {
        this.setCreateTime(createTime);
        return this;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public ZonedDateTime getUpdateTime() {
        return this.updateTime;
    }

    public RoleDatasetScope updateTime(ZonedDateTime updateTime) {
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
        if (!(o instanceof RoleDatasetScope)) {
            return false;
        }
        return getId() != null && getId().equals(((RoleDatasetScope) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "RoleDatasetScope{" +
            "id=" + getId() +
            ", roleName='" + getRoleName() + "'" +
            ", datasetId=" + getDatasetId() +
            ", enabled=" + getEnabled() +
            ", tenantId='" + getTenantId() + "'" +
            "}";
    }
}
