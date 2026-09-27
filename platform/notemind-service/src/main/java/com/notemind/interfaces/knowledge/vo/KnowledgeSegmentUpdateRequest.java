package com.notemind.interfaces.knowledge.vo;

/**
 * 知识片段更新请求体。
 */
public class KnowledgeSegmentUpdateRequest {
    private String content;

    /** 获取内容 */
    public String getContent() {
        return content;
    }

    /** 设置内容 */
    public void setContent(String content) {
        this.content = content;
    }
}
