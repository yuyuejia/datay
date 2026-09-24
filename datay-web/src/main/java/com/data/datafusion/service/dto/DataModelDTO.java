package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

@SuppressWarnings("common-java:DuplicatedBlocks")
public class DataModelDTO implements Serializable {

    private Long id;
    private String name;
    private String code;
    private String description;
    private Long directoryId;
    private String modelType;
    private String dimensionKind;
    private Integer levelCount;
    private String timeFieldName;
    private String timeLevels;
    private String timeStart;
    private String timeEnd;
    private Boolean isRegistered;
    private String project;
    private String tenantId;
    private Long dataSourceId;
    private String schemaName;
    private String tableName;
    private ZonedDateTime createTime;
    private ZonedDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getDirectoryId() {
        return directoryId;
    }

    public void setDirectoryId(Long directoryId) {
        this.directoryId = directoryId;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public String getDimensionKind() {
        return dimensionKind;
    }

    public void setDimensionKind(String dimensionKind) {
        this.dimensionKind = dimensionKind;
    }

    public Integer getLevelCount() {
        return levelCount;
    }

    public void setLevelCount(Integer levelCount) {
        this.levelCount = levelCount;
    }

    public String getTimeFieldName() {
        return timeFieldName;
    }

    public void setTimeFieldName(String timeFieldName) {
        this.timeFieldName = timeFieldName;
    }

    public String getTimeLevels() {
        return timeLevels;
    }

    public void setTimeLevels(String timeLevels) {
        this.timeLevels = timeLevels;
    }

    public String getTimeStart() {
        return timeStart;
    }

    public void setTimeStart(String timeStart) {
        this.timeStart = timeStart;
    }

    public String getTimeEnd() {
        return timeEnd;
    }

    public void setTimeEnd(String timeEnd) {
        this.timeEnd = timeEnd;
    }

    public Boolean getIsRegistered() {
        return isRegistered;
    }

    public void setIsRegistered(Boolean isRegistered) {
        this.isRegistered = isRegistered;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
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
        if (!(o instanceof DataModelDTO)) {
            return false;
        }
        return getId() != null && getId().equals(((DataModelDTO) o).getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String toString() {
        return "DataModelDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", code='" + getCode() + "'" +
            ", description='" + getDescription() + "'" +
            ", directoryId=" + getDirectoryId() +
            ", modelType='" + getModelType() + "'" +
            ", dimensionKind='" + getDimensionKind() + "'" +
            ", levelCount=" + getLevelCount() +
            ", timeFieldName='" + getTimeFieldName() + "'" +
            ", timeLevels='" + getTimeLevels() + "'" +
            ", timeStart='" + getTimeStart() + "'" +
            ", timeEnd='" + getTimeEnd() + "'" +
            ", isRegistered=" + getIsRegistered() +
            ", project='" + getProject() + "'" +
            ", tenantId='" + getTenantId() + "'" +
            ", dataSourceId=" + getDataSourceId() +
            ", schemaName='" + getSchemaName() + "'" +
            ", tableName='" + getTableName() + "'" +
            "}";
    }
}