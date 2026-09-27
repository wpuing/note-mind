package com.notemind.interfaces.app.vo;

/**
 * 问答应用保存请求体。
 */
public class QaAppSaveRequest {
    private String name;
    private String description;
    private String knowledgeBaseId;
    private String retrievalStrategyId;
    private String chatModelId;
    private String answerPromptId;
    private Integer enableAgentic;
    private Integer historyLimit;
    private String fallbackReply;
    private Integer enabled;

    /** 获取名称 */
    public String getName() { return name; }
    /** 设置名称 */
    public void setName(String name) { this.name = name; }
    /** 获取描述 */
    public String getDescription() { return description; }
    /** 设置描述 */
    public void setDescription(String description) { this.description = description; }
    /** 获取知识库 ID */
    public String getKnowledgeBaseId() { return knowledgeBaseId; }
    /** 设置知识库 ID */
    public void setKnowledgeBaseId(String knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    /** 获取检索策略 ID */
    public String getRetrievalStrategyId() { return retrievalStrategyId; }
    /** 设置检索策略 ID */
    public void setRetrievalStrategyId(String retrievalStrategyId) { this.retrievalStrategyId = retrievalStrategyId; }
    /** 获取chat Model Id */
    public String getChatModelId() { return chatModelId; }
    /** 设置chat Model Id */
    public void setChatModelId(String chatModelId) { this.chatModelId = chatModelId; }
    /** 获取answer Prompt Id */
    public String getAnswerPromptId() { return answerPromptId; }
    /** 设置answer Prompt Id */
    public void setAnswerPromptId(String answerPromptId) { this.answerPromptId = answerPromptId; }
    /** 获取enable Agentic */
    public Integer getEnableAgentic() { return enableAgentic; }
    /** 设置enable Agentic */
    public void setEnableAgentic(Integer enableAgentic) { this.enableAgentic = enableAgentic; }
    /** 获取历史条数 */
    public Integer getHistoryLimit() { return historyLimit; }
    /** 设置历史条数 */
    public void setHistoryLimit(Integer historyLimit) { this.historyLimit = historyLimit; }
    /** 获取fallback Reply */
    public String getFallbackReply() { return fallbackReply; }
    /** 设置fallback Reply */
    public void setFallbackReply(String fallbackReply) { this.fallbackReply = fallbackReply; }
    /** 获取是否启用 */
    public Integer getEnabled() { return enabled; }
    /** 设置是否启用 */
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
}
