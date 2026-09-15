package com.notemind.interfaces.chat.vo;

/**
 * SSE 流式问答请求体。
 */
public class ChatStreamRequest {
    private String appId;
    private String sessionId;
    private String question;

    /** 获取应用 ID */
    public String getAppId() { return appId; }
    /** 设置应用 ID */
    public void setAppId(String appId) { this.appId = appId; }
    /** 获取会话 ID */
    public String getSessionId() { return sessionId; }
    /** 设置会话 ID */
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    /** 获取问题 */
    public String getQuestion() { return question; }
    /** 设置问题 */
    public void setQuestion(String question) { this.question = question; }
}
