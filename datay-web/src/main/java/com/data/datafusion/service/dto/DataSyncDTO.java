package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A DTO for the {@link com.data.datafusion.domain.DataSync} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DataSyncDTO implements Serializable {

    private Long id;

    private String jobName;

    private String jobCode;

    private String jobDesc;

    private String dir;

    private String type;

    private String source;

    private String target;

    private String cron;

    private String jobContext;

    private String status;

    private String lastStatus;

    private ZonedDateTime updateTime;

    private ZonedDateTime createTime;

    private String project;

    private String tenantId;

    private Integer dr;

    //增加tableConfig
    private List<DataSyncTableConfigDTO> selectedTables;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getJobCode() {
        return jobCode;
    }

    public void setJobCode(String jobCode) {
        this.jobCode = jobCode;
    }

    public String getJobDesc() {
        return jobDesc;
    }

    public void setJobDesc(String jobDesc) {
        this.jobDesc = jobDesc;
    }

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getJobContext() {
        return jobContext;
    }

    public void setJobContext(String jobContext) {
        this.jobContext = jobContext;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
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
        if (!(o instanceof DataSyncDTO)) {
            return false;
        }

        DataSyncDTO dataSyncDTO = (DataSyncDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, dataSyncDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "DataSyncDTO{" +
            "id=" + getId() +
            ", jobName='" + getJobName() + "'" +
            ", jobCode='" + getJobCode() + "'" +
            ", jobDesc='" + getJobDesc() + "'" +
            ", dir='" + getDir() + "'" +
            ", type='" + getType() + "'" +
            ", source='" + getSource() + "'" +
            ", target='" + getTarget() + "'" +
            ", cron='" + getCron() + "'" +
            ", jobContext='" + getJobContext() + "'" +
            ", status='" + getStatus() + "'" +
            ", lastStatus='" + getLastStatus() + "'" +
            ", updateTime='" + getUpdateTime() + "'" +
            ", createTime='" + getCreateTime() + "'" +
            ", project='" + getProject() + "'" +
            ", tenantId='" + getTenantId() + "'" +
            ", dr=" + getDr() +
            "}";
    }

    public List<DataSyncTableConfigDTO> getSelectedTables() {
        return selectedTables;
    }

    public void setSelectedTables(List<DataSyncTableConfigDTO> selectedTables) {
        this.selectedTables = selectedTables;
    }
}
