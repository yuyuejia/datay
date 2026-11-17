package com.data.datafusion.domain;

import com.data.datafusion.util.entity.UuidV7Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * 用户访问令牌：一个用户可以创建多个命名令牌，用于不同客户端或业务场景。
 *
 * <p>数据库中只保存令牌的 BCrypt 哈希和 SHA-256 查找哈希；明文令牌仅在创建或重新生成时返回一次。</p>
 */
@Entity
@Table(
    name = "dp_user_access_token",
    uniqueConstraints = {
        @UniqueConstraint(name = "ux_user_access_token_lookup_hash", columnNames = "lookup_hash"),
        @UniqueConstraint(name = "ux_user_access_token_user_name", columnNames = { "user_id", "name" }),
    }
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class UserAccessToken implements Serializable {

    /** 令牌状态：启用。 */
    public static final String STATUS_ENABLED = "ENABLED";

    /** 令牌状态：停用。 */
    public static final String STATUS_DISABLED = "DISABLED";

    private static final long serialVersionUID = 1L;

    @Id
    @UuidV7Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    /** SHA-256(token)，用于快速定位令牌记录。 */
    @Column(name = "lookup_hash", nullable = false, length = 64)
    private String lookupHash;

    /** BCrypt(token)，用于最终校验。 */
    @Column(name = "token_hash", nullable = false, length = 60)
    private String tokenHash;

    /** 令牌末 4 位，仅用于页面展示。 */
    @Column(name = "token_hint", length = 8)
    private String tokenHint;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "last_used_time")
    private ZonedDateTime lastUsedTime;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public String getLookupHash() {
        return lookupHash;
    }

    public void setLookupHash(String lookupHash) {
        this.lookupHash = lookupHash;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getTokenHint() {
        return tokenHint;
    }

    public void setTokenHint(String tokenHint) {
        this.tokenHint = tokenHint;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ZonedDateTime getLastUsedTime() {
        return lastUsedTime;
    }

    public void setLastUsedTime(ZonedDateTime lastUsedTime) {
        this.lastUsedTime = lastUsedTime;
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
