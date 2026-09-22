package com.notemind.interfaces.auth.vo;

/**
 * 当前用户个人资料视图对象。
 */
public class UserProfileVo {
    private String id;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String role;
    private Integer status;
    private String createTime;
    private String updateTime;

    /** 获取主键 ID */
    public String getId() {
        return id;
    }

    /** 设置主键 ID */
    public void setId(String id) {
        this.id = id;
    }

    /** 获取用户名 */
    public String getUsername() {
        return username;
    }

    /** 设置用户名 */
    public void setUsername(String username) {
        this.username = username;
    }

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

    /** 获取角色 */
    public String getRole() {
        return role;
    }

    /** 设置角色 */
    public void setRole(String role) {
        this.role = role;
    }

    /** 获取状态 */
    public Integer getStatus() {
        return status;
    }

    /** 设置状态 */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /** 获取创建时间 */
    public String getCreateTime() {
        return createTime;
    }

    /** 设置创建时间 */
    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    /** 获取更新时间 */
    public String getUpdateTime() {
        return updateTime;
    }

    /** 设置更新时间 */
    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }
}
