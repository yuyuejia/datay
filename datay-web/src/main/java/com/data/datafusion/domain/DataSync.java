package com.data.datafusion.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * A DataSync.
 */
@Entity
@Table(name = "dp_data_sync")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DataSync implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "job_name")
    private String jobName;

    @Column(name = "job_code")
    private String jobCode;

    @Column(name = "job_desc")
    private String jobDesc;

    @Column(name = "dir")
    private String dir;

    @Column(name = "type")
    private String type;

    @Column(name = "source")
    private String source;

    @Column(name = "target")
    private String target;

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

    public DataSync id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getJobName() {
        return this.jobName;
    }

    public DataSync jobName(String jobName) {
        this.setJobName(jobName);
        return this;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getJobCode() {
        return this.jobCode;
    }

    public DataSync jobCode(String jobCode) {
        this.setJobCode(jobCode);
        return this;
    }

    public void setJobCode(String jobCode) {
        this.jobCode = jobCode;
    }

    public String getJobDesc() {
        return this.jobDesc;
    }

    public DataSync jobDesc(String jobDesc) {
        this.setJobDesc(jobDesc);
        return this;
    }

    public void setJobDesc(String jobDesc) {
        this.jobDesc = jobDesc;
    }

    public String getDir() {
        return this.dir;
    }

    public DataSync dir(String dir) {
        this.setDir(dir);
        return this;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public String getType() {
        return this.type;
    }

    public DataSync type(String type) {
        this.setType(type);
        return this;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSource() {
        return this.source;
    }

    public DataSync source(String source) {
        this.setSource(source);
        return this;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return this.target;
    }

    public DataSync target(String target) {
        this.setTarget(target);
        return this;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getCron() {
        return this.cron;
    }

    public DataSync cron(String cron) {
        this.setCron(cron);
        return this;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getJobContext() {
        return this.jobContext;
    }

    public DataSync jobContext(String jobContext) {
        this.setJobContext(jobContext);
        return this;
    }

    public void setJobContext(String jobContext) {
        this.jobContext = jobContext;
    }

    public String getStatus() {
        return this.status;
    }

    public DataSync status(String status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastStatus() {
        return this.lastStatus;
    }

    public DataSync lastStatus(String lastStatus) {
        this.setLastStatus(lastStatus);
        return this;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }

    public ZonedDateTime getUpdateTime() {
        return this.updateTime;
    }

    public DataSync updateTime(ZonedDateTime updateTime) {
        this.setUpdateTime(updateTime);
        return this;
    }

    public void setUpdateTime(ZonedDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public ZonedDateTime getCreateTime() {
        return this.createTime;
    }

    public DataSync createTime(ZonedDateTime createTime) {
        this.setCreateTime(createTime);
        return this;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public String getProject() {
        return this.project;
    }

    public DataSync project(String project) {
        this.setProject(project);
        return this;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public DataSync tenantId(String tenantId) {
        this.setTenantId(tenantId);
        return this;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Integer getDr() {
        return this.dr;
    }

    public DataSync dr(Integer dr) {
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
        if (!(o instanceof DataSync)) {
            return false;
        }
        return getId() != null && getId().equals(((DataSync) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "DataSync{" +
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
}
