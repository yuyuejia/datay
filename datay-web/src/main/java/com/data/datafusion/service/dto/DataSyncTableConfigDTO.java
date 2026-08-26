package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A DTO for the {@link com.data.datafusion.domain.DataSyncTableConfig} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DataSyncTableConfigDTO implements Serializable {

    private Long id;

    private String syncTask;

    private String srcDatasource;

    private String srcSchemaName;

    private String srcTableName;

    private String srcColPks;

    private String desDatasource;

    private String desSchemaName;

    private String desTableName;

    private String desColPks;

    private String jobDesc;

    private String columeConfig;

    private ZonedDateTime updateTime;

    private ZonedDateTime createTime;

    private String project;

    private String tenantId;

    private Integer dr;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSyncTask() {
        return syncTask;
    }

    public void setSyncTask(String syncTask) {
        this.syncTask = syncTask;
    }

    public String getSrcDatasource() {
        return srcDatasource;
    }

    public void setSrcDatasource(String srcDatasource) {
        this.srcDatasource = srcDatasource;
    }

    public String getSrcSchemaName() {
        return srcSchemaName;
    }

    public void setSrcSchemaName(String srcSchemaName) {
        this.srcSchemaName = srcSchemaName;
    }

    public String getSrcTableName() {
        return srcTableName;
    }

    public void setSrcTableName(String srcTableName) {
        this.srcTableName = srcTableName;
    }

    public String getSrcColPks() {
        return srcColPks;
    }

    public void setSrcColPks(String srcColPks) {
        this.srcColPks = srcColPks;
    }

    public String getDesDatasource() {
        return desDatasource;
    }

    public void setDesDatasource(String desDatasource) {
        this.desDatasource = desDatasource;
    }

    public String getDesSchemaName() {
        return desSchemaName;
    }

    public void setDesSchemaName(String desSchemaName) {
        this.desSchemaName = desSchemaName;
    }

    public String getDesTableName() {
        return desTableName;
    }

    public void setDesTableName(String desTableName) {
        this.desTableName = desTableName;
    }

    public String getDesColPks() {
        return desColPks;
    }

    public void setDesColPks(String desColPks) {
        this.desColPks = desColPks;
    }

    public String getJobDesc() {
        return jobDesc;
    }

    public void setJobDesc(String jobDesc) {
        this.jobDesc = jobDesc;
    }

    public String getColumeConfig() {
        return columeConfig;
    }

    public void setColumeConfig(String columeConfig) {
        this.columeConfig = columeConfig;
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

    public Integer getDr() {
        return dr;
    }

    public void setDr(Integer dr) {
        this.dr = dr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataSyncTableConfigDTO)) {
            return false;
        }

        DataSyncTableConfigDTO dataSyncTableConfigDTO = (DataSyncTableConfigDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, dataSyncTableConfigDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "DataSyncTableConfigDTO{" +
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
