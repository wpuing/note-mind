package com.notemind.interfaces.chat.vo;

/**
 * 创建聊天会话请求体。
 */
public class ChatSessionCreateRequest {
    private String appId;
    /** ADMIN / WEB */
    private String clientSource;

    /** 获取应用 ID */
    public String getAppId() { return appId; }
    /** 设置应用 ID */
    public void setAppId(String appId) { this.appId = appId; }
    /** 获取client Source */
    public String getClientSource() { return clientSource; }
    /** 设置client Source */
    public void setClientSource(String clientSource) { this.clientSource = clientSource; }
}
