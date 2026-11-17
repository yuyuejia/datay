package com.data.datafusion.service.dto;

import java.io.Serializable;

/**
 * 修改访问令牌名称、描述或状态的请求体；字段为 {@code null} 表示不修改。
 */
public class UpdateUserAccessTokenRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;

    private String description;

    private String status;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
