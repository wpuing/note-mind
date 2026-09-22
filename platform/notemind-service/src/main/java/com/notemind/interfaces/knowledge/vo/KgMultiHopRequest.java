package com.notemind.interfaces.knowledge.vo;

/** 知识图谱多跳查询请求。 */
public class KgMultiHopRequest {
    private String entityId;
    private Integer depth;

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public Integer getDepth() {
        return depth;
    }

    public void setDepth(Integer depth) {
        this.depth = depth;
    }
}
