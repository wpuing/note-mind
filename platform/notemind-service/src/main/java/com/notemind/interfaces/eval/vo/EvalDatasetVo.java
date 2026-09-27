package com.notemind.interfaces.eval.vo;

/**
 * 评测集视图对象。
 */
public class EvalDatasetVo {
    private String id;
    private String name;
    private String description;
    private String knowledgeBaseId;
    private String knowledgeBaseName;
    private String sourceType;
    private Integer caseCount;
    private String createTime;

    /** 获取主键 ID */
    public String getId() { return id; }
    /** 设置主键 ID */
    public void setId(String id) { this.id = id; }
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
    /** 获取knowledge Base Name */
    public String getKnowledgeBaseName() { return knowledgeBaseName; }
    /** 设置knowledge Base Name */
    public void setKnowledgeBaseName(String knowledgeBaseName) { this.knowledgeBaseName = knowledgeBaseName; }
    /** 获取来源类型 */
    public String getSourceType() { return sourceType; }
    /** 设置来源类型 */
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    /** 获取case Count */
    public Integer getCaseCount() { return caseCount; }
    /** 设置case Count */
    public void setCaseCount(Integer caseCount) { this.caseCount = caseCount; }
    /** 获取创建时间 */
    public String getCreateTime() { return createTime; }
    /** 设置创建时间 */
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
