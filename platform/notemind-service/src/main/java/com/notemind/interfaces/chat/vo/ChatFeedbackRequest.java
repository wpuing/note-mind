package com.notemind.interfaces.chat.vo;

/**
 * 消息赞踩反馈请求体。
 */
public class ChatFeedbackRequest {
    private String feedback;
    private String reason;

    /** 获取反馈类型 */
    public String getFeedback() { return feedback; }
    /** 设置反馈类型 */
    public void setFeedback(String feedback) { this.feedback = feedback; }
    /** 获取reason */
    public String getReason() { return reason; }
    /** 设置reason */
    public void setReason(String reason) { this.reason = reason; }
}
