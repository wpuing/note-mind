package com.notemind.interfaces.auth.vo;

import java.util.Map;

/**
 * 登录响应体（Token 与用户信息）。
 */
public class LoginResponse {
    private String accessToken;
    private String tokenType = "Bearer";
    private long expiresIn;
    private Map<String, Object> user;

    /** 获取access Token */
    public String getAccessToken() {
        return accessToken;
    }

    /** 设置access Token */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    /** 获取token Type */
    public String getTokenType() {
        return tokenType;
    }

    /** 设置token Type */
    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    /** 获取expires In */
    public long getExpiresIn() {
        return expiresIn;
    }

    /** 设置expires In */
    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    /** getUser 方法 */
    public Map<String, Object> getUser() {
        return user;
    }

    /** 设置user */
    public void setUser(Map<String, Object> user) {
        this.user = user;
    }
}
