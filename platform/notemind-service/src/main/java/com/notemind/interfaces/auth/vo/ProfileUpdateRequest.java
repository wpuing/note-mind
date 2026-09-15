package com.notemind.interfaces.auth.vo;

/**
 * 个人资料更新请求体。
 */
public class ProfileUpdateRequest {
    private String nickname;
    private String avatarUrl;

    /** 获取昵称 */
    public String getNickname() {
        return nickname;
    }

    /** 设置昵称 */
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    /** 获取avatar Url */
    public String getAvatarUrl() {
        return avatarUrl;
    }

    /** 设置avatar Url */
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
