package com.data.datafusion.domain;

import com.data.datafusion.util.entity.UuidV7Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import java.time.ZonedDateTime;
import org.hibernate.annotations.Filter;

/**
 * 维度角色：同一张维度表在事实表中扮演的不同业务含义。
 *
 * <p>例如日期维度可以维护 {@code ORDER_DATE}（下单日期）、{@code PAY_DATE}（支付日期）、
 * {@code SHIP_DATE}（发货日期）三个角色。事实表字段通过 {@code model_field.dimension_role}
 * 引用角色编码。</p>
 */
@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(
    name = "dp_dimension_role",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "ux_dimension_role_model_code",
            columnNames = { "dimension_model_id", "code" }
        ),
    }
)
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DimensionRole implements Serializable, TenantAware {

    private static final long serialVersionUID = 1L;

    @Id
    @UuidV7Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "tenant_id")
    private String tenantId;

    /** 所属维度模型 id。 */
    @Column(name = "dimension_model_id", length = 36, nullable = false)
    private String dimensionModelId;

    /** 角色编码，如 ORDER_DATE；创建后不可修改。 */
    @Column(name = "code", length = 64, nullable = false)
    private String code;

    /** 角色名称，如 下单日期。 */
    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "update_time")
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

    public String getDimensionModelId() {
        return dimensionModelId;
    }

    public void setDimensionModelId(String dimensionModelId) {
        this.dimensionModelId = dimensionModelId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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
        if (!(o instanceof DimensionRole)) {
            return false;
        }
        return getId() != null && getId().equals(((DimensionRole) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "DimensionRole{" +
            "id=" +
            getId() +
            ", dimensionModelId=" +
            getDimensionModelId() +
            ", code='" +
            getCode() +
            "', name='" +
            getName() +
            "'}"
        );
    }
}
