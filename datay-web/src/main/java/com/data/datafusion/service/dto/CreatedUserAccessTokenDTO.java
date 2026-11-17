package com.data.datafusion.service.dto;

import java.io.Serializable;

/**
 * 创建或重新生成访问令牌后的返回体，明文令牌仅返回这一次。
 */
public class CreatedUserAccessTokenDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;

    private UserAccessTokenDTO accessToken;

    public CreatedUserAccessTokenDTO(String token, UserAccessTokenDTO accessToken) {
        this.token = token;
        this.accessToken = accessToken;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserAccessTokenDTO getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(UserAccessTokenDTO accessToken) {
        this.accessToken = accessToken;
    }
}
