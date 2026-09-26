package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A DTO for the {@link com.data.datafusion.domain.AnalysisDashboard} entity.
 *
 * <p>{@link #spec} 为看板定义 JSON 字符串，包含 datasets / widgets / filters 等，由前端解析渲染。
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AnalysisDashboardDTO implements Serializable {

    private Long id;
    private String name;
    private String code;
    private String description;
    private Long dataSourceId;
    private String spec;
    private String status;
    private String tenantId;
    private ZonedDateTime createTime;
    private ZonedDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
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

    public ZonedDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public ZonedDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(ZonedDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AnalysisDashboardDTO)) {
            return false;
        }
        return getId() != null && getId().equals(((AnalysisDashboardDTO) o).getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String toString() {
        return (
            "AnalysisDashboardDTO{" +
            "id=" +
            getId() +
            ", name='" +
            getName() +
            "'" +
            ", code='" +
            getCode() +
            "'" +
            ", status='" +
            getStatus() +
            "}"
        );
    }
}
