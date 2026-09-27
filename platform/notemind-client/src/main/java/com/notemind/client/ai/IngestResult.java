package com.notemind.client.ai;

/**
 * 入库/向量化结果摘要。
 * <p>
 * 用于旧版 ingest 与当前 embedSegments 的响应映射。
 */
public class IngestResult {
    /** 文档 ID。 */
    private String documentId;
    /** 知识库 ID。 */
    private String knowledgeBaseId;
    /** 处理片段数。 */
    private int segmentCount;
    /** 原文或处理字符数。 */
    private Integer charCount;
    /** 处理状态。 */
    private String status;

    /**
     * 获取文档 ID。
     *
     * @return 文档 ID
     */
    public String getDocumentId() {
        return documentId;
    }

    /**
     * 设置文档 ID。
     *
     * @param documentId 文档 ID
     */
    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    /**
     * 获取知识库 ID。
     *
     * @return 知识库 ID
     */
    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    /**
     * 设置知识库 ID。
     *
     * @param knowledgeBaseId 知识库 ID
     */
    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    /**
     * 获取片段数。
     *
     * @return 片段数
     */
    public int getSegmentCount() {
        return segmentCount;
    }

    /**
     * 设置片段数。
     *
     * @param segmentCount 片段数
     */
    public void setSegmentCount(int segmentCount) {
        this.segmentCount = segmentCount;
    }

    /**
     * 获取字符数。
     *
     * @return 字符数
     */
    public Integer getCharCount() {
        return charCount;
    }

    /**
     * 设置字符数。
     *
     * @param charCount 字符数
     */
    public void setCharCount(Integer charCount) {
        this.charCount = charCount;
    }

    /**
     * 获取状态。
     *
     * @return 状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置状态。
     *
     * @param status 状态
     */
    public void setStatus(String status) {
        this.status = status;
    }
}
