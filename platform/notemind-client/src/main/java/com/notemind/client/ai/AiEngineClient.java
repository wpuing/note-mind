package com.notemind.client.ai;

import java.util.List;
import java.util.Map;

/**
 * 调用 Python AI 引擎（backend :8000）的 Feign/HTTP 契约。
 * <p>
 * 由 service 模块提供具体 HTTP 实现；Java 业务侧仅通过本接口访问解析、切分、
 * 向量化、检索、对话与 Agentic RAG，禁止在 platform 内直接写 LangChain。
 */
public interface AiEngineClient {

    /**
     * 一次性入库文档（解析 + 切分 + 写向量），对应 POST /api/v1/ai/ingest/documents/{documentId}。
     *
     * @param documentId      文档业务 ID
     * @param knowledgeBaseId 所属知识库 ID
     * @param fileBytes       原始文件字节
     * @param filename        原始文件名（用于推断类型）
     * @param chunkParams     切分策略参数
     * @return 入库结果摘要
     * @deprecated 上传已拆分为 split + embed；保留兼容演示路径
     */
    @Deprecated
    IngestResult ingestDocument(
            String documentId,
            String knowledgeBaseId,
            byte[] fileBytes,
            String filename,
            IngestChunkParams chunkParams);

    /**
     * 解析并切分文档，不写入 PGVector，对应 POST /api/v1/ai/chunk/split。
     *
     * @param documentId      文档业务 ID
     * @param knowledgeBaseId 所属知识库 ID
     * @param fileBytes       原始文件字节
     * @param filename        原始文件名
     * @param chunkParams     切分策略参数
     * @return 切分片段列表与统计
     */
    SplitResult splitDocument(
            String documentId,
            String knowledgeBaseId,
            byte[] fileBytes,
            String filename,
            IngestChunkParams chunkParams);

    /**
     * 对给定片段向量化并写入 PG，对应 POST /api/v1/ai/chunk/embed。
     *
     * @param request 待向量化片段请求体
     * @return 向量化结果摘要
     */
    IngestResult embedSegments(EmbedSegmentRequest request);

    /**
     * 分页查询文档在 AI 侧的片段列表，对应 GET .../documents/{documentId}/segments。
     *
     * @param documentId 文档 ID
     * @param keyword    内容关键词（可空）
     * @param page       页码（从 1 起）
     * @param pageSize   每页条数
     * @return 片段分页结果
     */
    SegmentPageResult listDocumentSegments(
            String documentId, String keyword, int page, int pageSize);

    /**
     * 删除知识库下全部向量数据，对应 DELETE .../bases/{knowledgeBaseId}/vectors。
     *
     * @param knowledgeBaseId 知识库 ID
     */
    void deleteKnowledgeBaseVectors(String knowledgeBaseId);

    /**
     * 删除单个文档的向量数据，对应 DELETE .../documents/{documentId}/vectors。
     *
     * @param documentId 文档 ID
     */
    void deleteDocumentVectors(String documentId);

    /**
     * 按片段 ID 批量删除向量，对应 POST .../segments/delete-vectors。
     *
     * @param segmentIds 片段 ID 列表
     */
    void deleteSegmentVectors(java.util.List<String> segmentIds);

    /**
     * 汇总知识库在 PGVector/BM25 侧的向量状态，对应 GET .../vector-status。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return 库级与文档级向量统计
     */
    KnowledgeVectorStatusResult getKnowledgeBaseVectorStatus(String knowledgeBaseId);

    /**
     * 按文件类型解析为纯文本（不切分、不向量化），对应 POST /api/v1/ai/parse/extract。
     *
     * @param fileBytes 文件字节
     * @param filename  文件名
     * @return 解析出的纯文本与元信息
     */
    ParseTextResult extractText(byte[] fileBytes, String filename);

    /**
     * 按检索策略七开关执行检索测试，对应 POST /api/v1/ai/retrieval/test。
     *
     * @param question         用户问题
     * @param knowledgeBaseId  知识库 ID
     * @param strategy         检索策略快照
     * @param chatHistory      可选多轮历史（改写用）
     * @param documentIds      可选限定文档范围
     * @return 命中片段、阶段耗时与改写查询等
     */
    RetrievalTestResult retrievalTest(
            String question,
            String knowledgeBaseId,
            RetrievalStrategyParams strategy,
            List<Map<String, String>> chatHistory,
            List<String> documentIds);

    /**
     * OpenAI 兼容对话补全（Judge / 生成 / 评测），对应 POST /api/v1/ai/llm/chat。
     *
     * @param request 消息、模型与密钥等
     * @return 模型回复正文
     */
    ChatCompletionResult chatCompletion(ChatCompletionRequest request);

    /**
     * 运行 LangGraph Agentic RAG，对应 POST /api/v1/ai/agent/run。
     *
     * @param request 问题、知识库、策略与对话模型凭证
     * @return 回答、sources、Judge 结论与执行时间线
     */
    AgentGraphRunResult runAgentic(AgentGraphRunRequest request);
}
