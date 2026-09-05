package com.data.datafusion.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;

@Entity
@Table(name = "model_field")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ModelField implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "model_id")
    private Long modelId;

    @Column(name = "field_name")
    private String fieldName;

    @Column(name = "field_type")
    private String fieldType;

    @Column(name = "field_length")
    private Integer fieldLength;

    @Column(name = "field_precision")
    private Integer fieldPrecision;

    @Column(name = "field_scale")
    private Integer fieldScale;

    @Column(name = "description")
    private String description;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "is_partition_key")
    private Boolean isPartitionKey;

    @Column(name = "is_primary_key")
    private Boolean isPrimaryKey;

    @Column(name = "dimension_model_id")
    private Long dimensionModelId;

    @Column(name = "dimension_field_id")
    private Long dimensionFieldId;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "update_time")
    private ZonedDateTime updateTime;

    public Long getId() {
        return this.id;
    }

    public ModelField id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getModelId() {
        return this.modelId;
    }

    public ModelField modelId(Long modelId) {
        this.setModelId(modelId);
        return this;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public String getFieldName() {
        return this.fieldName;
    }

    public ModelField fieldName(String fieldName) {
        this.setFieldName(fieldName);
        return this;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldType() {
        return this.fieldType;
    }

    public ModelField fieldType(String fieldType) {
        this.setFieldType(fieldType);
        return this;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public Integer getFieldLength() {
        return this.fieldLength;
    }

    public ModelField fieldLength(Integer fieldLength) {
        this.setFieldLength(fieldLength);
        return this;
    }

    public void setFieldLength(Integer fieldLength) {
        this.fieldLength = fieldLength;
    }

    public Integer getFieldPrecision() {
        return this.fieldPrecision;
    }

    public ModelField fieldPrecision(Integer fieldPrecision) {
        this.setFieldPrecision(fieldPrecision);
        return this;
    }

    public void setFieldPrecision(Integer fieldPrecision) {
        this.fieldPrecision = fieldPrecision;
    }

    public Integer getFieldScale() {
        return this.fieldScale;
    }

    public ModelField fieldScale(Integer fieldScale) {
        this.setFieldScale(fieldScale);
        return this;
    }

    public void setFieldScale(Integer fieldScale) {
        this.fieldScale = fieldScale;
    }

    public String getDescription() {
        return this.description;
    }

    public ModelField description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSortOrder() {
        return this.sortOrder;
    }

    public ModelField sortOrder(Integer sortOrder) {
        this.setSortOrder(sortOrder);
        return this;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getIsPartitionKey() {
        return this.isPartitionKey;
    }

    public ModelField isPartitionKey(Boolean isPartitionKey) {
        this.setIsPartitionKey(isPartitionKey);
        return this;
    }

    public void setIsPartitionKey(Boolean isPartitionKey) {
        this.isPartitionKey = isPartitionKey;
    }

    public Boolean getIsPrimaryKey() {
        return this.isPrimaryKey;
    }

    public ModelField isPrimaryKey(Boolean isPrimaryKey) {
        this.setIsPrimaryKey(isPrimaryKey);
        return this;
    }

    public void setIsPrimaryKey(Boolean isPrimaryKey) {
        this.isPrimaryKey = isPrimaryKey;
    }

    public Long getDimensionModelId() {
        return this.dimensionModelId;
    }

    public ModelField dimensionModelId(Long dimensionModelId) {
        this.setDimensionModelId(dimensionModelId);
        return this;
    }

    public void setDimensionModelId(Long dimensionModelId) {
        this.dimensionModelId = dimensionModelId;
    }

    public Long getDimensionFieldId() {
        return this.dimensionFieldId;
    }

    public ModelField dimensionFieldId(Long dimensionFieldId) {
        this.setDimensionFieldId(dimensionFieldId);
        return this;
    }

    public void setDimensionFieldId(Long dimensionFieldId) {
        this.dimensionFieldId = dimensionFieldId;
    }

    public ZonedDateTime getCreateTime() {
        return this.createTime;
    }

    public ModelField createTime(ZonedDateTime createTime) {
        this.setCreateTime(createTime);
        return this;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public ZonedDateTime getUpdateTime() {
        return this.updateTime;
    }

    public ModelField updateTime(ZonedDateTime updateTime) {
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
        if (!(o instanceof ModelField)) {
            return false;
        }
        return getId() != null && getId().equals(((ModelField) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ModelField{" +
            "id=" + getId() +
            ", modelId=" + getModelId() +
            ", fieldName='" + getFieldName() + "'" +
            ", fieldType='" + getFieldType() + "'" +
            ", fieldLength=" + getFieldLength() +
            ", fieldPrecision=" + getFieldPrecision() +
            ", fieldScale=" + getFieldScale() +
            ", description='" + getDescription() + "'" +
            ", sortOrder=" + getSortOrder() +
            ", isPartitionKey=" + getIsPartitionKey() +
            ", isPrimaryKey=" + getIsPrimaryKey() +
            ", dimensionModelId=" + getDimensionModelId() +
            ", dimensionFieldId=" + getDimensionFieldId() +
            ", createTime='" + getCreateTime() + "'" +
            ", updateTime='" + getUpdateTime() + "'" +
            "}";
    }
}