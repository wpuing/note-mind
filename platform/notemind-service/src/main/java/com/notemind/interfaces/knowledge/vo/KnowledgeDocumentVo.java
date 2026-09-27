package com.notemind.interfaces.knowledge.vo;

/**
 * 知识文档视图对象。
 */
public class KnowledgeDocumentVo {
    private String id;
    private String knowledgeBaseId;
    private String knowledgeBaseName;
    private String title;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String parseStatus;
    private String errorMessage;
    private Integer segmentCount;
    private Integer charCount;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private String chunkStrategyId;
    private String strategyType;
    private String createTime;
    private Long vectorDoneCount;
    private Long vectorPendingCount;
    private Long vectorFailedCount;
    /** READY / PARTIAL / EMPTY / FAILED */
    private String vectorStatus;

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

    /** 获取file Type */
    public String getFileType() {
        return fileType;
    }

    /** 设置file Type */
    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    /** 获取文件大小 */
    public Long getFileSize() {
        return fileSize;
    }

    /** 设置文件大小 */
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
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
    public Integer getSegmentCount() {
        return segmentCount;
    }

    /** 设置segment Count */
    public void setSegmentCount(Integer segmentCount) {
        this.segmentCount = segmentCount;
    }

    /** 获取char Count */
    public Integer getCharCount() {
        return charCount;
    }

    /** 设置char Count */
    public void setCharCount(Integer charCount) {
        this.charCount = charCount;
    }

    /** 获取chunk Size */
    public Integer getChunkSize() {
        return chunkSize;
    }

    /** 设置chunk Size */
    public void setChunkSize(Integer chunkSize) {
        this.chunkSize = chunkSize;
    }

    /** 获取chunk Overlap */
    public Integer getChunkOverlap() {
        return chunkOverlap;
    }

    /** 设置chunk Overlap */
    public void setChunkOverlap(Integer chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    /** 获取切分策略 ID */
    public String getChunkStrategyId() {
        return chunkStrategyId;
    }

    /** 设置切分策略 ID */
    public void setChunkStrategyId(String chunkStrategyId) {
        this.chunkStrategyId = chunkStrategyId;
    }

    /** 获取策略类型 */
    public String getStrategyType() {
        return strategyType;
    }

    /** 设置策略类型 */
    public void setStrategyType(String strategyType) {
        this.strategyType = strategyType;
    }

    /** 获取创建时间 */
    public String getCreateTime() {
        return createTime;
    }

    /** 设置创建时间 */
    public void setCreateTime(String createTime) {
        this.createTime = createTime;
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
}
