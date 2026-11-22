package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * 维度角色 DTO。
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DimensionRoleDTO implements Serializable {

    private String id;
    private String dimensionModelId;
    private String code;
    private String name;
    private String description;
    private Integer sortOrder;
    private ZonedDateTime createTime;
    private ZonedDateTime updateTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDimensionModelId() {
        return dimensionModelId;
    }

    public void setDimensionModelId(String dimensionModelId) {
        this.dimensionModelId = dimensionModelId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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
        if (!(o instanceof DimensionRoleDTO)) {
            return false;
        }
        return getId() != null && getId().equals(((DimensionRoleDTO) o).getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String toString() {
        return (
            "DimensionRoleDTO{" +
            "id=" +
            getId() +
            ", dimensionModelId=" +
            getDimensionModelId() +
            ", code='" +
            getCode() +
            "', name='" +
            getName() +
            "'}"
        );
    }
}
