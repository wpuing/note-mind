package com.notemind.interfaces.auth.vo;

/**
 * 登录请求体（用户名、密码）。
 */
public class LoginRequest {
    private String username;
    private String password;

    /** 获取用户名 */
    public String getUsername() {
        return username;
    }

    /** 设置用户名 */
    public void setUsername(String username) {
        this.username = username;
    }

    /** 获取密码 */
    public String getPassword() {
        return password;
    }

    /** 设置密码 */
    public void setPassword(String password) {
        this.password = password;
    }
}
