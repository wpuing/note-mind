package com.notemind.client.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 切分结果（不写 PG），对应 POST /api/v1/ai/chunk/split。
 * <p>
 * 供 Java 落 MySQL 片段表；后续再调用 embed 写入向量。
 */
public class SplitResult {
    /** 文档 ID。 */
    private String documentId;
    /** 知识库 ID。 */
    private String knowledgeBaseId;
    /** 切分策略类型，如 recursive / parent_child。 */
    private String strategyType;
    /** 切分策略业务 ID。 */
    private String chunkStrategyId;
    /** 原文总字符数。 */
    private Integer charCount;
    /** 片段总数（含父块）。 */
    private int segmentCount;
    /** 可向量化子块数量。 */
    private int vectorizableCount;
    /** 切分状态。 */
    private String status;
    /** 切分出的片段明细。 */
    private List<SplitSegment> segments = new ArrayList<>();

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
     * 获取策略类型。
     *
     * @return 策略类型
     */
    public String getStrategyType() {
        return strategyType;
    }

    /**
     * 设置策略类型。
     *
     * @param strategyType 策略类型
     */
    public void setStrategyType(String strategyType) {
        this.strategyType = strategyType;
    }

    /**
     * 获取切分策略 ID。
     *
     * @return 策略 ID
     */
    public String getChunkStrategyId() {
        return chunkStrategyId;
    }

    /**
     * 设置切分策略 ID。
     *
     * @param chunkStrategyId 策略 ID
     */
    public void setChunkStrategyId(String chunkStrategyId) {
        this.chunkStrategyId = chunkStrategyId;
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
     * 获取片段总数。
     *
     * @return 片段数
     */
    public int getSegmentCount() {
        return segmentCount;
    }

    /**
     * 设置片段总数。
     *
     * @param segmentCount 片段数
     */
    public void setSegmentCount(int segmentCount) {
        this.segmentCount = segmentCount;
    }

    /**
     * 获取可向量化片段数。
     *
     * @return 可向量化数量
     */
    public int getVectorizableCount() {
        return vectorizableCount;
    }

    /**
     * 设置可向量化片段数。
     *
     * @param vectorizableCount 可向量化数量
     */
    public void setVectorizableCount(int vectorizableCount) {
        this.vectorizableCount = vectorizableCount;
    }

    /**
     * 获取切分状态。
     *
     * @return 状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置切分状态。
     *
     * @param status 状态
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取片段列表。
     *
     * @return 片段列表
     */
    public List<SplitSegment> getSegments() {
        return segments;
    }

    /**
     * 设置片段列表；null 时重置为空列表。
     *
     * @param segments 片段列表
     */
    public void setSegments(List<SplitSegment> segments) {
        // 空引用时兜底为空列表，避免下游 NPE
        this.segments = segments == null ? new ArrayList<>() : segments;
    }

    /**
     * 单条切分片段（可含父/子类型）。
     */
    public static class SplitSegment {
        /** 片段 ID。 */
        private String id;
        /** 父片段 ID（子块时有值）。 */
        private String parentId;
        /** 片段类型：parent / child / leaf 等。 */
        private String segmentType;
        /** 片段序号。 */
        private int segmentIndex;
        /** 片段正文。 */
        private String content;
        /** 估算 token 数。 */
        private Integer contentTokens;
        /** 页码信息（OCR 场景）。 */
        private Object page;
        /** 向量状态，切分后通常为 pending。 */
        private String vectorStatus;
        /** 扩展元数据。 */
        private Map<String, Object> meta;

        /**
         * 获取片段 ID。
         *
         * @return 片段 ID
         */
        public String getId() {
            return id;
        }

        /**
         * 设置片段 ID。
         *
         * @param id 片段 ID
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * 获取父片段 ID。
         *
         * @return 父片段 ID
         */
        public String getParentId() {
            return parentId;
        }

        /**
         * 设置父片段 ID。
         *
         * @param parentId 父片段 ID
         */
        public void setParentId(String parentId) {
            this.parentId = parentId;
        }

        /**
         * 获取片段类型。
         *
         * @return 类型
         */
        public String getSegmentType() {
            return segmentType;
        }

        /**
         * 设置片段类型。
         *
         * @param segmentType 类型
         */
        public void setSegmentType(String segmentType) {
            this.segmentType = segmentType;
        }

        /**
         * 获取片段序号。
         *
         * @return 序号
         */
        public int getSegmentIndex() {
            return segmentIndex;
        }

        /**
         * 设置片段序号。
         *
         * @param segmentIndex 序号
         */
        public void setSegmentIndex(int segmentIndex) {
            this.segmentIndex = segmentIndex;
        }

        /**
         * 获取正文。
         *
         * @return 正文
         */
        public String getContent() {
            return content;
        }

        /**
         * 设置正文。
         *
         * @param content 正文
         */
        public void setContent(String content) {
            this.content = content;
        }

        /**
         * 获取 token 估算。
         *
         * @return token 数
         */
        public Integer getContentTokens() {
            return contentTokens;
        }

        /**
         * 设置 token 估算。
         *
         * @param contentTokens token 数
         */
        public void setContentTokens(Integer contentTokens) {
            this.contentTokens = contentTokens;
        }

        /**
         * 获取页码信息。
         *
         * @return 页码对象
         */
        public Object getPage() {
            return page;
        }

        /**
         * 设置页码信息。
         *
         * @param page 页码对象
         */
        public void setPage(Object page) {
            this.page = page;
        }

        /**
         * 获取向量状态。
         *
         * @return 向量状态
         */
        public String getVectorStatus() {
            return vectorStatus;
        }

        /**
         * 设置向量状态。
         *
         * @param vectorStatus 向量状态
         */
        public void setVectorStatus(String vectorStatus) {
            this.vectorStatus = vectorStatus;
        }

        /**
         * 获取元数据。
         *
         * @return meta
         */
        public Map<String, Object> getMeta() {
            return meta;
        }

        /**
         * 设置元数据。
         *
         * @param meta meta
         */
        public void setMeta(Map<String, Object> meta) {
            this.meta = meta;
        }
    }
}
