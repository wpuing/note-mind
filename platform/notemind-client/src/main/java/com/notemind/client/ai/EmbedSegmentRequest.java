package com.notemind.client.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 向 AI 引擎提交待向量化片段的请求体。
 * <p>
 * 对应 POST /api/v1/ai/chunk/embed；仅子块应入 PGVector。
 */
public class EmbedSegmentRequest {
    /** 文档 ID。 */
    private String documentId;
    /** 知识库 ID。 */
    private String knowledgeBaseId;
    /** 待向量化片段列表。 */
    private List<Item> segments = new ArrayList<>();

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
     * 获取待向量化片段列表。
     *
     * @return 片段列表
     */
    public List<Item> getSegments() {
        return segments;
    }

    /**
     * 设置待向量化片段列表；null 时重置为空列表。
     *
     * @param segments 片段列表
     */
    public void setSegments(List<Item> segments) {
        // 空引用时兜底为空列表，避免下游 NPE
        this.segments = segments == null ? new ArrayList<>() : segments;
    }

    /**
     * 单条待向量化片段。
     */
    public static class Item {
        /** 片段业务 ID（与 MySQL 对齐）。 */
        private String id;
        /** 片段正文。 */
        private String content;
        /** 父片段 ID（父子切分时）。 */
        private String parentSegmentId;
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
         * 获取片段正文。
         *
         * @return 正文
         */
        public String getContent() {
            return content;
        }

        /**
         * 设置片段正文。
         *
         * @param content 正文
         */
        public void setContent(String content) {
            this.content = content;
        }

        /**
         * 获取父片段 ID。
         *
         * @return 父片段 ID
         */
        public String getParentSegmentId() {
            return parentSegmentId;
        }

        /**
         * 设置父片段 ID。
         *
         * @param parentSegmentId 父片段 ID
         */
        public void setParentSegmentId(String parentSegmentId) {
            this.parentSegmentId = parentSegmentId;
        }

        /**
         * 获取元数据。
         *
         * @return meta Map
         */
        public Map<String, Object> getMeta() {
            return meta;
        }

        /**
         * 设置元数据。
         *
         * @param meta meta Map
         */
        public void setMeta(Map<String, Object> meta) {
            this.meta = meta;
        }
    }
}
