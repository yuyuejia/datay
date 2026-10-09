package com.data.datafusion.domain;

import com.data.datafusion.util.entity.UuidV7Id;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * A DataSyncTableConfig.
 */
@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "dp_sync_table_config")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DataSyncTableConfig implements Serializable, TenantAware {

    private static final long serialVersionUID = 1L;

    @Id
    @UuidV7Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "sync_task")
    private String syncTask;

    @Column(name = "src_datasource")
    private String srcDatasource;

    @Column(name = "src_schema_name")
    private String srcSchemaName;

    @Column(name = "src_table_name")
    private String srcTableName;

    @Column(name = "src_col_pks")
    private String srcColPks;

    @Column(name = "des_datasource")
    private String desDatasource;

    @Column(name = "des_schema_name")
    private String desSchemaName;

    @Column(name = "des_table_name")
    private String desTableName;

    @Column(name = "des_col_pks")
    private String desColPks;

    @Column(name = "job_desc")
    private String jobDesc;

    @Column(name = "colume_config")
    private String columeConfig;

    @Column(name = "update_time")
    private ZonedDateTime updateTime;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "project")
    private String project;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "dr")
    private Integer dr;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public DataSyncTableConfig id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSyncTask() {
        return this.syncTask;
    }

    public DataSyncTableConfig syncTask(String syncTask) {
        this.setSyncTask(syncTask);
        return this;
    }

    public void setSyncTask(String syncTask) {
        this.syncTask = syncTask;
    }

    public String getSrcDatasource() {
        return this.srcDatasource;
    }

    public DataSyncTableConfig srcDatasource(String srcDatasource) {
        this.setSrcDatasource(srcDatasource);
        return this;
    }

    public void setSrcDatasource(String srcDatasource) {
        this.srcDatasource = srcDatasource;
    }

    public String getSrcSchemaName() {
        return this.srcSchemaName;
    }

    public DataSyncTableConfig srcSchemaName(String srcSchemaName) {
        this.setSrcSchemaName(srcSchemaName);
        return this;
    }

    public void setSrcSchemaName(String srcSchemaName) {
        this.srcSchemaName = srcSchemaName;
    }

    public String getSrcTableName() {
        return this.srcTableName;
    }

    public DataSyncTableConfig srcTableName(String srcTableName) {
        this.setSrcTableName(srcTableName);
        return this;
    }

    public void setSrcTableName(String srcTableName) {
        this.srcTableName = srcTableName;
    }

    public String getSrcColPks() {
        return this.srcColPks;
    }

    public DataSyncTableConfig srcColPks(String srcColPks) {
        this.setSrcColPks(srcColPks);
        return this;
    }

    public void setSrcColPks(String srcColPks) {
        this.srcColPks = srcColPks;
    }

    public String getDesDatasource() {
        return this.desDatasource;
    }

    public DataSyncTableConfig desDatasource(String desDatasource) {
        this.setDesDatasource(desDatasource);
        return this;
    }

    public void setDesDatasource(String desDatasource) {
        this.desDatasource = desDatasource;
    }

    public String getDesSchemaName() {
        return this.desSchemaName;
    }

    public DataSyncTableConfig desSchemaName(String desSchemaName) {
        this.setDesSchemaName(desSchemaName);
        return this;
    }

    public void setDesSchemaName(String desSchemaName) {
        this.desSchemaName = desSchemaName;
    }

    public String getDesTableName() {
        return this.desTableName;
    }

    public DataSyncTableConfig desTableName(String desTableName) {
        this.setDesTableName(desTableName);
        return this;
    }

    public void setDesTableName(String desTableName) {
        this.desTableName = desTableName;
    }

    public String getDesColPks() {
        return this.desColPks;
    }

    public DataSyncTableConfig desColPks(String desColPks) {
        this.setDesColPks(desColPks);
        return this;
    }

    public void setDesColPks(String desColPks) {
        this.desColPks = desColPks;
    }

    public String getJobDesc() {
        return this.jobDesc;
    }

    public DataSyncTableConfig jobDesc(String jobDesc) {
        this.setJobDesc(jobDesc);
        return this;
    }

    public void setJobDesc(String jobDesc) {
        this.jobDesc = jobDesc;
    }

    public String getColumeConfig() {
        return this.columeConfig;
    }

    public DataSyncTableConfig columeConfig(String columeConfig) {
        this.setColumeConfig(columeConfig);
        return this;
    }

    public void setColumeConfig(String columeConfig) {
        this.columeConfig = columeConfig;
    }

    public ZonedDateTime getUpdateTime() {
        return this.updateTime;
    }

    public DataSyncTableConfig updateTime(ZonedDateTime updateTime) {
        this.setUpdateTime(updateTime);
        return this;
    }

    public void setUpdateTime(ZonedDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public ZonedDateTime getCreateTime() {
        return this.createTime;
    }

    public DataSyncTableConfig createTime(ZonedDateTime createTime) {
        this.setCreateTime(createTime);
        return this;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public String getProject() {
        return this.project;
    }

    public DataSyncTableConfig project(String project) {
        this.setProject(project);
        return this;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public DataSyncTableConfig tenantId(String tenantId) {
        this.setTenantId(tenantId);
        return this;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Integer getDr() {
        return this.dr;
    }

    public DataSyncTableConfig dr(Integer dr) {
        this.setDr(dr);
        return this;
    }

    public void setDr(Integer dr) {
        this.dr = dr;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataSyncTableConfig)) {
            return false;
        }
        return getId() != null && getId().equals(((DataSyncTableConfig) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "DataSyncTableConfig{" +
            "id=" + getId() +
            ", syncTask='" + getSyncTask() + "'" +
            ", srcDatasource='" + getSrcDatasource() + "'" +
            ", srcSchemaName='" + getSrcSchemaName() + "'" +
            ", srcTableName='" + getSrcTableName() + "'" +
            ", srcColPks='" + getSrcColPks() + "'" +
            ", desDatasource='" + getDesDatasource() + "'" +
            ", desSchemaName='" + getDesSchemaName() + "'" +
            ", desTableName='" + getDesTableName() + "'" +
            ", desColPks='" + getDesColPks() + "'" +
            ", jobDesc='" + getJobDesc() + "'" +
            ", columeConfig='" + getColumeConfig() + "'" +
            ", updateTime='" + getUpdateTime() + "'" +
            ", createTime='" + getCreateTime() + "'" +
            ", project='" + getProject() + "'" +
            ", tenantId='" + getTenantId() + "'" +
            ", dr=" + getDr() +
            "}";
    }
}