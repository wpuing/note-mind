package com.notemind.interfaces.knowledge.vo;

/**
 * 知识片段视图对象。
 */
public class KnowledgeSegmentVo {
    private String id;
    private String knowledgeBaseId;
    private String knowledgeBaseName;
    private String documentId;
    private String documentTitle;
    private String parentId;
    private String segmentType;
    private Integer segmentIndex;
    private String content;
    private Integer contentTokens;
    private Integer pageNo;
    private String vectorStatus;
    private String vectorId;
    private Integer manuallyEdited;
    private String createTime;

    /** 获取主键 ID */
    public String getId() {
        return id;
    }

    /** 设置主键 ID */
    public void setId(String id) {
        this.id = id;
    }

    /** 获取知识库 ID */
    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    /** 设置知识库 ID */
    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    /** 获取knowledge Base Name */
    public String getKnowledgeBaseName() {
        return knowledgeBaseName;
    }

    /** 设置knowledge Base Name */
    public void setKnowledgeBaseName(String knowledgeBaseName) {
        this.knowledgeBaseName = knowledgeBaseName;
    }

    /** 获取文档 ID */
    public String getDocumentId() {
        return documentId;
    }

    /** 设置文档 ID */
    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    /** 获取document Title */
    public String getDocumentTitle() {
        return documentTitle;
    }

    /** 设置document Title */
    public void setDocumentTitle(String documentTitle) {
        this.documentTitle = documentTitle;
    }

    /** 获取父片段 ID */
    public String getParentId() {
        return parentId;
    }

    /** 设置父片段 ID */
    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    /** 获取片段类型 */
    public String getSegmentType() {
        return segmentType;
    }

    /** 设置片段类型 */
    public void setSegmentType(String segmentType) {
        this.segmentType = segmentType;
    }

    /** 获取segment Index */
    public Integer getSegmentIndex() {
        return segmentIndex;
    }

    /** 设置segment Index */
    public void setSegmentIndex(Integer segmentIndex) {
        this.segmentIndex = segmentIndex;
    }

    /** 获取内容 */
    public String getContent() {
        return content;
    }

    /** 设置内容 */
    public void setContent(String content) {
        this.content = content;
    }

    /** 获取content Tokens */
    public Integer getContentTokens() {
        return contentTokens;
    }

    /** 设置content Tokens */
    public void setContentTokens(Integer contentTokens) {
        this.contentTokens = contentTokens;
    }

    /** 获取page No */
    public Integer getPageNo() {
        return pageNo;
    }

    /** 设置page No */
    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    /** 获取向量状态 */
    public String getVectorStatus() {
        return vectorStatus;
    }

    /** 设置向量状态 */
    public void setVectorStatus(String vectorStatus) {
        this.vectorStatus = vectorStatus;
    }

    /** 获取vector Id */
    public String getVectorId() {
        return vectorId;
    }

    /** 设置vector Id */
    public void setVectorId(String vectorId) {
        this.vectorId = vectorId;
    }

    /** 获取manually Edited */
    public Integer getManuallyEdited() {
        return manuallyEdited;
    }

    /** 设置manually Edited */
    public void setManuallyEdited(Integer manuallyEdited) {
        this.manuallyEdited = manuallyEdited;
    }

    /** 获取创建时间 */
    public String getCreateTime() {
        return createTime;
    }

    /** 设置创建时间 */
    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }
}
