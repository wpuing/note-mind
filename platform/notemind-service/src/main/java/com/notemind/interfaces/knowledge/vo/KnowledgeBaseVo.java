package com.notemind.interfaces.knowledge.vo;

/**
 * 知识库视图对象。
 */
public class KnowledgeBaseVo {
    private String id;
    private String name;
    private String category;
    private String description;
    private Integer status;
    private String embeddingModelId;
    private String chunkStrategyId;
    private String retrievalStrategyId;
    private Long documentCount;
    private Long segmentCount;
    private String createTime;
    /** 向量汇总：READY / PARTIAL / EMPTY / FAILED */
    private String vectorStatus;
    private Long vectorDoneCount;
    private Long vectorPendingCount;
    private Long vectorFailedCount;

    /** 获取主键 ID */
    public String getId() {
        return id;
    }

    /** 设置主键 ID */
    public void setId(String id) {
        this.id = id;
    }

    /** 获取名称 */
    public String getName() {
        return name;
    }

    /** 设置名称 */
    public void setName(String name) {
        this.name = name;
    }

    /** 获取分类 */
    public String getCategory() {
        return category;
    }

    /** 设置分类 */
    public void setCategory(String category) {
        this.category = category;
    }

    /** 获取描述 */
    public String getDescription() {
        return description;
    }

    /** 设置描述 */
    public void setDescription(String description) {
        this.description = description;
    }

    /** 获取状态 */
    public Integer getStatus() {
        return status;
    }

    /** 设置状态 */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /** 获取embedding Model Id */
    public String getEmbeddingModelId() {
        return embeddingModelId;
    }

    /** 设置embedding Model Id */
    public void setEmbeddingModelId(String embeddingModelId) {
        this.embeddingModelId = embeddingModelId;
    }

    /** 获取切分策略 ID */
    public String getChunkStrategyId() {
        return chunkStrategyId;
    }

    /** 设置切分策略 ID */
    public void setChunkStrategyId(String chunkStrategyId) {
        this.chunkStrategyId = chunkStrategyId;
    }

    /** 获取检索策略 ID */
    public String getRetrievalStrategyId() {
        return retrievalStrategyId;
    }

    /** 设置检索策略 ID */
    public void setRetrievalStrategyId(String retrievalStrategyId) {
        this.retrievalStrategyId = retrievalStrategyId;
    }

    /** 获取document Count */
    public Long getDocumentCount() {
        return documentCount;
    }

    /** 设置document Count */
    public void setDocumentCount(Long documentCount) {
        this.documentCount = documentCount;
    }

    /** 获取segment Count */
    public Long getSegmentCount() {
        return segmentCount;
    }

    /** 设置segment Count */
    public void setSegmentCount(Long segmentCount) {
        this.segmentCount = segmentCount;
    }

    /** 获取创建时间 */
    public String getCreateTime() {
        return createTime;
    }

    /** 设置创建时间 */
    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    /** 获取向量状态 */
    public String getVectorStatus() {
        return vectorStatus;
    }

    /** 设置向量状态 */
    public void setVectorStatus(String vectorStatus) {
        this.vectorStatus = vectorStatus;
    }

    /** 获取vector Done Count */
    public Long getVectorDoneCount() {
        return vectorDoneCount;
    }

    /** 设置vector Done Count */
    public void setVectorDoneCount(Long vectorDoneCount) {
        this.vectorDoneCount = vectorDoneCount;
    }

    /** 获取vector Pending Count */
    public Long getVectorPendingCount() {
        return vectorPendingCount;
    }

    /** 设置vector Pending Count */
    public void setVectorPendingCount(Long vectorPendingCount) {
        this.vectorPendingCount = vectorPendingCount;
    }

    /** 获取vector Failed Count */
    public Long getVectorFailedCount() {
        return vectorFailedCount;
    }

    /** 设置vector Failed Count */
    public void setVectorFailedCount(Long vectorFailedCount) {
        this.vectorFailedCount = vectorFailedCount;
    }
}
