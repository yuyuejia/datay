package com.data.datafusion.domain;

import com.data.datafusion.util.entity.UuidV7Id;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * A DpTable.
 */
@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "dp_table")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DpTable implements Serializable, TenantAware {

    private static final long serialVersionUID = 1L;

    @Id
    @UuidV7Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "name")
    private String name;

    @Column(name = "schema_name")
    private String schemaName;

    @Column(name = "description")
    private String description;

    @Column(name = "source_db_type")
    private String sourceDBType;

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "source_schema")
    private String sourceSchema;

    @Column(name = "source_table")
    private String sourceTable;

    @Column(name = "sql_content")
    private String sqlContent;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "update_time")
    private ZonedDateTime updateTime;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "tenant_id")
    private String tenantId;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public DpTable id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public DpTable name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSchemaName() {
        return this.schemaName;
    }

    public DpTable schemaName(String schemaName) {
        this.setSchemaName(schemaName);
        return this;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getDescription() {
        return this.description;
    }

    public DpTable description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSourceDBType() {
        return this.sourceDBType;
    }

    public DpTable sourceDBType(String sourceDBType) {
        this.setSourceDBType(sourceDBType);
        return this;
    }

    public void setSourceDBType(String sourceDBType) {
        this.sourceDBType = sourceDBType;
    }

    public String getSourceId() {
        return this.sourceId;
    }

    public DpTable sourceId(String sourceId) {
        this.setSourceId(sourceId);
        return this;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceSchema() {
        return this.sourceSchema;
    }

    public DpTable sourceSchema(String sourceSchema) {
        this.setSourceSchema(sourceSchema);
        return this;
    }

    public void setSourceSchema(String sourceSchema) {
        this.sourceSchema = sourceSchema;
    }

    public String getSourceTable() {
        return this.sourceTable;
    }

    public DpTable sourceTable(String sourceTable) {
        this.setSourceTable(sourceTable);
        return this;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    public String getSqlContent() {
        return this.sqlContent;
    }

    public DpTable sqlContent(String sqlContent) {
        this.setSqlContent(sqlContent);
        return this;
    }

    public void setSqlContent(String sqlContent) {
        this.sqlContent = sqlContent;
    }

    public String getFileType() {
        return this.fileType;
    }

    public DpTable fileType(String fileType) {
        this.setFileType(fileType);
        return this;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getFilePath() {
        return this.filePath;
    }

    public DpTable filePath(String filePath) {
        this.setFilePath(filePath);
        return this;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public ZonedDateTime getUpdateTime() {
        return this.updateTime;
    }

    public DpTable updateTime(ZonedDateTime updateTime) {
        this.setUpdateTime(updateTime);
        return this;
    }

    public void setUpdateTime(ZonedDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public ZonedDateTime getCreateTime() {
        return this.createTime;
    }

    public DpTable createTime(ZonedDateTime createTime) {
        this.setCreateTime(createTime);
        return this;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public DpTable tenantId(String tenantId) {
        this.setTenantId(tenantId);
        return this;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DpTable)) {
            return false;
        }
        return getId() != null && getId().equals(((DpTable) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "DpTable{" +
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