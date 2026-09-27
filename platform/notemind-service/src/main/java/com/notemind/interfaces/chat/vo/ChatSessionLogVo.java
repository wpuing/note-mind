package com.notemind.interfaces.chat.vo;

/**
 * 管理端会话日志视图对象。
 */
public class ChatSessionLogVo {
    private String id;
    private String appId;
    private String appName;
    private String title;
    private Integer messageCount;
    private String clientSource;
    private Integer likeCount;
    private Integer dislikeCount;
    private String createTime;

    /** 获取主键 ID */
    public String getId() { return id; }
    /** 设置主键 ID */
    public void setId(String id) { this.id = id; }
    /** 获取应用 ID */
    public String getAppId() { return appId; }
    /** 设置应用 ID */
    public void setAppId(String appId) { this.appId = appId; }
    /** 获取app Name */
    public String getAppName() { return appName; }
    /** 设置app Name */
    public void setAppName(String appName) { this.appName = appName; }
    /** 获取标题 */
    public String getTitle() { return title; }
    /** 设置标题 */
    public void setTitle(String title) { this.title = title; }
    /** 获取message Count */
    public Integer getMessageCount() { return messageCount; }
    /** 设置message Count */
    public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }
    /** 获取client Source */
    public String getClientSource() { return clientSource; }
    /** 设置client Source */
    public void setClientSource(String clientSource) { this.clientSource = clientSource; }
    /** 获取like Count */
    public Integer getLikeCount() { return likeCount; }
    /** 设置like Count */
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
    /** 获取dislike Count */
    public Integer getDislikeCount() { return dislikeCount; }
    /** 设置dislike Count */
    public void setDislikeCount(Integer dislikeCount) { this.dislikeCount = dislikeCount; }
    /** 获取创建时间 */
    public String getCreateTime() { return createTime; }
    /** 设置创建时间 */
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
