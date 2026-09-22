package com.notemind.interfaces.auth.vo;

/**
 * 修改密码请求体。
 */
public class ChangePasswordRequest {
    private String oldPassword;
    private String newPassword;

    /** 获取旧密码 */
    public String getOldPassword() {
        return oldPassword;
    }

    /** 设置旧密码 */
    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    /** 获取新密码 */
    public String getNewPassword() {
        return newPassword;
    }

    /** 设置新密码 */
    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
