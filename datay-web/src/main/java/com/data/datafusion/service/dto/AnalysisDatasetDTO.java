package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A DTO for the {@link com.data.datafusion.domain.AnalysisDataset} entity.
 *
 * <p>{@link #definition} 为与类型相关的定义 JSON 字符串；{@link #fields} 为最近一次预览的列信息 JSON。
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AnalysisDatasetDTO implements Serializable {

    private String id;
    private String tenantId;
    private String name;
    private String code;
    private String description;
    private String type;
    private String dataSourceId;
    private String definition;
    private String fields;
    private String status;
    private ZonedDateTime createTime;
    private ZonedDateTime updateTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public String getFields() {
        return fields;
    }

    public void setFields(String fields) {
        this.fields = fields;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
        if (!(o instanceof AnalysisDatasetDTO)) {
            return false;
        }
        return getId() != null && getId().equals(((AnalysisDatasetDTO) o).getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String toString() {
        return (
            "AnalysisDatasetDTO{" +
            "id=" +
            getId() +
            ", name='" +
            getName() +
            "', code='" +
            getCode() +
            "', type='" +
            getType() +
            "', status='" +
            getStatus() +
            "'}"
        );
    }
}
