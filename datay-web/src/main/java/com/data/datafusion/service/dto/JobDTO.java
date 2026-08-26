package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A DTO for the {@link com.data.datafusion.domain.Job} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class JobDTO implements Serializable {

    private Long id;

    private String jobName;

    private String jobGroup;

    private String type;

    private String cron;

    private String jobContext;

    private String status;

    private ZonedDateTime updateTime;

    private ZonedDateTime createTime;

    private String project;

    private String tenantId;

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

    public String getJobGroup() {
        return jobGroup;
    }

    public void setJobGroup(String jobGroup) {
        this.jobGroup = jobGroup;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JobDTO)) {
            return false;
        }

        JobDTO jobDTO = (JobDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, jobDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "JobDTO{" +
            "id=" + getId() +
            ", jobName='" + getJobName() + "'" +
            ", jobGroup='" + getJobGroup() + "'" +
            ", type='" + getType() + "'" +
            ", cron='" + getCron() + "'" +
            ", jobContext='" + getJobContext() + "'" +
            ", status='" + getStatus() + "'" +
            ", updateTime='" + getUpdateTime() + "'" +
            ", createTime='" + getCreateTime() + "'" +
            ", project='" + getProject() + "'" +
            ", tenantId='" + getTenantId() + "'" +
            "}";
    }
}
