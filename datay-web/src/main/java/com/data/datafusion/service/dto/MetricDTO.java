package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A DTO for the {@link com.data.datafusion.domain.Metric} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class MetricDTO implements Serializable {

    private String id;
    private String name;
    private String code;
    private String description;
    private String directoryId;
    private String metricType;
    private String status;
    private String factModelId;
    private String filterConfig;
    private String unit;
    private String dataType;
    private Boolean isAdditive;
    private String formula;
    private String tenantId;
    private ZonedDateTime createTime;
    private ZonedDateTime updateTime;

    /** 从计算公式解析出的引用指标展示信息。 */
    private List<MetricRefDTO> refMetrics = new ArrayList<>();

    /** 事实表模型名称（展示用）。 */
    private String factModelName;

    /** 事实表物理表名（展示用）。 */
    private String factTableName;

    public String getId() {
        return id;
    }

    public void setId(String id) {
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

    public String getDirectoryId() {
        return directoryId;
    }

    public void setDirectoryId(String directoryId) {
        this.directoryId = directoryId;
    }

    public String getMetricType() {
        return metricType;
    }

    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFactModelId() {
        return factModelId;
    }

    public void setFactModelId(String factModelId) {
        this.factModelId = factModelId;
    }

    public String getFilterConfig() {
        return filterConfig;
    }

    public void setFilterConfig(String filterConfig) {
        this.filterConfig = filterConfig;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public Boolean getIsAdditive() {
        return isAdditive;
    }

    public void setIsAdditive(Boolean isAdditive) {
        this.isAdditive = isAdditive;
    }

    public String getFormula() {
        return formula;
    }

    public void setFormula(String formula) {
        this.formula = formula;
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

    public List<MetricRefDTO> getRefMetrics() {
        return refMetrics;
    }

    public void setRefMetrics(List<MetricRefDTO> refMetrics) {
        this.refMetrics = refMetrics;
    }

    public String getFactModelName() {
        return factModelName;
    }

    public void setFactModelName(String factModelName) {
        this.factModelName = factModelName;
    }

    public String getFactTableName() {
        return factTableName;
    }

    public void setFactTableName(String factTableName) {
        this.factTableName = factTableName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MetricDTO)) {
            return false;
        }
        return getId() != null && getId().equals(((MetricDTO) o).getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String toString() {
        return "MetricDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", code='" + getCode() + "'" +
            ", metricType='" + getMetricType() + "'" +
            ", status='" + getStatus() + "'" +
            ", factModelId=" + getFactModelId() +
            ", dataType='" + getDataType() + "'" +
            ", isAdditive=" + getIsAdditive() +
            ", unit='" + getUnit() + "'" +
            "}";
    }
}
