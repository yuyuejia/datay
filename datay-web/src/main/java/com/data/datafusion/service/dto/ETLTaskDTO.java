package com.data.datafusion.service.dto;

import com.data.datafusion.domain.ETLEdge;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

/**
 * A DTO for the {@link com.data.datafusion.domain.ETLTask} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ETLTaskDTO implements Serializable {

    private Long id;

    private String taskName;

    private String taskCode;

    private Long jobId;

    private String taskDesc;

    private String dir;

    private String type;

    private String cron;

    private String jobContext;

    private String status;

    private String lastStatus;

    private ZonedDateTime updateTime;

    private ZonedDateTime createTime;

    private String creater;

    private String project;

    private String tenantId;

    private Integer dr;

    private List<ETLNodeDTO> nodes;

    private List<ETLEdgeDTO> edges;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public String getTaskDesc() {
        return taskDesc;
    }

    public void setTaskDesc(String taskDesc) {
        this.taskDesc = taskDesc;
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

    public String getCreater() {
        return creater;
    }

    public void setCreater(String creater) {
        this.creater = creater;
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
        if (!(o instanceof ETLTaskDTO)) {
            return false;
        }

        ETLTaskDTO eTLTaskDTO = (ETLTaskDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, eTLTaskDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ETLTaskDTO{" +
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

    public List<ETLNodeDTO> getNodes() {
        return nodes;
    }

    public void setNodes(List<ETLNodeDTO> nodes) {
        this.nodes = nodes;
    }

    public List<ETLEdgeDTO> getEdges() {
        return edges;
    }

    public void setEdges(List<ETLEdgeDTO> edges) {
        this.edges = edges;
    }
}
