package com.notemind.interfaces.knowledge.vo;

import java.util.List;

/**
 * 召回调试多策略对比请求体。
 */
public class RecallBenchCompareRequest {
    private String question;
    private String knowledgeBaseId;
    /** 1～4 个检索策略 ID */
    private List<String> retrievalStrategyIds;
    private List<String> documentIds;

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

    /** 获取retrieval Strategy Ids */
    public List<String> getRetrievalStrategyIds() {
        return retrievalStrategyIds;
    }

    /** 设置retrieval Strategy Ids */
    public void setRetrievalStrategyIds(List<String> retrievalStrategyIds) {
        this.retrievalStrategyIds = retrievalStrategyIds;
    }

    /** 获取document Ids */
    public List<String> getDocumentIds() {
        return documentIds;
    }

    /** 设置document Ids */
    public void setDocumentIds(List<String> documentIds) {
        this.documentIds = documentIds;
    }
}
