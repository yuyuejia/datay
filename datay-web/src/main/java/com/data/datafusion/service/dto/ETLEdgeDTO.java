package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.util.Objects;

/**
 * A DTO for the {@link com.data.datafusion.domain.ETLEdge} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ETLEdgeDTO implements Serializable {

    private Long id;

    private String taskId;

    private String name;

    private String code;

    private String source;

    private String target;

    private String config;

    private String status;

    private String tenantId;

    private Integer dr;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
        if (!(o instanceof ETLEdgeDTO)) {
            return false;
        }

        ETLEdgeDTO eTLEdgeDTO = (ETLEdgeDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, eTLEdgeDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ETLEdgeDTO{" +
            "id=" + getId() +
            ", taskId='" + getTaskId() + "'" +
            ", name='" + getName() + "'" +
            ", code='" + getCode() + "'" +
            ", source='" + getSource() + "'" +
            ", target='" + getTarget() + "'" +
            ", config='" + getConfig() + "'" +
            ", status='" + getStatus() + "'" +
            ", tenantId='" + getTenantId() + "'" +
            ", dr=" + getDr() +
            "}";
    }
}
