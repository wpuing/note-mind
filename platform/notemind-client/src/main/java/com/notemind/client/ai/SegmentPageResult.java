package com.notemind.client.ai;

import java.util.List;

/**
 * AI 侧文档片段分页查询结果。
 * <p>
 * 对应 GET /api/v1/ai/knowledge/documents/{documentId}/segments。
 */
public class SegmentPageResult {
    /** 总条数。 */
    private long total;
    /** 当前页码。 */
    private int page;
    /** 每页大小。 */
    private int pageSize;
    /** 当前页记录。 */
    private List<SegmentItem> records;

    /**
     * 获取总条数。
     *
     * @return 总数
     */
    public long getTotal() {
        return total;
    }

    /**
     * 设置总条数。
     *
     * @param total 总数
     */
    public void setTotal(long total) {
        this.total = total;
    }

    /**
     * 获取页码。
     *
     * @return 页码
     */
    public int getPage() {
        return page;
    }

    /**
     * 设置页码。
     *
     * @param page 页码
     */
    public void setPage(int page) {
        this.page = page;
    }

    /**
     * 获取每页大小。
     *
     * @return 页大小
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * 设置每页大小。
     *
     * @param pageSize 页大小
     */
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * 获取当前页记录。
     *
     * @return 记录列表
     */
    public List<SegmentItem> getRecords() {
        return records;
    }

    /**
     * 设置当前页记录。
     *
     * @param records 记录列表
     */
    public void setRecords(List<SegmentItem> records) {
        this.records = records;
    }

    /**
     * 单条片段摘要（列表展示用）。
     */
    public static class SegmentItem {
        /** 片段 ID。 */
        private String segmentId;
        /** 所属文档 ID。 */
        private String documentId;
        /** 所属知识库 ID。 */
        private String knowledgeBaseId;
        /** 父片段 ID。 */
        private String parentSegmentId;
        /** 完整正文（可选）。 */
        private String content;
        /** 切块序号。 */
        private Integer chunkIndex;
        /** 正文预览截断。 */
        private String contentPreview;

        /**
         * 获取片段 ID。
         *
         * @return 片段 ID
         */
        public String getSegmentId() {
            return segmentId;
        }

        /**
         * 设置片段 ID。
         *
         * @param segmentId 片段 ID
         */
        public void setSegmentId(String segmentId) {
            this.segmentId = segmentId;
        }

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
         * 获取切块序号。
         *
         * @return 序号
         */
        public Integer getChunkIndex() {
            return chunkIndex;
        }

        /**
         * 设置切块序号。
         *
         * @param chunkIndex 序号
         */
        public void setChunkIndex(Integer chunkIndex) {
            this.chunkIndex = chunkIndex;
        }

        /**
         * 获取内容预览。
         *
         * @return 预览文本
         */
        public String getContentPreview() {
            return contentPreview;
        }

        /**
         * 设置内容预览。
         *
         * @param contentPreview 预览文本
         */
        public void setContentPreview(String contentPreview) {
            this.contentPreview = contentPreview;
        }
    }
}
