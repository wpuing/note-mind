package com.notemind.interfaces.knowledge.vo;

/** 文档解析文本预览。 */
public class DocumentParsedTextVo {
    private String documentId;
    private String title;
    private String fileName;
    private String fileType;
    private Integer charCount;
    private String text;
    private String hint;

    /** 获取文档 ID */
    public String getDocumentId() { return documentId; }
    /** 设置文档 ID */
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    /** 获取标题 */
    public String getTitle() { return title; }
    /** 设置标题 */
    public void setTitle(String title) { this.title = title; }
    /** 获取文件名 */
    public String getFileName() { return fileName; }
    /** 设置文件名 */
    public void setFileName(String fileName) { this.fileName = fileName; }
    /** 获取file Type */
    public String getFileType() { return fileType; }
    /** 设置file Type */
    public void setFileType(String fileType) { this.fileType = fileType; }
    /** 获取char Count */
    public Integer getCharCount() { return charCount; }
    /** 设置char Count */
    public void setCharCount(Integer charCount) { this.charCount = charCount; }
    /** 获取文本内容 */
    public String getText() { return text; }
    /** 设置文本内容 */
    public void setText(String text) { this.text = text; }
    /** 获取hint */
    public String getHint() { return hint; }
    /** 设置hint */
    public void setHint(String hint) { this.hint = hint; }
}
