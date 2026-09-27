package com.notemind.client.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 引擎从 PGVector/BM25 汇总的知识库向量状态。
 * <p>
 * 对应 GET /api/v1/ai/knowledge/bases/{id}/vector-status。
 */
public class KnowledgeVectorStatusResult {
    /** 知识库 ID。 */
    private String knowledgeBaseId;
    /** 片段总数。 */
    private long segmentCount;
    /** 已向量化数量。 */
    private long vectorDoneCount;
    /** 待向量化数量。 */
    private long vectorPendingCount;
    /** 向量化失败数量。 */
    private long vectorFailedCount;
    /** 库级汇总状态。 */
    private String vectorStatus;
    /** 按文档拆分的向量统计。 */
    private List<DocumentVectorStat> documents = new ArrayList<>();

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
     * 获取片段总数。
     *
     * @return 片段数
     */
    public long getSegmentCount() {
        return segmentCount;
    }

    /**
     * 设置片段总数。
     *
     * @param segmentCount 片段数
     */
    public void setSegmentCount(long segmentCount) {
        this.segmentCount = segmentCount;
    }

    /**
     * 获取已完成向量化数量。
     *
     * @return 完成数
     */
    public long getVectorDoneCount() {
        return vectorDoneCount;
    }

    /**
     * 设置已完成向量化数量。
     *
     * @param vectorDoneCount 完成数
     */
    public void setVectorDoneCount(long vectorDoneCount) {
        this.vectorDoneCount = vectorDoneCount;
    }

    /**
     * 获取待向量化数量。
     *
     * @return 待处理数
     */
    public long getVectorPendingCount() {
        return vectorPendingCount;
    }

    /**
     * 设置待向量化数量。
     *
     * @param vectorPendingCount 待处理数
     */
    public void setVectorPendingCount(long vectorPendingCount) {
        this.vectorPendingCount = vectorPendingCount;
    }

    /**
     * 获取失败数量。
     *
     * @return 失败数
     */
    public long getVectorFailedCount() {
        return vectorFailedCount;
    }

    /**
     * 设置失败数量。
     *
     * @param vectorFailedCount 失败数
     */
    public void setVectorFailedCount(long vectorFailedCount) {
        this.vectorFailedCount = vectorFailedCount;
    }

    /**
     * 获取库级向量状态。
     *
     * @return 状态字符串
     */
    public String getVectorStatus() {
        return vectorStatus;
    }

    /**
     * 设置库级向量状态。
     *
     * @param vectorStatus 状态字符串
     */
    public void setVectorStatus(String vectorStatus) {
        this.vectorStatus = vectorStatus;
    }

    /**
     * 获取文档级统计列表。
     *
     * @return 文档统计
     */
    public List<DocumentVectorStat> getDocuments() {
        return documents;
    }

    /**
     * 设置文档级统计列表；null 时重置为空列表。
     *
     * @param documents 文档统计
     */
    public void setDocuments(List<DocumentVectorStat> documents) {
        // 空引用时兜底为空列表，避免下游 NPE
        this.documents = documents == null ? new ArrayList<>() : documents;
    }

    /**
     * 单个文档的向量状态统计。
     */
    public static class DocumentVectorStat {
        /** 文档 ID。 */
        private String documentId;
        /** 该文档片段数。 */
        private long segmentCount;
        /** 已完成数。 */
        private long vectorDoneCount;
        /** 待处理数。 */
        private long vectorPendingCount;
        /** 失败数。 */
        private long vectorFailedCount;
        /** 文档级汇总状态。 */
        private String vectorStatus;

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
         * 获取片段数。
         *
         * @return 片段数
         */
        public long getSegmentCount() {
            return segmentCount;
        }

        /**
         * 设置片段数。
         *
         * @param segmentCount 片段数
         */
        public void setSegmentCount(long segmentCount) {
            this.segmentCount = segmentCount;
        }

        /**
         * 获取已完成数。
         *
         * @return 完成数
         */
        public long getVectorDoneCount() {
            return vectorDoneCount;
        }

        /**
         * 设置已完成数。
         *
         * @param vectorDoneCount 完成数
         */
        public void setVectorDoneCount(long vectorDoneCount) {
            this.vectorDoneCount = vectorDoneCount;
        }

        /**
         * 获取待处理数。
         *
         * @return 待处理数
         */
        public long getVectorPendingCount() {
            return vectorPendingCount;
        }

        /**
         * 设置待处理数。
         *
         * @param vectorPendingCount 待处理数
         */
        public void setVectorPendingCount(long vectorPendingCount) {
            this.vectorPendingCount = vectorPendingCount;
        }

        /**
         * 获取失败数。
         *
         * @return 失败数
         */
        public long getVectorFailedCount() {
            return vectorFailedCount;
        }

        /**
         * 设置失败数。
         *
         * @param vectorFailedCount 失败数
         */
        public void setVectorFailedCount(long vectorFailedCount) {
            this.vectorFailedCount = vectorFailedCount;
        }

        /**
         * 获取文档向量状态。
         *
         * @return 状态
         */
        public String getVectorStatus() {
            return vectorStatus;
        }

        /**
         * 设置文档向量状态。
         *
         * @param vectorStatus 状态
         */
        public void setVectorStatus(String vectorStatus) {
            this.vectorStatus = vectorStatus;
        }
    }
}
