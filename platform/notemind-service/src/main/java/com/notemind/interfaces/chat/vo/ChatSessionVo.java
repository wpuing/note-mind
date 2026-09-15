package com.notemind.interfaces.chat.vo;

/**
 * 聊天会话视图对象。
 */
public class ChatSessionVo {
    private String id;
    private String appId;
    private String title;
    private String createTime;
    private String updateTime;

    /** 获取主键 ID */
    public String getId() { return id; }
    /** 设置主键 ID */
    public void setId(String id) { this.id = id; }
    /** 获取应用 ID */
    public String getAppId() { return appId; }
    /** 设置应用 ID */
    public void setAppId(String appId) { this.appId = appId; }
    /** 获取标题 */
    public String getTitle() { return title; }
    /** 设置标题 */
    public void setTitle(String title) { this.title = title; }
    /** 获取创建时间 */
    public String getCreateTime() { return createTime; }
    /** 设置创建时间 */
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    /** 获取更新时间 */
    public String getUpdateTime() { return updateTime; }
    /** 设置更新时间 */
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
}
