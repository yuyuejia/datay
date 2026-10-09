package com.data.datafusion.domain;

import com.data.datafusion.util.entity.UuidV7Id;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * 数据应用初始化记录：某租户基于某个资产包执行一次初始化后留下的痕迹，
 * 保存初始化结果与「包内逻辑 ID → 本租户真实 ID」的映射，便于排查与回溯。
 */
@Entity
@Table(name = "dp_app_package_instance")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AppPackageInstance implements Serializable, TenantAware {

    /** 初始化成功。 */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /** 初始化失败。 */
    public static final String STATUS_FAILED = "FAILED";

    private static final long serialVersionUID = 1L;

    @Id
    @UuidV7Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "package_id", length = 36)
    private String packageId;

    @Column(name = "package_code")
    private String packageCode;

    @Column(name = "package_name")
    private String packageName;

    @Column(name = "status")
    private String status;

    @Column(name = "message")
    private String message;

    /** 初始化结果与 ID 映射 JSON。 */
    @Column(name = "id_mapping")
    private String idMapping;

    @Column(name = "create_user")
    private String createUser;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

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

    public String getPackageId() {
        return packageId;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
    }

    public String getPackageCode() {
        return packageCode;
    }

    public void setPackageCode(String packageCode) {
        this.packageCode = packageCode;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getIdMapping() {
        return idMapping;
    }

    public void setIdMapping(String idMapping) {
        this.idMapping = idMapping;
    }

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(String createUser) {
        this.createUser = createUser;
    }

    public ZonedDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AppPackageInstance)) {
            return false;
        }
        return getId() != null && getId().equals(((AppPackageInstance) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
