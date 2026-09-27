package com.notemind.domain.knowledge.entity;

/**
 * 知识库聚合根（持久化字段 + 列表统计）。
 * <p>持久化：名称、分类、状态、绑定的向量模型与切分/检索策略；列表侧可带文档/片段/向量计数。</p>
 */
public class KnowledgeBase {
    /** 主键，如 kb_xxxxxxxxxxxxxxxx */
    private String id;
    /** 知识库名称 */
    private String name;
    /** 分类标签 */
    private String category;
    /** 描述说明 */
    private String description;
    /** 状态：1 启用 / 0 停用 */
    private Integer status;
    /** 绑定的向量（Embedding）模型 ID */
    private String embeddingModelId;
    /** 绑定的切分策略 ID */
    private String chunkStrategyId;
    /** 绑定的检索策略 ID */
    private String retrievalStrategyId;
    /** 创建时间 */
    private String createTime;
    /** 文档数量（列表统计，非必持久化） */
    private Long documentCount;
    /** 片段数量（列表统计） */
    private Long segmentCount;
    /** 向量化成功数（列表统计） */
    private Long vectorDoneCount;
    /** 待向量化数（列表统计） */
    private Long vectorPendingCount;
    /** 向量化失败数（列表统计） */
    private Long vectorFailedCount;
    /** 展示用向量状态：EMPTY / READY / PARTIAL / FAILED */
    private String vectorStatus;

    /** @return 主键 */ public String getId() { return id; }
    /** @param id 主键 */ public void setId(String id) { this.id = id; }
    /** @return 名称 */ public String getName() { return name; }
    /** @param name 名称 */ public void setName(String name) { this.name = name; }
    /** @return 分类 */ public String getCategory() { return category; }
    /** @param category 分类 */ public void setCategory(String category) { this.category = category; }
    /** @return 描述 */ public String getDescription() { return description; }
    /** @param description 描述 */ public void setDescription(String description) { this.description = description; }
    /** @return 状态 */ public Integer getStatus() { return status; }
    /** @param status 状态 */ public void setStatus(Integer status) { this.status = status; }
    /** @return 向量模型 ID */ public String getEmbeddingModelId() { return embeddingModelId; }
    /** @param embeddingModelId 向量模型 ID */ public void setEmbeddingModelId(String embeddingModelId) { this.embeddingModelId = embeddingModelId; }
    /** @return 切分策略 ID */ public String getChunkStrategyId() { return chunkStrategyId; }
    /** @param chunkStrategyId 切分策略 ID */ public void setChunkStrategyId(String chunkStrategyId) { this.chunkStrategyId = chunkStrategyId; }
    /** @return 检索策略 ID */ public String getRetrievalStrategyId() { return retrievalStrategyId; }
    /** @param retrievalStrategyId 检索策略 ID */ public void setRetrievalStrategyId(String retrievalStrategyId) { this.retrievalStrategyId = retrievalStrategyId; }
    /** @return 创建时间 */ public String getCreateTime() { return createTime; }
    /** @param createTime 创建时间 */ public void setCreateTime(String createTime) { this.createTime = createTime; }
    /** @return 文档数 */ public Long getDocumentCount() { return documentCount; }
    /** @param documentCount 文档数 */ public void setDocumentCount(Long documentCount) { this.documentCount = documentCount; }
    /** @return 片段数 */ public Long getSegmentCount() { return segmentCount; }
    /** @param segmentCount 片段数 */ public void setSegmentCount(Long segmentCount) { this.segmentCount = segmentCount; }
    /** @return 向量成功数 */ public Long getVectorDoneCount() { return vectorDoneCount; }
    /** @param vectorDoneCount 向量成功数 */ public void setVectorDoneCount(Long vectorDoneCount) { this.vectorDoneCount = vectorDoneCount; }
    /** @return 待向量化数 */ public Long getVectorPendingCount() { return vectorPendingCount; }
    /** @param vectorPendingCount 待向量化数 */ public void setVectorPendingCount(Long vectorPendingCount) { this.vectorPendingCount = vectorPendingCount; }
    /** @return 向量失败数 */ public Long getVectorFailedCount() { return vectorFailedCount; }
    /** @param vectorFailedCount 向量失败数 */ public void setVectorFailedCount(Long vectorFailedCount) { this.vectorFailedCount = vectorFailedCount; }
    /** @return 向量状态 */ public String getVectorStatus() { return vectorStatus; }
    /** @param vectorStatus 向量状态 */ public void setVectorStatus(String vectorStatus) { this.vectorStatus = vectorStatus; }
}
