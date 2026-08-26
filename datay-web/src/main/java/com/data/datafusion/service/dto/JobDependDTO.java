package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A DTO for the {@link com.data.datafusion.domain.JobDepend} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class JobDependDTO implements Serializable {

    private Long id;

    private String parentJobCode;

    private String childJobCode;

    private String jobCode;

    private Long lastInterval;

    private ZonedDateTime createTime;

    private String tenantId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getParentJobCode() {
        return parentJobCode;
    }

    public void setParentJobCode(String parentJobCode) {
        this.parentJobCode = parentJobCode;
    }

    public String getChildJobCode() {
        return childJobCode;
    }

    public void setChildJobCode(String childJobCode) {
        this.childJobCode = childJobCode;
    }

    public String getJobCode() {
        return jobCode;
    }

    public void setJobCode(String jobCode) {
        this.jobCode = jobCode;
    }

    public Long getLastInterval() {
        return lastInterval;
    }

    public void setLastInterval(Long lastInterval) {
        this.lastInterval = lastInterval;
    }

    public ZonedDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
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
        if (!(o instanceof JobDependDTO)) {
            return false;
        }

        JobDependDTO jobDependDTO = (JobDependDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, jobDependDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "JobDependDTO{" +
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
