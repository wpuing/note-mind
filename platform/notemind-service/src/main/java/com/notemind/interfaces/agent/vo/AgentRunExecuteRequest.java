package com.notemind.interfaces.agent.vo;

/**
 * Agent 执行请求体：知识库、检索策略、应用与问题。
 */
public class AgentRunExecuteRequest {
    private String knowledgeBaseId;
    private String retrievalStrategyId;
    private String appId;
    private String question;

    /** 获取知识库 ID */
    public String getKnowledgeBaseId() { return knowledgeBaseId; }
    /** 设置知识库 ID */
    public void setKnowledgeBaseId(String knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    /** 获取检索策略 ID */
    public String getRetrievalStrategyId() { return retrievalStrategyId; }
    /** 设置检索策略 ID */
    public void setRetrievalStrategyId(String retrievalStrategyId) { this.retrievalStrategyId = retrievalStrategyId; }
    /** 获取应用 ID */
    public String getAppId() { return appId; }
    /** 设置应用 ID */
    public void setAppId(String appId) { this.appId = appId; }
    /** 获取问题 */
    public String getQuestion() { return question; }
    /** 设置问题 */
    public void setQuestion(String question) { this.question = question; }
}
