package com.notemind.interfaces.chat.vo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 聊天消息视图对象。
 */
public class ChatMessageVo {
    private String id;
    private String sessionId;
    private String role;
    private String content;
    private String agentRunId;
    private String feedback;
    private String createTime;
    private List<Map<String, Object>> sources = new ArrayList<>();
    private String conclusionLabel;
    private Integer stepCount;
    private Integer retrievalRounds;
    private Integer rewriteRounds;

    /** 获取主键 ID */
    public String getId() { return id; }
    /** 设置主键 ID */
    public void setId(String id) { this.id = id; }
    /** 获取会话 ID */
    public String getSessionId() { return sessionId; }
    /** 设置会话 ID */
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    /** 获取角色 */
    public String getRole() { return role; }
    /** 设置角色 */
    public void setRole(String role) { this.role = role; }
    /** 获取内容 */
    public String getContent() { return content; }
    /** 设置内容 */
    public void setContent(String content) { this.content = content; }
    /** 获取Agent 运行 ID */
    public String getAgentRunId() { return agentRunId; }
    /** 设置Agent 运行 ID */
    public void setAgentRunId(String agentRunId) { this.agentRunId = agentRunId; }
    /** 获取反馈类型 */
    public String getFeedback() { return feedback; }
    /** 设置反馈类型 */
    public void setFeedback(String feedback) { this.feedback = feedback; }
    /** 获取创建时间 */
    public String getCreateTime() { return createTime; }
    /** 设置创建时间 */
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    /** getSources 方法 */
    public List<Map<String, Object>> getSources() { return sources; }
    /** 设置引用来源 */
    public void setSources(List<Map<String, Object>> sources) {
        this.sources = sources == null ? new ArrayList<>() : sources;
    }
    /** 获取结论标签 */
    public String getConclusionLabel() { return conclusionLabel; }
    /** 设置结论标签 */
    public void setConclusionLabel(String conclusionLabel) { this.conclusionLabel = conclusionLabel; }
    /** 获取步骤数 */
    public Integer getStepCount() { return stepCount; }
    /** 设置步骤数 */
    public void setStepCount(Integer stepCount) { this.stepCount = stepCount; }
    /** 获取检索轮次 */
    public Integer getRetrievalRounds() { return retrievalRounds; }
    /** 设置检索轮次 */
    public void setRetrievalRounds(Integer retrievalRounds) { this.retrievalRounds = retrievalRounds; }
    /** 获取改写轮次 */
    public Integer getRewriteRounds() { return rewriteRounds; }
    /** 设置改写轮次 */
    public void setRewriteRounds(Integer rewriteRounds) { this.rewriteRounds = rewriteRounds; }
}
