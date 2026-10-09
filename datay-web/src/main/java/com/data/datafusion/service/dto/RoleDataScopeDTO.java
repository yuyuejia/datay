package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

@SuppressWarnings("common-java:DuplicatedBlocks")
public class RoleDataScopeDTO implements Serializable {

    private String id;
    private String roleName;
    private String dimensionModelId;
    private String filterConfig;
    private Boolean enabled;
    private ZonedDateTime createTime;
    private ZonedDateTime updateTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getDimensionModelId() {
        return dimensionModelId;
    }

    public void setDimensionModelId(String dimensionModelId) {
        this.dimensionModelId = dimensionModelId;
    }

    public String getFilterConfig() {
        return filterConfig;
    }

    public void setFilterConfig(String filterConfig) {
        this.filterConfig = filterConfig;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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
        if (!(o instanceof RoleDataScopeDTO)) {
            return false;
        }
        return getId() != null && getId().equals(((RoleDataScopeDTO) o).getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String toString() {
        return "RoleDataScopeDTO{" +
            "id=" + getId() +
            ", roleName='" + getRoleName() + "'" +
            ", dimensionModelId=" + getDimensionModelId() +
            ", enabled=" + getEnabled() +
            "}";
    }
}
