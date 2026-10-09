package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据应用资产包 DTO。
 *
 * <p>{@link #content} 体积较大，列表接口不返回，仅在详情/导出时下发。
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AppPackageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private String tenantId;

    private String name;

    private String code;

    private String description;

    /** 业务场景分类。 */
    private String category;

    private String version;

    /** SYSTEM 系统预制 / TENANT 租户导出。 */
    private String packageType;

    private String status;

    /** 资产构成摘要。 */
    private Map<String, Integer> itemSummary = new LinkedHashMap<>();

    /** 资产包内容 JSON，仅详情/导出接口返回。 */
    private String content;

    private Long contentSize;

    private String createUser;

    private ZonedDateTime createTime;

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

    public Map<String, Integer> getItemSummary() {
        return itemSummary;
    }

    public void setItemSummary(Map<String, Integer> itemSummary) {
        this.itemSummary = itemSummary == null ? new LinkedHashMap<>() : itemSummary;
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
}
