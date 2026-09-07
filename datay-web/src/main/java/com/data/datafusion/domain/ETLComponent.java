package com.data.datafusion.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * A ETLComponent.
 */
@Entity
@Table(name = "dp_etl_component")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ETLComponent implements Serializable, TenantAware {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "code")
    private String code;

    @Column(name = "jhi_desc")
    private String desc;

    @Column(name = "jhi_group")
    private String group;

    @Column(name = "type")
    private String type;

    @Column(name = "config")
    private String config;

    @Column(name = "status")
    private String status;

    @Column(name = "update_time")
    private ZonedDateTime updateTime;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "creater")
    private String creater;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "dr")
    private Integer dr;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public ETLComponent id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public ETLComponent name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return this.code;
    }

    public ETLComponent code(String code) {
        this.setCode(code);
        return this;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDesc() {
        return this.desc;
    }

    public ETLComponent desc(String desc) {
        this.setDesc(desc);
        return this;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getGroup() {
        return this.group;
    }

    public ETLComponent group(String group) {
        this.setGroup(group);
        return this;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getType() {
        return this.type;
    }

    public ETLComponent type(String type) {
        this.setType(type);
        return this;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getConfig() {
        return this.config;
    }

    public ETLComponent config(String config) {
        this.setConfig(config);
        return this;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public String getStatus() {
        return this.status;
    }

    public ETLComponent status(String status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ZonedDateTime getUpdateTime() {
        return this.updateTime;
    }

    public ETLComponent updateTime(ZonedDateTime updateTime) {
        this.setUpdateTime(updateTime);
        return this;
    }

    public void setUpdateTime(ZonedDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public ZonedDateTime getCreateTime() {
        return this.createTime;
    }

    public ETLComponent createTime(ZonedDateTime createTime) {
        this.setCreateTime(createTime);
        return this;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public String getCreater() {
        return this.creater;
    }

    public ETLComponent creater(String creater) {
        this.setCreater(creater);
        return this;
    }

    public void setCreater(String creater) {
        this.creater = creater;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public ETLComponent tenantId(String tenantId) {
        this.setTenantId(tenantId);
        return this;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Integer getDr() {
        return this.dr;
    }

    public ETLComponent dr(Integer dr) {
        this.setDr(dr);
        return this;
    }

    public void setDr(Integer dr) {
        this.dr = dr;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ETLComponent)) {
            return false;
        }
        return getId() != null && getId().equals(((ETLComponent) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ETLComponent{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", code='" + getCode() + "'" +
            ", desc='" + getDesc() + "'" +
            ", group='" + getGroup() + "'" +
            ", type='" + getType() + "'" +
            ", config='" + getConfig() + "'" +
            ", status='" + getStatus() + "'" +
            ", updateTime='" + getUpdateTime() + "'" +
            ", createTime='" + getCreateTime() + "'" +
            ", creater='" + getCreater() + "'" +
            ", tenantId='" + getTenantId() + "'" +
            ", dr=" + getDr() +
            "}";
    }
}