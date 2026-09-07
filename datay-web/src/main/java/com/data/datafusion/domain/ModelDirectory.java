package com.data.datafusion.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.io.Serializable;
import java.time.ZonedDateTime;

@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "model_directory")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ModelDirectory implements Serializable, TenantAware {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "name")
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "update_time")
    private ZonedDateTime updateTime;

    public Long getId() {
        return this.id;
    }

    public ModelDirectory id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public ModelDirectory name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getParentId() {
        return this.parentId;
    }

    public ModelDirectory parentId(Long parentId) {
        this.setParentId(parentId);
        return this;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Integer getSortOrder() {
        return this.sortOrder;
    }

    public ModelDirectory sortOrder(Integer sortOrder) {
        this.setSortOrder(sortOrder);
        return this;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public ZonedDateTime getCreateTime() {
        return this.createTime;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public ModelDirectory tenantId(String tenantId) {
        this.setTenantId(tenantId);
        return this;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public ModelDirectory createTime(ZonedDateTime createTime) {
        this.setCreateTime(createTime);
        return this;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public ZonedDateTime getUpdateTime() {
        return this.updateTime;
    }

    public ModelDirectory updateTime(ZonedDateTime updateTime) {
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
        if (!(o instanceof ModelDirectory)) {
            return false;
        }
        return getId() != null && getId().equals(((ModelDirectory) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ModelDirectory{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", parentId=" + getParentId() +
            ", sortOrder=" + getSortOrder() +
            ", createTime='" + getCreateTime() + "'" +
            ", updateTime='" + getUpdateTime() + "'" +
            "}";
    }
}