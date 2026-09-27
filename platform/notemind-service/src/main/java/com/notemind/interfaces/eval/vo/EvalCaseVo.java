package com.notemind.interfaces.eval.vo;

/**
 * 评测用例视图对象。
 */
public class EvalCaseVo {
    private String id;
    private String datasetId;
    private String question;
    private String expectedAnswer;
    private String sourceType;
    private Integer includeInEval;
    private String remark;
    private String knowledgeBaseId;
    private String documentId;
    private String sourceSegmentIds;
    private String sourceContent;
    private String sourceLabel;
    private String createTime;

    /** 获取主键 ID */
    public String getId() { return id; }
    /** 设置主键 ID */
    public void setId(String id) { this.id = id; }
    /** 获取评测集 ID */
    public String getDatasetId() { return datasetId; }
    /** 设置评测集 ID */
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    /** 获取问题 */
    public String getQuestion() { return question; }
    /** 设置问题 */
    public void setQuestion(String question) { this.question = question; }
    /** 获取期望答案 */
    public String getExpectedAnswer() { return expectedAnswer; }
    /** 设置期望答案 */
    public void setExpectedAnswer(String expectedAnswer) { this.expectedAnswer = expectedAnswer; }
    /** 获取来源类型 */
    public String getSourceType() { return sourceType; }
    /** 设置来源类型 */
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    /** 获取include In Eval */
    public Integer getIncludeInEval() { return includeInEval; }
    /** 设置include In Eval */
    public void setIncludeInEval(Integer includeInEval) { this.includeInEval = includeInEval; }
    /** 获取备注 */
    public String getRemark() { return remark; }
    /** 设置备注 */
    public void setRemark(String remark) { this.remark = remark; }
    /** 获取知识库 ID */
    public String getKnowledgeBaseId() { return knowledgeBaseId; }
    /** 设置知识库 ID */
    public void setKnowledgeBaseId(String knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    /** 获取文档 ID */
    public String getDocumentId() { return documentId; }
    /** 设置文档 ID */
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    /** 获取原文片段 ID */
    public String getSourceSegmentIds() { return sourceSegmentIds; }
    /** 设置原文片段 ID */
    public void setSourceSegmentIds(String sourceSegmentIds) { this.sourceSegmentIds = sourceSegmentIds; }
    /** 获取source Content */
    public String getSourceContent() { return sourceContent; }
    /** 设置source Content */
    public void setSourceContent(String sourceContent) { this.sourceContent = sourceContent; }
    /** 获取source Label */
    public String getSourceLabel() { return sourceLabel; }
    /** 设置source Label */
    public void setSourceLabel(String sourceLabel) { this.sourceLabel = sourceLabel; }
    /** 获取创建时间 */
    public String getCreateTime() { return createTime; }
    /** 设置创建时间 */
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
