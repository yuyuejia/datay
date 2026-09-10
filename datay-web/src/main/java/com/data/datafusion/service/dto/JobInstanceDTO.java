package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A DTO for the {@link com.data.datafusion.domain.JobInstance} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class JobInstanceDTO implements Serializable {

    private Long id;

    private String instanceCode;

    private String jobName;

    private String jobCode;

    private String type;

    private String jobContext;

    private String status;

    private String jobMessage;

    private String execNode;

    private String startTime;

    private String endTime;

    private ZonedDateTime createTime;

    private String project;

    private String tenantId;

    private String parentInstanceCode;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInstanceCode() {
        return instanceCode;
    }

    public void setInstanceCode(String instanceCode) {
        this.instanceCode = instanceCode;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public String getJobMessage() {
        return jobMessage;
    }

    public void setJobMessage(String jobMessage) {
        this.jobMessage = jobMessage;
    }

    public String getExecNode() {
        return execNode;
    }

    public void setExecNode(String execNode) {
        this.execNode = execNode;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
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

    public String getParentInstanceCode() {
        return parentInstanceCode;
    }

    public void setParentInstanceCode(String parentInstanceCode) {
        this.parentInstanceCode = parentInstanceCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JobInstanceDTO)) {
            return false;
        }

        JobInstanceDTO jobInstanceDTO = (JobInstanceDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, jobInstanceDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "JobInstanceDTO{" +
            "id=" + getId() +
            ", instanceCode='" + getInstanceCode() + "'" +
            ", jobName='" + getJobName() + "'" +
            ", jobCode='" + getJobCode() + "'" +
            ", type='" + getType() + "'" +
            ", jobContext='" + getJobContext() + "'" +
            ", status='" + getStatus() + "'" +
            ", jobMessage='" + getJobMessage() + "'" +
            ", execNode='" + getExecNode() + "'" +
            ", startTime='" + getStartTime() + "'" +
            ", endTime='" + getEndTime() + "'" +
            ", createTime='" + getCreateTime() + "'" +
            ", project='" + getProject() + "'" +
            ", tenantId='" + getTenantId() + "'" +
            ", parentInstanceCode='" + getParentInstanceCode() + "'" +
            "}";
    }
}
