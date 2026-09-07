package com.data.datafusion.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.io.Serializable;
import java.time.ZonedDateTime;

@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "data_model")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DataModel implements Serializable, TenantAware {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "code")
    private String code;

    @Column(name = "description")
    private String description;

    @Column(name = "directory_id")
    private Long directoryId;

    @Column(name = "model_type")
    private String modelType;

    @Column(name = "is_registered")
    private Boolean isRegistered;

    @Column(name = "project")
    private String project;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "data_source_id")
    private Long dataSourceId;

    @Column(name = "schema_name")
    private String schemaName;

    @Column(name = "table_name")
    private String tableName;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "update_time")
    private ZonedDateTime updateTime;

    public Long getId() {
        return this.id;
    }

    public DataModel id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public DataModel name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return this.code;
    }

    public DataModel code(String code) {
        this.setCode(code);
        return this;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return this.description;
    }

    public DataModel description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getDirectoryId() {
        return this.directoryId;
    }

    public DataModel directoryId(Long directoryId) {
        this.setDirectoryId(directoryId);
        return this;
    }

    public void setDirectoryId(Long directoryId) {
        this.directoryId = directoryId;
    }

    public String getModelType() {
        return this.modelType;
    }

    public DataModel modelType(String modelType) {
        this.setModelType(modelType);
        return this;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public Boolean getIsRegistered() {
        return this.isRegistered;
    }

    public DataModel isRegistered(Boolean isRegistered) {
        this.setIsRegistered(isRegistered);
        return this;
    }

    public void setIsRegistered(Boolean isRegistered) {
        this.isRegistered = isRegistered;
    }

    public String getProject() {
        return this.project;
    }

    public DataModel project(String project) {
        this.setProject(project);
        return this;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public DataModel tenantId(String tenantId) {
        this.setTenantId(tenantId);
        return this;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Long getDataSourceId() {
        return this.dataSourceId;
    }

    public DataModel dataSourceId(Long dataSourceId) {
        this.setDataSourceId(dataSourceId);
        return this;
    }

    public void setDataSourceId(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getSchemaName() {
        return this.schemaName;
    }

    public DataModel schemaName(String schemaName) {
        this.setSchemaName(schemaName);
        return this;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getTableName() {
        return this.tableName;
    }

    public DataModel tableName(String tableName) {
        this.setTableName(tableName);
        return this;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public ZonedDateTime getCreateTime() {
        return this.createTime;
    }

    public DataModel createTime(ZonedDateTime createTime) {
        this.setCreateTime(createTime);
        return this;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public ZonedDateTime getUpdateTime() {
        return this.updateTime;
    }

    public DataModel updateTime(ZonedDateTime updateTime) {
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
        if (!(o instanceof DataModel)) {
            return false;
        }
        return getId() != null && getId().equals(((DataModel) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "DataModel{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", code='" + getCode() + "'" +
            ", description='" + getDescription() + "'" +
            ", directoryId=" + getDirectoryId() +
            ", modelType='" + getModelType() + "'" +
            ", isRegistered=" + getIsRegistered() +
            ", project='" + getProject() + "'" +
            ", tenantId='" + getTenantId() + "'" +
            ", dataSourceId=" + getDataSourceId() +
            ", schemaName='" + getSchemaName() + "'" +
            ", tableName='" + getTableName() + "'" +
            ", createTime='" + getCreateTime() + "'" +
            ", updateTime='" + getUpdateTime() + "'" +
            "}";
    }
}