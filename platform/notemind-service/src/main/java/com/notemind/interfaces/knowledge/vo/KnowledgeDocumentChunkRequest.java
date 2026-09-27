package com.notemind.interfaces.knowledge.vo;

/**
 * 文档切分请求体。
 */
public class KnowledgeDocumentChunkRequest {
    private String chunkStrategyId;

    /** 获取切分策略 ID */
    public String getChunkStrategyId() {
        return chunkStrategyId;
    }

    /** 设置切分策略 ID */
    public void setChunkStrategyId(String chunkStrategyId) {
        this.chunkStrategyId = chunkStrategyId;
    }
}
