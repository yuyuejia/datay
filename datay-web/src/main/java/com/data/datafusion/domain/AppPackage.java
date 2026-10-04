package com.data.datafusion.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * 数据应用资产包。
 *
 * <p>资产包是一份可跨租户复用的数据应用定义快照，内容包含数据源、数据模型（含字段）、
 * 指标、ETL 任务、SQL 任务与编排任务，序列化为 JSON 存放在 {@link #content} 中。
 * 包内所有跨对象引用都使用「包内逻辑 ID」，因此可以在任意租户中初始化并重新生成真实 ID。
 *
 * <p>资产包分两类：
 * <ul>
 *   <li>{@link #TYPE_SYSTEM} 系统预制包：随程序发布，{@code tenantId} 为空，对所有租户可见；</li>
 *   <li>{@link #TYPE_TENANT} 租户导出包：属于某个租户，仅该租户可见。</li>
 * </ul>
 *
 * <p>注意：本实体不声明 {@code tenantFilter}，因为系统预制包需要跨租户可见，
 * 租户隔离由 {@link com.data.datafusion.repository.AppPackageRepository} 的查询条件显式保证。
 */
@Entity
@Table(name = "dp_app_package")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AppPackage implements Serializable, TenantAware {

    /** 系统预制资产包。 */
    public static final String TYPE_SYSTEM = "SYSTEM";

    /** 租户导出的资产包。 */
    public static final String TYPE_TENANT = "TENANT";

    /** 状态：正常可用。 */
    public static final String STATUS_ENABLED = "ENABLED";

    /** 状态：已停用。 */
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

    /** 业务场景分类，如「零售电商」「财务分析」。 */
    @Column(name = "category")
    private String category;

    @Column(name = "version")
    private String version;

    @Column(name = "package_type")
    private String packageType;

    @Column(name = "status")
    private String status;

    /** 资产构成摘要 JSON，如 {"dataSources":1,"models":8,...}。 */
    @Column(name = "item_summary")
    private String itemSummary;

    /** 资产包完整内容 JSON。 */
    @Column(name = "content")
    private String content;

    @Column(name = "content_size")
    private Long contentSize;

    @Column(name = "create_user")
    private String createUser;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "update_time")
    private ZonedDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getPackageType() {
        return packageType;
    }

    public void setPackageType(String packageType) {
        this.packageType = packageType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getItemSummary() {
        return itemSummary;
    }

    public void setItemSummary(String itemSummary) {
        this.itemSummary = itemSummary;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getContentSize() {
        return contentSize;
    }

    public void setContentSize(Long contentSize) {
        this.contentSize = contentSize;
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

    public ZonedDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(ZonedDateTime updateTime) {
        this.updateTime = updateTime;
    }

    /** 是否为系统预制包。 */
    public boolean isSystemPackage() {
        return TYPE_SYSTEM.equalsIgnoreCase(packageType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AppPackage)) {
            return false;
        }
        return getId() != null && getId().equals(((AppPackage) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "AppPackage{" +
            "id=" +
            getId() +
            ", name='" +
            getName() +
            "'" +
            ", code='" +
            getCode() +
            "'" +
            ", packageType='" +
            getPackageType() +
            "'" +
            ", version='" +
            getVersion() +
            "'" +
            "}"
        );
    }
}
