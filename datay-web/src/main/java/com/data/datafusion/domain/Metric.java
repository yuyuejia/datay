package com.data.datafusion.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import org.hibernate.annotations.Filter;

/**
 * 指标：定义业务统计口径与计算逻辑。
 * <p>
 * 支持两种类型：
 * <ul>
 *     <li>{@link #TYPE_ATOMIC} 原子指标：选择事实表字段 + 聚合函数进行汇总计算。</li>
 *     <li>{@link #TYPE_DERIVED} 衍生原子指标：基于原子指标（可再引用衍生指标）通过公式再计算。</li>
 * </ul>
 */
@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "dp_metric")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Metric implements Serializable, TenantAware {

    /** 原子指标。 */
    public static final String TYPE_ATOMIC = "ATOMIC";

    /** 衍生原子指标。 */
    public static final String TYPE_DERIVED = "DERIVED";

    /** 状态：启用。 */
    public static final String STATUS_ENABLED = "ENABLED";

    /** 状态：停用。 */
    public static final String STATUS_DISABLED = "DISABLED";

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "name")
    private String name;

    @Column(name = "code")
    private String code;

    @Column(name = "description")
    private String description;

    @Column(name = "directory_id")
    private Long directoryId;

    @Column(name = "metric_type")
    private String metricType;

    @Column(name = "status")
    private String status;

    @Column(name = "fact_model_id")
    private Long factModelId;

    @Column(name = "filter_config")
    private String filterConfig;

    @Column(name = "unit")
    private String unit;

    @Column(name = "data_type")
    private String dataType;

    @Column(name = "is_additive")
    private Boolean isAdditive;

    @Column(name = "formula")
    private String formula;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "update_time")
    private ZonedDateTime updateTime;

    public Long getId() {
        return this.id;
    }

    public Metric id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public Metric tenantId(String tenantId) {
        this.setTenantId(tenantId);
        return this;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return this.name;
    }

    public Metric name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return this.code;
    }

    public Metric code(String code) {
        this.setCode(code);
        return this;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return this.description;
    }

    public Metric description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getDirectoryId() {
        return this.directoryId;
    }

    public Metric directoryId(Long directoryId) {
        this.setDirectoryId(directoryId);
        return this;
    }

    public void setDirectoryId(Long directoryId) {
        this.directoryId = directoryId;
    }

    public String getMetricType() {
        return this.metricType;
    }

    public Metric metricType(String metricType) {
        this.setMetricType(metricType);
        return this;
    }

    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }

    public String getStatus() {
        return this.status;
    }

    public Metric status(String status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getFactModelId() {
        return this.factModelId;
    }

    public Metric factModelId(Long factModelId) {
        this.setFactModelId(factModelId);
        return this;
    }

    public void setFactModelId(Long factModelId) {
        this.factModelId = factModelId;
    }

    public String getFilterConfig() {
        return this.filterConfig;
    }

    public Metric filterConfig(String filterConfig) {
        this.setFilterConfig(filterConfig);
        return this;
    }

    public void setFilterConfig(String filterConfig) {
        this.filterConfig = filterConfig;
    }

    public String getUnit() {
        return this.unit;
    }

    public Metric unit(String unit) {
        this.setUnit(unit);
        return this;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getDataType() {
        return this.dataType;
    }

    public Metric dataType(String dataType) {
        this.setDataType(dataType);
        return this;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public Boolean getIsAdditive() {
        return this.isAdditive;
    }

    public Metric isAdditive(Boolean isAdditive) {
        this.setIsAdditive(isAdditive);
        return this;
    }

    public void setIsAdditive(Boolean isAdditive) {
        this.isAdditive = isAdditive;
    }

    public String getFormula() {
        return this.formula;
    }

    public Metric formula(String formula) {
        this.setFormula(formula);
        return this;
    }

    public void setFormula(String formula) {
        this.formula = formula;
    }

    public ZonedDateTime getCreateTime() {
        return this.createTime;
    }

    public Metric createTime(ZonedDateTime createTime) {
        this.setCreateTime(createTime);
        return this;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public ZonedDateTime getUpdateTime() {
        return this.updateTime;
    }

    public Metric updateTime(ZonedDateTime updateTime) {
        this.setUpdateTime(updateTime);
        return this;
    }

    public void setUpdateTime(ZonedDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Metric)) {
            return false;
        }
        return getId() != null && getId().equals(((Metric) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Metric{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", code='" + getCode() + "'" +
            ", metricType='" + getMetricType() + "'" +
            ", status='" + getStatus() + "'" +
            ", factModelId=" + getFactModelId() +
            ", dataType='" + getDataType() + "'" +
            ", isAdditive=" + getIsAdditive() +
            ", unit='" + getUnit() + "'" +
            ", tenantId='" + getTenantId() + "'" +
            "}";
    }
}
