package com.notemind.application.assembler.knowledge;

import com.notemind.domain.knowledge.entity.KnowledgeBase;
import com.notemind.interfaces.knowledge.vo.KnowledgeBaseVo;

/**
 * 知识库领域实体与接口 VO 的装配器。
 */
public final class KnowledgeBaseAssembler {

    /** 工具类禁止实例化 */
    private KnowledgeBaseAssembler() {}

    /**
     * 将知识库实体转为前端 VO（含文档/片段/向量统计字段）。
     *
     * @param kb 领域实体，可为 null
     * @return VO；实体为 null 时返回 null
     */
    public static KnowledgeBaseVo toVo(KnowledgeBase kb) {
        // 空实体直接返回
        if (kb == null) return null;
        KnowledgeBaseVo vo = new KnowledgeBaseVo();
        vo.setId(kb.getId());
        vo.setName(kb.getName());
        vo.setCategory(kb.getCategory());
        vo.setDescription(kb.getDescription());
        vo.setStatus(kb.getStatus());
        vo.setEmbeddingModelId(kb.getEmbeddingModelId());
        vo.setChunkStrategyId(kb.getChunkStrategyId());
        vo.setRetrievalStrategyId(kb.getRetrievalStrategyId());
        vo.setCreateTime(kb.getCreateTime());
        vo.setDocumentCount(kb.getDocumentCount());
        vo.setSegmentCount(kb.getSegmentCount());
        vo.setVectorDoneCount(kb.getVectorDoneCount());
        vo.setVectorPendingCount(kb.getVectorPendingCount());
        vo.setVectorFailedCount(kb.getVectorFailedCount());
        vo.setVectorStatus(kb.getVectorStatus());
        return vo;
    }
}

