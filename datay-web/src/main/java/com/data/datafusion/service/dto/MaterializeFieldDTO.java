package com.data.datafusion.service.dto;

import java.io.Serializable;

public class MaterializeFieldDTO implements Serializable {

    private String fieldName;
    private String logicalType;
    private String physicalType;
    private Integer fieldLength;
    private Integer fieldPrecision;
    private Integer fieldScale;
    private Boolean isPrimaryKey;
    private String description;

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getLogicalType() {
        return logicalType;
    }

    public void setLogicalType(String logicalType) {
        this.logicalType = logicalType;
    }

    public String getPhysicalType() {
        return physicalType;
    }

    public void setPhysicalType(String physicalType) {
        this.physicalType = physicalType;
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

    public Boolean getIsPrimaryKey() {
        return isPrimaryKey;
    }

    public void setIsPrimaryKey(Boolean isPrimaryKey) {
        this.isPrimaryKey = isPrimaryKey;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}