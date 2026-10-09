package com.data.datafusion.domain;

import com.data.datafusion.util.entity.UuidV7Id;

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
    @UuidV7Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "df_group")
    private String dfGroup;

    @Column(name = "df_key")
    private String dfKey;

    @Column(name = "df_value")
    private String dfValue;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "tenant_id")
    private String tenantId;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public ServiceConfig id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
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

    public String getTenantId() {
        return this.tenantId;
    }

    public ServiceConfig tenantId(String tenantId) {
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
            ", tenantId='" + getTenantId() + "'" +
            "}";
    }
}