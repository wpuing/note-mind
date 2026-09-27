package com.notemind.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务默认值集中配置，避免各处硬编码种子 ID / 分页上限等。
 * 对应 application.yml：{@code notemind.defaults.*}
 */
@ConfigurationProperties(prefix = "notemind.defaults")
public class NoteMindDefaultsProperties {

    /** 缺省知识库 ID（与种子 / Python settings.default_knowledge_base_id 对齐） */
    private String knowledgeBaseId = "kb_default";

    /** 新建知识库未指定时的向量模型配置 ID */
    private String embeddingModelId = "m_emb_v4";

    /** 新建知识库未指定时的切分策略 ID */
    private String chunkStrategyId = "cs_recursive";

    /** 新建知识库未指定时的检索策略 ID */
    private String retrievalStrategyId = "rs_baseline_vector";

    /** 列表接口 pageSize 上限 */
    private int maxPageSize = 100;

    /** Prompt 模板允许的场景（空列表表示不校验场景枚举） */
    private List<String> promptScenarios = new ArrayList<>(List.of(
            "问答生成", "查询改写", "Agentic RAG", "评测集构建", "效果评测"));

    /** 文档上传允许的扩展名（小写、无点） */
    private List<String> uploadExtensions = new ArrayList<>(List.of(
            "pdf", "txt", "md", "markdown", "docx", "xlsx", "pptx"));

    /**
     * 获取缺省知识库 ID。
     *
     * @return 知识库 ID
     */
    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    /**
     * 设置缺省知识库 ID。
     *
     * @param knowledgeBaseId 知识库 ID
     */
    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    /**
     * 获取默认向量模型配置 ID。
     *
     * @return 模型配置 ID
     */
    public String getEmbeddingModelId() {
        return embeddingModelId;
    }

    /**
     * 设置默认向量模型配置 ID。
     *
     * @param embeddingModelId 模型配置 ID
     */
    public void setEmbeddingModelId(String embeddingModelId) {
        this.embeddingModelId = embeddingModelId;
    }

    /**
     * 获取默认切分策略 ID。
     *
     * @return 切分策略 ID
     */
    public String getChunkStrategyId() {
        return chunkStrategyId;
    }

    /**
     * 设置默认切分策略 ID。
     *
     * @param chunkStrategyId 切分策略 ID
     */
    public void setChunkStrategyId(String chunkStrategyId) {
        this.chunkStrategyId = chunkStrategyId;
    }

    /**
     * 获取默认检索策略 ID。
     *
     * @return 检索策略 ID
     */
    public String getRetrievalStrategyId() {
        return retrievalStrategyId;
    }

    /**
     * 设置默认检索策略 ID。
     *
     * @param retrievalStrategyId 检索策略 ID
     */
    public void setRetrievalStrategyId(String retrievalStrategyId) {
        this.retrievalStrategyId = retrievalStrategyId;
    }

    /**
     * 获取列表 pageSize 上限。
     *
     * @return 最大每页条数
     */
    public int getMaxPageSize() {
        return maxPageSize;
    }

    /**
     * 设置列表 pageSize 上限。
     *
     * @param maxPageSize 最大每页条数
     */
    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    /**
     * 获取 Prompt 允许的场景列表。
     *
     * @return 场景列表
     */
    public List<String> getPromptScenarios() {
        return promptScenarios;
    }

    /**
     * 设置 Prompt 允许的场景列表。
     *
     * @param promptScenarios 场景列表
     */
    public void setPromptScenarios(List<String> promptScenarios) {
        this.promptScenarios = promptScenarios;
    }

    /**
     * 获取允许上传的扩展名列表。
     *
     * @return 扩展名列表
     */
    public List<String> getUploadExtensions() {
        return uploadExtensions;
    }

    /**
     * 设置允许上传的扩展名列表。
     *
     * @param uploadExtensions 扩展名列表
     */
    public void setUploadExtensions(List<String> uploadExtensions) {
        this.uploadExtensions = uploadExtensions;
    }

    /**
     * 空白则回落默认知识库 ID，否则返回去空白后的入参。
     *
     * @param knowledgeBaseId 入参知识库 ID（可空）
     * @return 有效知识库 ID
     */
    public String resolveKnowledgeBaseId(String knowledgeBaseId) {
        // 空或空白回落本类默认值
        if (knowledgeBaseId == null || knowledgeBaseId.isBlank()) {
            return this.knowledgeBaseId;
        }
        return knowledgeBaseId.trim();
    }

    /**
     * 将 pageSize 钳制到 [1, maxPageSize]（max 至少为 1）。
     *
     * @param pageSize 原始每页条数
     * @return 钳制后的值
     */
    public int clampPageSize(int pageSize) {
        int max = Math.max(1, maxPageSize);
        return Math.min(Math.max(pageSize, 1), max);
    }
}
