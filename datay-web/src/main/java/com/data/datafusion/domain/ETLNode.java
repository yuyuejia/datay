package com.data.datafusion.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * A ETLNode.
 */
@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "dp_etl_node")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ETLNode implements Serializable, TenantAware {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "task_id")
    private String taskId;

    @Column(name = "label")
    private String label;

    @Column(name = "code")
    private String code;

    @Column(name = "jhi_desc")
    private String desc;

    @Column(name = "type")
    private String type;

    @Column(name = "config")
    private String config;

    @Column(name = "x_axis")
    private String xAxis;

    @Column(name = "y_axis")
    private String yAxis;

    @Column(name = "status")
    private String status;

    @Column(name = "update_time")
    private ZonedDateTime updateTime;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "dr")
    private Integer dr;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public ETLNode id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskId() {
        return this.taskId;
    }

    public ETLNode taskId(String taskId) {
        this.setTaskId(taskId);
        return this;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getLabel() {
        return this.label;
    }

    public ETLNode label(String label) {
        this.setLabel(label);
        return this;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getCode() {
        return this.code;
    }

    public ETLNode code(String code) {
        this.setCode(code);
        return this;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDesc() {
        return this.desc;
    }

    public ETLNode desc(String desc) {
        this.setDesc(desc);
        return this;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getType() {
        return this.type;
    }

    public ETLNode type(String type) {
        this.setType(type);
        return this;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getConfig() {
        return this.config;
    }

    public ETLNode config(String config) {
        this.setConfig(config);
        return this;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public String getxAxis() {
        return this.xAxis;
    }

    public ETLNode xAxis(String xAxis) {
        this.setxAxis(xAxis);
        return this;
    }

    public void setxAxis(String xAxis) {
        this.xAxis = xAxis;
    }

    public String getyAxis() {
        return this.yAxis;
    }

    public ETLNode yAxis(String yAxis) {
        this.setyAxis(yAxis);
        return this;
    }

    public void setyAxis(String yAxis) {
        this.yAxis = yAxis;
    }

    public String getStatus() {
        return this.status;
    }

    public ETLNode status(String status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ZonedDateTime getUpdateTime() {
        return this.updateTime;
    }

    public ETLNode updateTime(ZonedDateTime updateTime) {
        this.setUpdateTime(updateTime);
        return this;
    }

    public void setUpdateTime(ZonedDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public ZonedDateTime getCreateTime() {
        return this.createTime;
    }

    public ETLNode createTime(ZonedDateTime createTime) {
        this.setCreateTime(createTime);
        return this;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public ETLNode tenantId(String tenantId) {
        this.setTenantId(tenantId);
        return this;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Integer getDr() {
        return this.dr;
    }

    public ETLNode dr(Integer dr) {
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
        if (!(o instanceof ETLNode)) {
            return false;
        }
        return getId() != null && getId().equals(((ETLNode) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ETLNode{" +
            "id=" + getId() +
            ", taskId='" + getTaskId() + "'" +
            ", label='" + getLabel() + "'" +
            ", code='" + getCode() + "'" +
            ", desc='" + getDesc() + "'" +
            ", type='" + getType() + "'" +
            ", config='" + getConfig() + "'" +
            ", xAxis='" + getxAxis() + "'" +
            ", yAxis='" + getyAxis() + "'" +
            ", status='" + getStatus() + "'" +
            ", updateTime='" + getUpdateTime() + "'" +
            ", createTime='" + getCreateTime() + "'" +
            ", tenantId='" + getTenantId() + "'" +
            ", dr=" + getDr() +
            "}";
    }
}