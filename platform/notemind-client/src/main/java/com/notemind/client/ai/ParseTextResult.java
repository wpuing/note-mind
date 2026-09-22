package com.notemind.client.ai;

/**
 * 文档解析为纯文本的结果（不切分、不向量化）。
 * <p>
 * 对应 POST /api/v1/ai/parse/extract，用于管理端「解析预览」。
 */
public class ParseTextResult {
    /** 原始文件名。 */
    private String filename;
    /** 识别出的文件类型。 */
    private String fileType;
    /** 解析后字符数。 */
    private Integer charCount;
    /** 纯文本正文。 */
    private String text;
    /** 解析状态，如 ok / fail。 */
    private String status;
    /** 提示信息（如限页 OCR 说明）。 */
    private String hint;

    /**
     * 获取文件名。
     *
     * @return 文件名
     */
    public String getFilename() { return filename; }

    /**
     * 设置文件名。
     *
     * @param filename 文件名
     */
    public void setFilename(String filename) { this.filename = filename; }

    /**
     * 获取文件类型。
     *
     * @return 类型
     */
    public String getFileType() { return fileType; }

    /**
     * 设置文件类型。
     *
     * @param fileType 类型
     */
    public void setFileType(String fileType) { this.fileType = fileType; }

    /**
     * 获取字符数。
     *
     * @return 字符数
     */
    public Integer getCharCount() { return charCount; }

    /**
     * 设置字符数。
     *
     * @param charCount 字符数
     */
    public void setCharCount(Integer charCount) { this.charCount = charCount; }

    /**
     * 获取纯文本。
     *
     * @return 文本
     */
    public String getText() { return text; }

    /**
     * 设置纯文本。
     *
     * @param text 文本
     */
    public void setText(String text) { this.text = text; }

    /**
     * 获取解析状态。
     *
     * @return 状态
     */
    public String getStatus() { return status; }

    /**
     * 设置解析状态。
     *
     * @param status 状态
     */
    public void setStatus(String status) { this.status = status; }

    /**
     * 获取提示信息。
     *
     * @return 提示
     */
    public String getHint() { return hint; }

    /**
     * 设置提示信息。
     *
     * @param hint 提示
     */
    public void setHint(String hint) { this.hint = hint; }
}
