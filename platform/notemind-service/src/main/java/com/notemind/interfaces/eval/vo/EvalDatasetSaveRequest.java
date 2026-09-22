package com.notemind.interfaces.eval.vo;

/**
 * 评测集保存请求体。
 */
public class EvalDatasetSaveRequest {
    private String name;
    private String description;
    private String knowledgeBaseId;
    private String sourceType;

    /** 获取名称 */
    public String getName() { return name; }
    /** 设置名称 */
    public void setName(String name) { this.name = name; }
    /** 获取描述 */
    public String getDescription() { return description; }
    /** 设置描述 */
    public void setDescription(String description) { this.description = description; }
    /** 获取知识库 ID */
    public String getKnowledgeBaseId() { return knowledgeBaseId; }
    /** 设置知识库 ID */
    public void setKnowledgeBaseId(String knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    /** 获取来源类型 */
    public String getSourceType() { return sourceType; }
    /** 设置来源类型 */
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
}
