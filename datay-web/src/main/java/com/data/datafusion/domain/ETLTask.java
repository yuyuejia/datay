package com.data.datafusion.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * A ETLTask.
 */
@Entity
@Table(name = "dp_etl_task")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ETLTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "task_name")
    private String taskName;

    @Column(name = "task_code")
    private String taskCode;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "task_desc")
    private String taskDesc;

    @Column(name = "dir")
    private String dir;

    @Column(name = "type")
    private String type;

    @Column(name = "cron")
    private String cron;

    @Column(name = "job_context")
    private String jobContext;

    @Column(name = "status")
    private String status;

    @Column(name = "last_status")
    private String lastStatus;

    @Column(name = "update_time")
    private ZonedDateTime updateTime;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "creater")
    private String creater;

    @Column(name = "project")
    private String project;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "dr")
    private Integer dr;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public ETLTask id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskName() {
        return this.taskName;
    }

    public ETLTask taskName(String taskName) {
        this.setTaskName(taskName);
        return this;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getTaskCode() {
        return this.taskCode;
    }

    public ETLTask taskCode(String taskCode) {
        this.setTaskCode(taskCode);
        return this;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public Long getJobId() {
        return this.jobId;
    }

    public ETLTask jobId(Long jobId) {
        this.setJobId(jobId);
        return this;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public String getTaskDesc() {
        return this.taskDesc;
    }

    public ETLTask taskDesc(String taskDesc) {
        this.setTaskDesc(taskDesc);
        return this;
    }

    public void setTaskDesc(String taskDesc) {
        this.taskDesc = taskDesc;
    }

    public String getDir() {
        return this.dir;
    }

    public ETLTask dir(String dir) {
        this.setDir(dir);
        return this;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public String getType() {
        return this.type;
    }

    public ETLTask type(String type) {
        this.setType(type);
        return this;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCron() {
        return this.cron;
    }

    public ETLTask cron(String cron) {
        this.setCron(cron);
        return this;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getJobContext() {
        return this.jobContext;
    }

    public ETLTask jobContext(String jobContext) {
        this.setJobContext(jobContext);
        return this;
    }

    public void setJobContext(String jobContext) {
        this.jobContext = jobContext;
    }

    public String getStatus() {
        return this.status;
    }

    public ETLTask status(String status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastStatus() {
        return this.lastStatus;
    }

    public ETLTask lastStatus(String lastStatus) {
        this.setLastStatus(lastStatus);
        return this;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }

    public ZonedDateTime getUpdateTime() {
        return this.updateTime;
    }

    public ETLTask updateTime(ZonedDateTime updateTime) {
        this.setUpdateTime(updateTime);
        return this;
    }

    public void setUpdateTime(ZonedDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public ZonedDateTime getCreateTime() {
        return this.createTime;
    }

    public ETLTask createTime(ZonedDateTime createTime) {
        this.setCreateTime(createTime);
        return this;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public String getCreater() {
        return this.creater;
    }

    public ETLTask creater(String creater) {
        this.setCreater(creater);
        return this;
    }

    public void setCreater(String creater) {
        this.creater = creater;
    }

    public String getProject() {
        return this.project;
    }

    public ETLTask project(String project) {
        this.setProject(project);
        return this;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public ETLTask tenantId(String tenantId) {
        this.setTenantId(tenantId);
        return this;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Integer getDr() {
        return this.dr;
    }

    public ETLTask dr(Integer dr) {
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
        if (!(o instanceof ETLTask)) {
            return false;
        }
        return getId() != null && getId().equals(((ETLTask) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ETLTask{" +
            "id=" + getId() +
            ", taskName='" + getTaskName() + "'" +
            ", taskCode='" + getTaskCode() + "'" +
            ", jobId=" + getJobId() +
            ", taskDesc='" + getTaskDesc() + "'" +
            ", dir='" + getDir() + "'" +
            ", type='" + getType() + "'" +
            ", cron='" + getCron() + "'" +
            ", jobContext='" + getJobContext() + "'" +
            ", status='" + getStatus() + "'" +
            ", lastStatus='" + getLastStatus() + "'" +
            ", updateTime='" + getUpdateTime() + "'" +
            ", createTime='" + getCreateTime() + "'" +
            ", creater='" + getCreater() + "'" +
            ", project='" + getProject() + "'" +
            ", tenantId='" + getTenantId() + "'" +
            ", dr=" + getDr() +
            "}";
    }
}
