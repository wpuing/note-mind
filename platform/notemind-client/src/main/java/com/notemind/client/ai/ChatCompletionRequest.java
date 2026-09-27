package com.notemind.client.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容对话补全请求，对应 POST /api/v1/ai/llm/chat。
 * <p>
 * 用于 Judge、生成、评测等场景；密钥由 Java 侧解密后下发。
 */
public class ChatCompletionRequest {
    /** 对话消息列表（role + content）。 */
    private List<ChatMessage> messages = new ArrayList<>();
    /** 模型名称。 */
    private String model;
    /** 采样温度；Judge 场景通常为 0。 */
    private Double temperature;
    /** API Key 明文。 */
    private String apiKey;
    /** OpenAI 兼容 Base URL。 */
    private String baseUrl;

    /**
     * 获取消息列表。
     *
     * @return 消息列表
     */
    public List<ChatMessage> getMessages() { return messages; }

    /**
     * 设置消息列表。
     *
     * @param messages 消息列表
     */
    public void setMessages(List<ChatMessage> messages) { this.messages = messages; }

    /**
     * 获取模型名。
     *
     * @return 模型名
     */
    public String getModel() { return model; }

    /**
     * 设置模型名。
     *
     * @param model 模型名
     */
    public void setModel(String model) { this.model = model; }

    /**
     * 获取温度。
     *
     * @return 温度值
     */
    public Double getTemperature() { return temperature; }

    /**
     * 设置温度。
     *
     * @param temperature 温度值
     */
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    /**
     * 获取 API Key。
     *
     * @return 密钥
     */
    public String getApiKey() { return apiKey; }

    /**
     * 设置 API Key。
     *
     * @param apiKey 密钥
     */
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    /**
     * 获取 Base URL。
     *
     * @return 服务地址
     */
    public String getBaseUrl() { return baseUrl; }

    /**
     * 设置 Base URL。
     *
     * @param baseUrl 服务地址
     */
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    /**
     * 单条对话消息（role/content）。
     */
    public static class ChatMessage {
        /** 角色：system / user / assistant。 */
        private String role;
        /** 消息正文。 */
        private String content;

        /**
         * 无参构造，供反序列化使用。
         */
        public ChatMessage() {}

        /**
         * 便捷构造。
         *
         * @param role    角色
         * @param content 正文
         */
        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        /**
         * 获取角色。
         *
         * @return 角色
         */
        public String getRole() { return role; }

        /**
         * 设置角色。
         *
         * @param role 角色
         */
        public void setRole(String role) { this.role = role; }

        /**
         * 获取正文。
         *
         * @return 正文
         */
        public String getContent() { return content; }

        /**
         * 设置正文。
         *
         * @param content 正文
         */
        public void setContent(String content) { this.content = content; }
    }
}
