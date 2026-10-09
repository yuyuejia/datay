package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A DTO for the {@link com.data.datafusion.domain.DpTable} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DpTableDTO implements Serializable {

    private String id;

    private String name;

    private String schemaName;

    private String description;

    private String sourceDBType;

    private String sourceId;

    private String sourceSchema;

    private String sourceTable;

    private String sqlContent;

    private String fileType;

    private String filePath;

    private ZonedDateTime updateTime;

    private ZonedDateTime createTime;

    private String tenantId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSourceDBType() {
        return sourceDBType;
    }

    public void setSourceDBType(String sourceDBType) {
        this.sourceDBType = sourceDBType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceSchema() {
        return sourceSchema;
    }

    public void setSourceSchema(String sourceSchema) {
        this.sourceSchema = sourceSchema;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    public String getSqlContent() {
        return sqlContent;
    }

    public void setSqlContent(String sqlContent) {
        this.sqlContent = sqlContent;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public ZonedDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(ZonedDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public ZonedDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DpTableDTO)) {
            return false;
        }

        DpTableDTO dpTableDTO = (DpTableDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, dpTableDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "DpTableDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", schemaName='" + getSchemaName() + "'" +
            ", description='" + getDescription() + "'" +
            ", sourceDBType='" + getSourceDBType() + "'" +
            ", sourceId='" + getSourceId() + "'" +
            ", sourceSchema='" + getSourceSchema() + "'" +
            ", sourceTable='" + getSourceTable() + "'" +
            ", sqlContent='" + getSqlContent() + "'" +
            ", fileType='" + getFileType() + "'" +
            ", filePath='" + getFilePath() + "'" +
            ", updateTime='" + getUpdateTime() + "'" +
            ", createTime='" + getCreateTime() + "'" +
            ", tenantId='" + getTenantId() + "'" +
            "}";
    }
}
