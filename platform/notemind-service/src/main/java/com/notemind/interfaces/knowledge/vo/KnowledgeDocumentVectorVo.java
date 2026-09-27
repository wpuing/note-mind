package com.notemind.interfaces.knowledge.vo;

/** 知识库向量状态下钻：单文档汇总 */
public class KnowledgeDocumentVectorVo {
    private String documentId;
    private String title;
    private String fileName;
    private String parseStatus;
    private String errorMessage;
    private Long segmentCount;
    private Long vectorDoneCount;
    private Long vectorPendingCount;
    private Long vectorFailedCount;
    private String vectorStatus;
    private String createTime;

    /** 获取文档 ID */
    public String getDocumentId() {
        return documentId;
    }

    /** 设置文档 ID */
    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    /** 获取标题 */
    public String getTitle() {
        return title;
    }

    /** 设置标题 */
    public void setTitle(String title) {
        this.title = title;
    }

    /** 获取文件名 */
    public String getFileName() {
        return fileName;
    }

    /** 设置文件名 */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /** 获取解析状态 */
    public String getParseStatus() {
        return parseStatus;
    }

    /** 设置解析状态 */
    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }

    /** 获取错误信息 */
    public String getErrorMessage() {
        return errorMessage;
    }

    /** 设置错误信息 */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /** 获取segment Count */
    public Long getSegmentCount() {
        return segmentCount;
    }

    /** 设置segment Count */
    public void setSegmentCount(Long segmentCount) {
        this.segmentCount = segmentCount;
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

    /** 获取向量状态 */
    public String getVectorStatus() {
        return vectorStatus;
    }

    /** 设置向量状态 */
    public void setVectorStatus(String vectorStatus) {
        this.vectorStatus = vectorStatus;
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
