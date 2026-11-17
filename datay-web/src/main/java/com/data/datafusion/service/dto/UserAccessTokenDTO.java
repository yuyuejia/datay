package com.data.datafusion.service.dto;

import com.data.datafusion.domain.UserAccessToken;
import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * 用户访问令牌的展示信息，不包含明文令牌和哈希。
 */
public class UserAccessTokenDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String description;
    private String tokenHint;
    private String status;
    private ZonedDateTime lastUsedTime;
    private ZonedDateTime createTime;

    public static UserAccessTokenDTO from(UserAccessToken token) {
        UserAccessTokenDTO dto = new UserAccessTokenDTO();
        dto.setId(token.getId());
        dto.setName(token.getName());
        dto.setDescription(token.getDescription());
        dto.setTokenHint(token.getTokenHint());
        dto.setStatus(token.getStatus());
        dto.setLastUsedTime(token.getLastUsedTime());
        dto.setCreateTime(token.getCreateTime());
        return dto;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
}
