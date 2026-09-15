package com.notemind.client.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agentic RAG 运行请求体，对应 POST /api/v1/ai/agent/run。
 * <p>
 * 携带用户问题、知识库、检索策略快照以及对话模型凭证与最大轮次。
 */
public class AgentGraphRunRequest {
    /** 用户原始问题。 */
    private String question;
    /** 目标知识库 ID。 */
    private String knowledgeBaseId;
    /** 检索策略七开关与阈值快照。 */
    private RetrievalStrategyParams strategy;
    /** 对话模型凭证（模型名、Key、Base URL、温度）。 */
    private ChatCreds chat;
    /** Agentic 最大检索/改写轮次上限。 */
    private Integer maxRounds;

    /**
     * 获取用户问题。
     *
     * @return 问题文本
     */
    public String getQuestion() { return question; }

    /**
     * 设置用户问题。
     *
     * @param question 问题文本
     */
    public void setQuestion(String question) { this.question = question; }

    /**
     * 获取知识库 ID。
     *
     * @return 知识库 ID
     */
    public String getKnowledgeBaseId() { return knowledgeBaseId; }

    /**
     * 设置知识库 ID。
     *
     * @param knowledgeBaseId 知识库 ID
     */
    public void setKnowledgeBaseId(String knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }

    /**
     * 获取检索策略参数。
     *
     * @return 策略快照
     */
    public RetrievalStrategyParams getStrategy() { return strategy; }

    /**
     * 设置检索策略参数。
     *
     * @param strategy 策略快照
     */
    public void setStrategy(RetrievalStrategyParams strategy) { this.strategy = strategy; }

    /**
     * 获取对话模型凭证。
     *
     * @return 凭证对象
     */
    public ChatCreds getChat() { return chat; }

    /**
     * 设置对话模型凭证。
     *
     * @param chat 凭证对象
     */
    public void setChat(ChatCreds chat) { this.chat = chat; }

    /**
     * 获取最大轮次。
     *
     * @return 轮次上限，可为 null
     */
    public Integer getMaxRounds() { return maxRounds; }

    /**
     * 设置最大轮次。
     *
     * @param maxRounds 轮次上限
     */
    public void setMaxRounds(Integer maxRounds) { this.maxRounds = maxRounds; }

    /**
     * 对话模型调用凭证（透传给 Python LLM 客户端）。
     */
    public static class ChatCreds {
        /** 模型名称，如 qwen-plus。 */
        private String model;
        /** API Key（由 Java 解密后下发，勿落日志）。 */
        private String apiKey;
        /** OpenAI 兼容 Base URL。 */
        private String baseUrl;
        /** 采样温度。 */
        private Double temperature;

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
         * 获取 API Key。
         *
         * @return 密钥明文
         */
        public String getApiKey() { return apiKey; }

        /**
         * 设置 API Key。
         *
         * @param apiKey 密钥明文
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
    }
}
