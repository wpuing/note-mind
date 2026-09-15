package com.notemind.client.ai;

/**
 * LLM 对话补全结果，对应 POST /api/v1/ai/llm/chat 响应。
 */
public class ChatCompletionResult {
    /** 模型返回的正文内容。 */
    private String content;

    /**
     * 获取回复正文。
     *
     * @return 文本内容
     */
    public String getContent() { return content; }

    /**
     * 设置回复正文。
     *
     * @param content 文本内容
     */
    public void setContent(String content) { this.content = content; }
}
