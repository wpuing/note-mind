package com.notemind.interfaces.eval.vo;

/**
 * 从文档生成评测用例请求体。
 */
public class EvalGenerateFromDocRequest {
    private String knowledgeBaseId;
    private String documentId;
    private Integer count;

    /** 获取知识库 ID */
    public String getKnowledgeBaseId() { return knowledgeBaseId; }
    /** 设置知识库 ID */
    public void setKnowledgeBaseId(String knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    /** 获取文档 ID */
    public String getDocumentId() { return documentId; }
    /** 设置文档 ID */
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    /** 获取count */
    public Integer getCount() { return count; }
    /** 设置count */
    public void setCount(Integer count) { this.count = count; }
}
