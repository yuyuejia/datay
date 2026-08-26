package com.data.datafusion.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * A JobDepend.
 */
@Entity
@Table(name = "dp_job_depend")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class JobDepend implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "parent_job_code")
    private String parentJobCode;

    @Column(name = "child_job_code")
    private String childJobCode;

    @Column(name = "job_code")
    private String jobCode;

    @Column(name = "last_interval")
    private Long lastInterval;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "tenant_id")
    private String tenantId;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public JobDepend id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getParentJobCode() {
        return this.parentJobCode;
    }

    public JobDepend parentJobCode(String parentJobCode) {
        this.setParentJobCode(parentJobCode);
        return this;
    }

    public void setParentJobCode(String parentJobCode) {
        this.parentJobCode = parentJobCode;
    }

    public String getChildJobCode() {
        return this.childJobCode;
    }

    public JobDepend childJobCode(String childJobCode) {
        this.setChildJobCode(childJobCode);
        return this;
    }

    public void setChildJobCode(String childJobCode) {
        this.childJobCode = childJobCode;
    }

    public String getJobCode() {
        return this.jobCode;
    }

    public JobDepend jobCode(String jobCode) {
        this.setJobCode(jobCode);
        return this;
    }

    public void setJobCode(String jobCode) {
        this.jobCode = jobCode;
    }

    public Long getLastInterval() {
        return this.lastInterval;
    }

    public JobDepend lastInterval(Long lastInterval) {
        this.setLastInterval(lastInterval);
        return this;
    }

    public void setLastInterval(Long lastInterval) {
        this.lastInterval = lastInterval;
    }

    public ZonedDateTime getCreateTime() {
        return this.createTime;
    }

    public JobDepend createTime(ZonedDateTime createTime) {
        this.setCreateTime(createTime);
        return this;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public JobDepend tenantId(String tenantId) {
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
        if (!(o instanceof JobDepend)) {
            return false;
        }
        return getId() != null && getId().equals(((JobDepend) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "JobDepend{" +
            "id=" + getId() +
            ", parentJobCode='" + getParentJobCode() + "'" +
            ", childJobCode='" + getChildJobCode() + "'" +
            ", jobCode='" + getJobCode() + "'" +
            ", lastInterval=" + getLastInterval() +
            ", createTime='" + getCreateTime() + "'" +
            ", tenantId='" + getTenantId() + "'" +
            "}";
    }
}
