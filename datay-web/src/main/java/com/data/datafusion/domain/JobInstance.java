package com.data.datafusion.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * A JobInstance.
 */
@Entity
@Table(name = "dp_job_instance")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class JobInstance implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "instance_code")
    private String instanceCode;

    @Column(name = "job_name")
    private String jobName;

    @Column(name = "job_code")
    private String jobCode;

    @Column(name = "type")
    private String type;

    @Column(name = "job_context")
    private String jobContext;

    @Column(name = "status")
    private String status;

    @Column(name = "job_message")
    private String jobMessage;

    @Column(name = "exec_node")
    private String execNode;

    @Column(name = "start_time")
    private String startTime;

    @Column(name = "end_time")
    private String endTime;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "project")
    private String project;

    @Column(name = "tenant_id")
    private String tenantId;

    // jhipster-needle-entity-add-field - JHipster will add fields here
    @Transient
    private Boolean isSubJob;

    @Transient
    private String ParentInstanceCode;

    public Long getId() {
        return this.id;
    }

    public JobInstance id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInstanceCode() {
        return this.instanceCode;
    }

    public JobInstance instanceCode(String instanceCode) {
        this.setInstanceCode(instanceCode);
        return this;
    }

    public void setInstanceCode(String instanceCode) {
        this.instanceCode = instanceCode;
    }

    public String getJobName() {
        return this.jobName;
    }

    public JobInstance jobName(String jobName) {
        this.setJobName(jobName);
        return this;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getJobCode() {
        return this.jobCode;
    }

    public JobInstance jobCode(String jobCode) {
        this.setJobCode(jobCode);
        return this;
    }

    public void setJobCode(String jobCode) {
        this.jobCode = jobCode;
    }

    public String getType() {
        return this.type;
    }

    public JobInstance type(String type) {
        this.setType(type);
        return this;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getJobContext() {
        return this.jobContext;
    }

    public JobInstance jobContext(String jobContext) {
        this.setJobContext(jobContext);
        return this;
    }

    public void setJobContext(String jobContext) {
        this.jobContext = jobContext;
    }

    public String getStatus() {
        return this.status;
    }

    public JobInstance status(String status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getJobMessage() {
        return this.jobMessage;
    }

    public JobInstance jobMessage(String jobMessage) {
        this.setJobMessage(jobMessage);
        return this;
    }

    public void setJobMessage(String jobMessage) {
        this.jobMessage = jobMessage;
    }

    public String getExecNode() {
        return this.execNode;
    }

    public JobInstance execNode(String execNode) {
        this.setExecNode(execNode);
        return this;
    }

    public void setExecNode(String execNode) {
        this.execNode = execNode;
    }

    public String getStartTime() {
        return this.startTime;
    }

    public JobInstance startTime(String startTime) {
        this.setStartTime(startTime);
        return this;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return this.endTime;
    }

    public JobInstance endTime(String endTime) {
        this.setEndTime(endTime);
        return this;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public ZonedDateTime getCreateTime() {
        return this.createTime;
    }

    public JobInstance createTime(ZonedDateTime createTime) {
        this.setCreateTime(createTime);
        return this;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public String getProject() {
        return this.project;
    }

    public JobInstance project(String project) {
        this.setProject(project);
        return this;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public JobInstance tenantId(String tenantId) {
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
        if (!(o instanceof JobInstance)) {
            return false;
        }
        return getId() != null && getId().equals(((JobInstance) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "JobInstance{" +
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
            "}";
    }

    public Boolean getSubJob() {
        return isSubJob;
    }

    public void setSubJob(Boolean subJob) {
        isSubJob = subJob;
    }

    public String getParentInstanceCode() {
        return ParentInstanceCode;
    }

    public void setParentInstanceCode(String parentInstanceCode) {
        ParentInstanceCode = parentInstanceCode;
    }
}
