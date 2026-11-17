package com.data.datafusion.service.dto;

import java.io.Serializable;

/**
 * 创建访问令牌的请求体。
 */
public class CreateUserAccessTokenRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;

    private String description;

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
}
