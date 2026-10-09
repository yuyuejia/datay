package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

@SuppressWarnings("common-java:DuplicatedBlocks")
public class ModelFieldDTO implements Serializable {

    private String id;
    private String modelId;
    private String fieldName;
    private String fieldType;
    private Integer fieldLength;
    private Integer fieldPrecision;
    private Integer fieldScale;
    private String description;
    private Integer sortOrder;
    private Boolean isPartitionKey;
    private Boolean isPrimaryKey;
    private String dimensionModelId;
    private String dimensionFieldId;
    private String fieldRole;
    private Integer levelIndex;
    private ZonedDateTime createTime;
    private ZonedDateTime updateTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public Integer getFieldLength() {
        return fieldLength;
    }

    public void setFieldLength(Integer fieldLength) {
        this.fieldLength = fieldLength;
    }

    public Integer getFieldPrecision() {
        return fieldPrecision;
    }

    public void setFieldPrecision(Integer fieldPrecision) {
        this.fieldPrecision = fieldPrecision;
    }

    public Integer getFieldScale() {
        return fieldScale;
    }

    public void setFieldScale(Integer fieldScale) {
        this.fieldScale = fieldScale;
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

    public Boolean getIsPartitionKey() {
        return isPartitionKey;
    }

    public void setIsPartitionKey(Boolean isPartitionKey) {
        this.isPartitionKey = isPartitionKey;
    }

    public Boolean getIsPrimaryKey() {
        return isPrimaryKey;
    }

    public void setIsPrimaryKey(Boolean isPrimaryKey) {
        this.isPrimaryKey = isPrimaryKey;
    }

    public String getDimensionModelId() {
        return dimensionModelId;
    }

    public void setDimensionModelId(String dimensionModelId) {
        this.dimensionModelId = dimensionModelId;
    }

    public String getDimensionFieldId() {
        return dimensionFieldId;
    }

    public void setDimensionFieldId(String dimensionFieldId) {
        this.dimensionFieldId = dimensionFieldId;
    }

    public String getFieldRole() {
        return fieldRole;
    }

    public void setFieldRole(String fieldRole) {
        this.fieldRole = fieldRole;
    }

    public Integer getLevelIndex() {
        return levelIndex;
    }

    public void setLevelIndex(Integer levelIndex) {
        this.levelIndex = levelIndex;
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
        if (!(o instanceof ModelFieldDTO)) {
            return false;
        }
        return getId() != null && getId().equals(((ModelFieldDTO) o).getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String toString() {
        return "ModelFieldDTO{" +
            "id=" + getId() +
            ", modelId=" + getModelId() +
            ", fieldName='" + getFieldName() + "'" +
            ", fieldType='" + getFieldType() + "'" +
            ", fieldLength=" + getFieldLength() +
            ", fieldPrecision=" + getFieldPrecision() +
            ", fieldScale=" + getFieldScale() +
            ", isPartitionKey=" + getIsPartitionKey() +
            ", isPrimaryKey=" + getIsPrimaryKey() +
            "}";
    }
}