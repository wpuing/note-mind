package com.notemind.interfaces.knowledge.vo;

import java.util.List;
import java.util.Map;

/**
 * 检索测试请求体。
 */
public class KnowledgeRetrievalTestRequest {
    private String question;
    private String knowledgeBaseId;
    /** 可选：覆盖知识库绑定策略；不传则用 KB 绑定 / 系统默认 */
    private String retrievalStrategyId;
    /** 可选：限定文档；不传或空表示整库检索 */
    private List<String> documentIds;
    private List<Map<String, String>> chatHistory;

    /** 获取问题 */
    public String getQuestion() {
        return question;
    }

    /** 设置问题 */
    public void setQuestion(String question) {
        this.question = question;
    }

    /** 获取知识库 ID */
    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    /** 设置知识库 ID */
    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    /** 获取检索策略 ID */
    public String getRetrievalStrategyId() {
        return retrievalStrategyId;
    }

    /** 设置检索策略 ID */
    public void setRetrievalStrategyId(String retrievalStrategyId) {
        this.retrievalStrategyId = retrievalStrategyId;
    }

    /** 获取document Ids */
    public List<String> getDocumentIds() {
        return documentIds;
    }

    /** 设置document Ids */
    public void setDocumentIds(List<String> documentIds) {
        this.documentIds = documentIds;
    }

    /** getChatHistory 方法 */
    public List<Map<String, String>> getChatHistory() {
        return chatHistory;
    }

    /** 设置chat History */
    public void setChatHistory(List<Map<String, String>> chatHistory) {
        this.chatHistory = chatHistory;
    }
}
