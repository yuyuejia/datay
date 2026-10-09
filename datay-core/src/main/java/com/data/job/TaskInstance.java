package com.data.job;

import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * A JobInstance.
 */
public class TaskInstance implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

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

    // jhipster-needle-entity-add-field - JHipster will add fields here
    private Boolean isSubJob;

    private String ParentInstanceCode;

    public String getId() {
        return this.id;
    }

    public TaskInstance id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getInstanceCode() {
        return this.instanceCode;
    }

    public TaskInstance instanceCode(String instanceCode) {
        this.setInstanceCode(instanceCode);
        return this;
    }

    public void setInstanceCode(String instanceCode) {
        this.instanceCode = instanceCode;
    }

    public String getJobName() {
        return this.jobName;
    }

    public TaskInstance jobName(String jobName) {
        this.setJobName(jobName);
        return this;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getJobCode() {
        return this.jobCode;
    }

    public TaskInstance jobCode(String jobCode) {
        this.setJobCode(jobCode);
        return this;
    }

    public void setJobCode(String jobCode) {
        this.jobCode = jobCode;
    }

    public String getType() {
        return this.type;
    }

    public TaskInstance type(String type) {
        this.setType(type);
        return this;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getJobContext() {
        return this.jobContext;
    }

    public TaskInstance jobContext(String jobContext) {
        this.setJobContext(jobContext);
        return this;
    }

    public void setJobContext(String jobContext) {
        this.jobContext = jobContext;
    }

    public String getStatus() {
        return this.status;
    }

    public TaskInstance status(String status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getJobMessage() {
        return this.jobMessage;
    }

    public TaskInstance jobMessage(String jobMessage) {
        this.setJobMessage(jobMessage);
        return this;
    }

    public void setJobMessage(String jobMessage) {
        this.jobMessage = jobMessage;
    }

    public String getExecNode() {
        return this.execNode;
    }

    public TaskInstance execNode(String execNode) {
        this.setExecNode(execNode);
        return this;
    }

    public void setExecNode(String execNode) {
        this.execNode = execNode;
    }

    public String getStartTime() {
        return this.startTime;
    }

    public TaskInstance startTime(String startTime) {
        this.setStartTime(startTime);
        return this;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return this.endTime;
    }

    public TaskInstance endTime(String endTime) {
        this.setEndTime(endTime);
        return this;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public ZonedDateTime getCreateTime() {
        return this.createTime;
    }

    public TaskInstance createTime(ZonedDateTime createTime) {
        this.setCreateTime(createTime);
        return this;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public String getProject() {
        return this.project;
    }

    public TaskInstance project(String project) {
        this.setProject(project);
        return this;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public TaskInstance tenantId(String tenantId) {
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
        if (!(o instanceof TaskInstance)) {
            return false;
        }
        return getId() != null && getId().equals(((TaskInstance) o).getId());
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
