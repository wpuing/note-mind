package com.notemind.interfaces.knowledge.vo;

/**
 * 知识库保存请求体。
 */
public class KnowledgeBaseSaveRequest {
    private String name;
    private String category;
    private String description;
    private Integer status;
    private String embeddingModelId;
    private String chunkStrategyId;
    private String retrievalStrategyId;

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
}
