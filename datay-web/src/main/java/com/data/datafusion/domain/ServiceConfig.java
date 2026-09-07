package com.data.datafusion.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * A ServiceConfig.
 */
@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "dp_service_config")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ServiceConfig implements Serializable, TenantAware {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "df_group")
    private String dfGroup;

    @Column(name = "df_key")
    private String dfKey;

    @Column(name = "df_value")
    private String dfValue;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "ytenant_id")
    private String ytenantId;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public ServiceConfig id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDfGroup() {
        return this.dfGroup;
    }

    public ServiceConfig dfGroup(String dfGroup) {
        this.setDfGroup(dfGroup);
        return this;
    }

    public void setDfGroup(String dfGroup) {
        this.dfGroup = dfGroup;
    }

    public String getDfKey() {
        return this.dfKey;
    }

    public ServiceConfig dfKey(String dfKey) {
        this.setDfKey(dfKey);
        return this;
    }

    public void setDfKey(String dfKey) {
        this.dfKey = dfKey;
    }

    public String getDfValue() {
        return this.dfValue;
    }

    public ServiceConfig dfValue(String dfValue) {
        this.setDfValue(dfValue);
        return this;
    }

    public void setDfValue(String dfValue) {
        this.dfValue = dfValue;
    }

    public ZonedDateTime getCreateTime() {
        return this.createTime;
    }

    public ServiceConfig createTime(ZonedDateTime createTime) {
        this.setCreateTime(createTime);
        return this;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public String getYtenantId() {
        return this.ytenantId;
    }

    public ServiceConfig ytenantId(String ytenantId) {
        this.setYtenantId(ytenantId);
        return this;
    }

    public void setYtenantId(String ytenantId) {
        this.ytenantId = ytenantId;
    }

    @Override
    public String getTenantId() {
        return this.ytenantId;
    }

    @Override
    public void setTenantId(String tenantId) {
        this.ytenantId = tenantId;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServiceConfig)) {
            return false;
        }
        return getId() != null && getId().equals(((ServiceConfig) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ServiceConfig{" +
            "id=" + getId() +
            ", dfGroup='" + getDfGroup() + "'" +
            ", dfKey='" + getDfKey() + "'" +
            ", dfValue='" + getDfValue() + "'" +
            ", createTime='" + getCreateTime() + "'" +
            ", ytenantId='" + getYtenantId() + "'" +
            "}";
    }
}