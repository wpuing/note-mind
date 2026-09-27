package com.notemind.infrastructure.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 引擎连接配置，对应 {@code notemind.ai-engine.*}。
 * <p>含基址、内部 Token、连接/读/解析超时。
 */
@ConfigurationProperties(prefix = "notemind.ai-engine")
public class AiEngineProperties {

    /** Python AI 引擎基址，默认本机 8000 */
    private String baseUrl = "http://127.0.0.1:8000";
    /** 内部调用 Token（请求头 X-AI-Engine-Token） */
    private String token = "local-dev-ai-engine-token";
    /** TCP 连接超时（毫秒） */
    private int connectTimeoutMs = 10000;
    /** 普通读超时（毫秒），默认 5 分钟 */
    private int readTimeoutMs = 300000;
    /** 解析/OCR 专用超时（默认 10 分钟） */
    private int parseTimeoutMs = 600000;

    /**
     * 获取 AI 引擎基址。
     *
     * @return 基址 URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 设置 AI 引擎基址。
     *
     * @param baseUrl 基址 URL
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * 获取内部调用 Token。
     *
     * @return Token 字符串
     */
    public String getToken() {
        return token;
    }

    /**
     * 设置内部调用 Token。
     *
     * @param token Token 字符串
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * 获取连接超时毫秒数。
     *
     * @return 连接超时
     */
    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    /**
     * 设置连接超时毫秒数。
     *
     * @param connectTimeoutMs 连接超时
     */
    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    /**
     * 获取读超时毫秒数。
     *
     * @return 读超时
     */
    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    /**
     * 设置读超时毫秒数。
     *
     * @param readTimeoutMs 读超时
     */
    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * 获取解析/OCR 专用超时毫秒数。
     *
     * @return 解析超时
     */
    public int getParseTimeoutMs() {
        return parseTimeoutMs;
    }

    /**
     * 设置解析/OCR 专用超时毫秒数。
     *
     * @param parseTimeoutMs 解析超时
     */
    public void setParseTimeoutMs(int parseTimeoutMs) {
        this.parseTimeoutMs = parseTimeoutMs;
    }
}
