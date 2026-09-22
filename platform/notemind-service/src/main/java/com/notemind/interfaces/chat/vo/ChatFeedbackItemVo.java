package com.notemind.interfaces.chat.vo;

/**
 * 对话反馈列表项视图对象。
 */
public class ChatFeedbackItemVo {
    private String id;
    private String sessionId;
    private String feedback;
    private String question;
    private String answer;
    private String feedbackReason;
    private Integer settled;
    private String createTime;
    private String appName;

    /** 获取主键 ID */
    public String getId() { return id; }
    /** 设置主键 ID */
    public void setId(String id) { this.id = id; }
    /** 获取会话 ID */
    public String getSessionId() { return sessionId; }
    /** 设置会话 ID */
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    /** 获取反馈类型 */
    public String getFeedback() { return feedback; }
    /** 设置反馈类型 */
    public void setFeedback(String feedback) { this.feedback = feedback; }
    /** 获取问题 */
    public String getQuestion() { return question; }
    /** 设置问题 */
    public void setQuestion(String question) { this.question = question; }
    /** 获取答案 */
    public String getAnswer() { return answer; }
    /** 设置答案 */
    public void setAnswer(String answer) { this.answer = answer; }
    /** 获取feedback Reason */
    public String getFeedbackReason() { return feedbackReason; }
    /** 设置feedback Reason */
    public void setFeedbackReason(String feedbackReason) { this.feedbackReason = feedbackReason; }
    /** 获取是否已沉淀 */
    public Integer getSettled() { return settled; }
    /** 设置是否已沉淀 */
    public void setSettled(Integer settled) { this.settled = settled; }
    /** 获取创建时间 */
    public String getCreateTime() { return createTime; }
    /** 设置创建时间 */
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    /** 获取app Name */
    public String getAppName() { return appName; }
    /** 设置app Name */
    public void setAppName(String appName) { this.appName = appName; }
}
