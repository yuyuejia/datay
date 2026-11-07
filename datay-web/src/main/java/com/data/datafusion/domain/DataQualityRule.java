package com.data.datafusion.domain;

import com.data.datafusion.util.entity.UuidV7Id;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import org.hibernate.annotations.Filter;

/**
 * 数据质量规则定义。
 *
 * <p>系统预置规则的 {@code tenantId} 为空，所有租户可见；自定义规则按租户隔离。</p>
 */
@Entity
@Filter(name = "tenantFilter", condition = "(tenant_id = :tenantId OR tenant_id IS NULL)")
@Table(name = "dp_data_quality_rule")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DataQualityRule implements Serializable, TenantAware {

    private static final long serialVersionUID = 1L;

    @Id
    @UuidV7Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(name = "rule_code", nullable = false, length = 100)
    private String ruleCode;

    @Column(name = "rule_level", nullable = false, length = 20)
    private String ruleLevel;

    @Column(name = "rule_type", nullable = false, length = 50)
    private String ruleType;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "default_config")
    private String defaultConfig;

    @Column(name = "severity", length = 20)
    private String severity = "ERROR";

    @Column(name = "enabled")
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "builtin")
    private Boolean builtin = Boolean.FALSE;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "update_time")
    private ZonedDateTime updateTime;

    @Column(name = "creater")
    private String creater;

    @Column(name = "tenant_id")
    private String tenantId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleLevel() {
        return ruleLevel;
    }

    public void setRuleLevel(String ruleLevel) {
        this.ruleLevel = ruleLevel;
    }

    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDefaultConfig() {
        return defaultConfig;
    }

    public void setDefaultConfig(String defaultConfig) {
        this.defaultConfig = defaultConfig;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getBuiltin() {
        return builtin;
    }

    public void setBuiltin(Boolean builtin) {
        this.builtin = builtin;
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

    public String getCreater() {
        return creater;
    }

    public void setCreater(String creater) {
        this.creater = creater;
    }

    @Override
    public String getTenantId() {
        return tenantId;
    }

    @Override
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataQualityRule)) {
            return false;
        }
        return getId() != null && getId().equals(((DataQualityRule) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "DataQualityRule{" +
            "id=" + getId() +
            ", ruleName='" + getRuleName() + "'" +
            ", ruleCode='" + getRuleCode() + "'" +
            ", ruleLevel='" + getRuleLevel() + "'" +
            ", ruleType='" + getRuleType() + "'" +
            ", tenantId='" + getTenantId() + "'" +
            "}";
    }
}
