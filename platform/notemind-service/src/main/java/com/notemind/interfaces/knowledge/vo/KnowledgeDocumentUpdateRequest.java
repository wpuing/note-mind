package com.notemind.interfaces.knowledge.vo;

/**
 * 知识文档更新请求体。
 */
public class KnowledgeDocumentUpdateRequest {
    private String title;

    /** 获取标题 */
    public String getTitle() {
        return title;
    }

    /** 设置标题 */
    public void setTitle(String title) {
        this.title = title;
    }
}
