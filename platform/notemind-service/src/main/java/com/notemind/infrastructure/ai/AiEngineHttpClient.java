package com.notemind.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.notemind.client.ai.AiEngineClient;
import com.notemind.client.ai.AgentGraphRunRequest;
import com.notemind.client.ai.AgentGraphRunResult;
import com.notemind.client.ai.ChatCompletionRequest;
import com.notemind.client.ai.ChatCompletionResult;
import com.notemind.client.ai.EmbedSegmentRequest;
import com.notemind.client.ai.IngestChunkParams;
import com.notemind.client.ai.IngestResult;
import com.notemind.client.ai.KnowledgeVectorStatusResult;
import com.notemind.client.ai.ParseTextResult;
import com.notemind.client.ai.RetrievalStrategyParams;
import com.notemind.client.ai.RetrievalTestResult;
import com.notemind.client.ai.SegmentPageResult;
import com.notemind.client.ai.SplitResult;
import com.notemind.application.service.system.SystemConfigAsvc;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 引擎 HTTP 客户端实现：Java Service 通过本类调用 Python backend（默认 :8000）。
 * <p>负责文档入库/切分/向量化、检索测试、LLM 对话、Agentic 图运行等，统一附带内部 Token。
 */
@Component
public class AiEngineHttpClient implements AiEngineClient {

    /** JDK HttpClient，强制 HTTP/1.1 以兼容 FastAPI multipart */
    private final HttpClient httpClient;
    /** AI 引擎基址、Token、超时等配置 */
    private final AiEngineProperties properties;
    /** 系统配置：默认 KB、TopK、温度等运行时钳制 */
    private final SystemConfigAsvc systemConfig;
    /** JSON 序列化/反序列化 */
    private final ObjectMapper objectMapper;

    /**
     * 注入配置与依赖，并构建仅使用 HTTP/1.1 的 HttpClient。
     *
     * @param properties   AI 引擎连接配置
     * @param systemConfig 系统配置服务
     * @param objectMapper Jackson 对象映射器
     */
    public AiEngineHttpClient(
            AiEngineProperties properties,
            SystemConfigAsvc systemConfig,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.systemConfig = systemConfig;
        this.objectMapper = objectMapper;
        // multipart 在 HTTP/2 下偶发被 FastAPI 解析丢 file 字段，强制 HTTP/1.1
        // 读取连接超时配置并构建 HttpClient
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    /**
     * 上传文档并完成解析+切分+向量化入库（一步 ingest）。
     *
     * @param documentId      文档 ID
     * @param knowledgeBaseId 知识库 ID
     * @param fileBytes       文件字节
     * @param filename        原始文件名
     * @param chunkParams     切分参数（可空）
     * @return 入库结果
     */
    @Override
    public IngestResult ingestDocument(
            String documentId,
            String knowledgeBaseId,
            byte[] fileBytes,
            String filename,
            IngestChunkParams chunkParams) {
        // 生成 multipart 边界，避免与内容冲突
        String boundary = "----NoteMind" + UUID.randomUUID().toString().replace("-", "");
        byte[] body;
        // 尝试组装 multipart 请求体
        try {
            body = buildMultipart(boundary, filename, fileBytes, knowledgeBaseId, chunkParams);
        // 组装失败则包装为业务异常
        } catch (IOException ex) {
            throw new IllegalStateException("build multipart failed", ex);
        }

        // 读取 AI 引擎基址并拼接入库路径
        String url = trimSlash(properties.getBaseUrl())
                + "/api/v1/ai/ingest/documents/"
                + documentId;

        // 读取超时与 Token，构造 POST 请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-AI-Engine-Token", properties.getToken())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        // 发送 HTTP 请求并处理响应
        try {
            // 同步调用 AI 引擎
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // 4xx/5xx 视为失败
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "AI ingest failed: " + response.statusCode() + " " + response.body());
            }
            return mapIngest(response.body());
        // 业务异常原样抛出
        } catch (IllegalStateException ex) {
            throw ex;
        // 其它异常统一包装
        } catch (Exception ex) {
            throw new IllegalStateException("AI ingest failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * 分页列出某文档在 AI 侧的片段（向量库视角，偏调试）。
     *
     * @param documentId 文档 ID
     * @param keyword    内容关键词（可空）
     * @param page       页码（从 1）
     * @param pageSize   每页条数（钳制到 1～100）
     * @return 片段分页结果
     */
    @Override
    public SegmentPageResult listDocumentSegments(
            String documentId, String keyword, int page, int pageSize) {
        // 读取基址并拼装查询 URL
        StringBuilder url = new StringBuilder(trimSlash(properties.getBaseUrl()))
                .append("/api/v1/ai/knowledge/documents/")
                .append(documentId)
                .append("/segments?page=")
                .append(Math.max(page, 1))
                .append("&page_size=")
                .append(Math.min(Math.max(pageSize, 1), 100));
        // 有关键词时追加 URL 编码查询参数
        if (keyword != null && !keyword.isBlank()) {
            url.append("&keyword=")
                    .append(URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8));
        }
        // 读取超时与 Token，构造 GET 请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .header("X-AI-Engine-Token", properties.getToken())
                .GET()
                .build();
        // 发送请求并映射响应
        try {
            // 同步调用 AI 引擎
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // HTTP 错误码直接失败
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "AI list segments failed: " + response.statusCode() + " " + response.body());
            }
            return mapSegments(response.body());
        // 业务异常透传
        } catch (IllegalStateException ex) {
            throw ex;
        // 网络/解析等异常包装
        } catch (Exception ex) {
            throw new IllegalStateException("AI list segments failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * 删除指定知识库在 PGVector 中的全部向量。
     *
     * @param knowledgeBaseId 知识库 ID；空白则直接返回
     */
    @Override
    public void deleteKnowledgeBaseVectors(String knowledgeBaseId) {
        // 空 ID 无需调用 AI
        if (knowledgeBaseId == null || knowledgeBaseId.isBlank()) {
            return;
        }
        // 读取基址并 URL 编码知识库 ID
        String url = trimSlash(properties.getBaseUrl())
                + "/api/v1/ai/knowledge/bases/"
                + URLEncoder.encode(knowledgeBaseId.trim(), StandardCharsets.UTF_8)
                + "/vectors";
        // 读取超时与 Token，构造 DELETE 请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .header("X-AI-Engine-Token", properties.getToken())
                .DELETE()
                .build();
        // 发送删除请求
        try {
            // 同步调用 AI 引擎
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // 非成功状态码抛错
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "AI delete kb vectors failed: " + response.statusCode() + " " + response.body());
            }
        // 业务异常透传
        } catch (IllegalStateException ex) {
            throw ex;
        // 其它异常包装
        } catch (Exception ex) {
            throw new IllegalStateException("AI delete kb vectors failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * 删除指定文档在向量库中的全部向量。
     *
     * @param documentId 文档 ID；空白则直接返回
     */
    @Override
    public void deleteDocumentVectors(String documentId) {
        // 空文档 ID 跳过
        if (documentId == null || documentId.isBlank()) {
            return;
        }
        // 读取基址并拼装删除路径
        String url = trimSlash(properties.getBaseUrl())
                + "/api/v1/ai/knowledge/documents/"
                + URLEncoder.encode(documentId.trim(), StandardCharsets.UTF_8)
                + "/vectors";
        // 读取超时与 Token
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .header("X-AI-Engine-Token", properties.getToken())
                .DELETE()
                .build();
        // 发送 DELETE
        try {
            // 同步调用 AI 引擎
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // 失败状态码抛错
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "AI delete document vectors failed: " + response.statusCode() + " " + response.body());
            }
        // 业务异常透传
        } catch (IllegalStateException ex) {
            throw ex;
        // 其它异常包装
        } catch (Exception ex) {
            throw new IllegalStateException("AI delete document vectors failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * 查询知识库向量化状态（含各文档统计）。
     *
     * @param knowledgeBaseId 知识库 ID；空白返回 EMPTY 状态
     * @return 向量状态汇总
     */
    @Override
    public KnowledgeVectorStatusResult getKnowledgeBaseVectorStatus(String knowledgeBaseId) {
        // 无知识库 ID 时返回空状态对象
        if (knowledgeBaseId == null || knowledgeBaseId.isBlank()) {
            KnowledgeVectorStatusResult empty = new KnowledgeVectorStatusResult();
            empty.setVectorStatus("EMPTY");
            return empty;
        }
        // 读取基址并拼装 status 路径
        String url = trimSlash(properties.getBaseUrl())
                + "/api/v1/ai/knowledge/bases/"
                + URLEncoder.encode(knowledgeBaseId.trim(), StandardCharsets.UTF_8)
                + "/vector-status";
        // 读取超时与 Token，构造 GET
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .header("X-AI-Engine-Token", properties.getToken())
                .GET()
                .build();
        // 发送并映射
        try {
            // 同步调用 AI 引擎
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // HTTP 错误
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "AI kb vector-status failed: " + response.statusCode() + " " + response.body());
            }
            return mapVectorStatus(response.body());
        // 业务异常透传
        } catch (IllegalStateException ex) {
            throw ex;
        // 其它异常包装
        } catch (Exception ex) {
            throw new IllegalStateException("AI kb vector-status failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * 仅切分文档（不入库向量）：multipart 上传文件与切分参数。
     *
     * @param documentId      文档 ID（可空，由引擎生成）
     * @param knowledgeBaseId 知识库 ID
     * @param fileBytes       文件内容
     * @param filename        文件名
     * @param chunkParams     切分策略参数
     * @return 切分结果（含片段列表）
     */
    @Override
    public SplitResult splitDocument(
            String documentId,
            String knowledgeBaseId,
            byte[] fileBytes,
            String filename,
            IngestChunkParams chunkParams) {
        // 生成 split 专用 multipart 边界
        String boundary = "----NoteMindSplit" + UUID.randomUUID().toString().replace("-", "");
        byte[] body;
        // 组装 multipart：字段 + 文件
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // 通过系统配置解析/回落默认知识库 ID
            writeField(out, boundary, "knowledge_base_id", systemConfig.resolveKnowledgeBaseId(knowledgeBaseId));
            // 有文档 ID 时写入表单字段
            if (documentId != null && !documentId.isBlank()) {
                writeField(out, boundary, "document_id", documentId);
            }
            // 存在切分参数时按字段逐个写入
            if (chunkParams != null) {
                // 块大小
                if (chunkParams.getChunkSize() != null) {
                    writeField(out, boundary, "chunk_size", String.valueOf(chunkParams.getChunkSize()));
                }
                // 块重叠
                if (chunkParams.getChunkOverlap() != null) {
                    writeField(out, boundary, "chunk_overlap", String.valueOf(chunkParams.getChunkOverlap()));
                }
                // 策略类型（递归/父子等）
                if (chunkParams.getStrategyType() != null && !chunkParams.getStrategyType().isBlank()) {
                    writeField(out, boundary, "strategy_type", chunkParams.getStrategyType());
                }
                // 父块大小
                if (chunkParams.getParentChunkSize() != null) {
                    writeField(out, boundary, "parent_chunk_size", String.valueOf(chunkParams.getParentChunkSize()));
                }
                // 子块大小
                if (chunkParams.getChildChunkSize() != null) {
                    writeField(out, boundary, "child_chunk_size", String.valueOf(chunkParams.getChildChunkSize()));
                }
                // 子块重叠
                if (chunkParams.getChildOverlap() != null) {
                    writeField(out, boundary, "child_overlap", String.valueOf(chunkParams.getChildOverlap()));
                }
                // 分隔符 JSON
                if (chunkParams.getSeparatorsJson() != null && !chunkParams.getSeparatorsJson().isBlank()) {
                    writeField(out, boundary, "separators_json", chunkParams.getSeparatorsJson());
                }
                // 切分策略业务 ID
                if (chunkParams.getChunkStrategyId() != null && !chunkParams.getChunkStrategyId().isBlank()) {
                    writeField(out, boundary, "chunk_strategy_id", chunkParams.getChunkStrategyId());
                }
            }
            // 清洗文件名中的引号，写入 file 部件
            String safeName = (filename == null ? "file.bin" : filename).replace("\"", "");
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(
                    ("Content-Disposition: form-data; name=\"file\"; filename=\"" + safeName + "\"\r\n")
                            .getBytes(StandardCharsets.UTF_8));
            out.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.write(fileBytes == null ? new byte[0] : fileBytes);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            body = out.toByteArray();
        // multipart 构建 IO 失败
        } catch (IOException ex) {
            throw new IllegalStateException("build split multipart failed", ex);
        }

        // 读取基址拼切分接口；附 Token 与超时
        String url = trimSlash(properties.getBaseUrl()) + "/api/v1/ai/chunk/split";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-AI-Engine-Token", properties.getToken())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        // 发送切分请求
        try {
            // 同步调用 AI 引擎
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // HTTP 错误
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "AI split failed: " + response.statusCode() + " " + response.body());
            }
            return mapSplit(response.body());
        // 业务异常透传
        } catch (IllegalStateException ex) {
            throw ex;
        // 其它异常包装
        } catch (Exception ex) {
            throw new IllegalStateException("AI split failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * 对已有片段批量向量化并写入 PGVector。
     *
     * @param request 文档 ID、知识库 ID 与片段列表
     * @return 入库结果摘要
     */
    @Override
    public IngestResult embedSegments(EmbedSegmentRequest request) {
        // 校验必填 documentId
        if (request == null || request.getDocumentId() == null || request.getDocumentId().isBlank()) {
            throw new IllegalArgumentException("documentId required");
        }
        // 用 ObjectMapper 构建 JSON 根节点
        ObjectNode root = objectMapper.createObjectNode();
        root.put("document_id", request.getDocumentId());
        // 通过系统配置解析知识库 ID
        root.put("knowledge_base_id", systemConfig.resolveKnowledgeBaseId(request.getKnowledgeBaseId()));
        ArrayNode arr = root.putArray("segments");
        // 遍历片段列表写入数组
        if (request.getSegments() != null) {
            // 逐条映射为引擎所需字段
            for (EmbedSegmentRequest.Item item : request.getSegments()) {
                ObjectNode n = arr.addObject();
                n.put("id", item.getId());
                n.put("content", item.getContent() == null ? "" : item.getContent());
                // 有父片段时写入
                if (item.getParentSegmentId() != null) {
                    n.put("parent_segment_id", item.getParentSegmentId());
                }
                // 有 meta 时转为 JSON 树
                if (item.getMeta() != null) {
                    // 依赖 ObjectMapper 转换 meta Map
                    n.set("meta", objectMapper.valueToTree(item.getMeta()));
                }
            }
        }
        byte[] body;
        // 序列化 JSON 请求体
        try {
            // 依赖 ObjectMapper 写出字节
            body = objectMapper.writeValueAsBytes(root);
        // 序列化失败
        } catch (Exception ex) {
            throw new IllegalStateException("build embed body failed", ex);
        }
        // 读取基址与 Token 构造 POST
        String url = trimSlash(properties.getBaseUrl()) + "/api/v1/ai/chunk/embed";
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .header("Content-Type", "application/json")
                .header("X-AI-Engine-Token", properties.getToken())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        // 发送向量化请求
        try {
            // 同步调用 AI 引擎
            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // HTTP 错误
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "AI embed failed: " + response.statusCode() + " " + response.body());
            }
            return mapIngest(response.body());
        // 业务异常透传
        } catch (IllegalStateException ex) {
            throw ex;
        // 其它异常包装
        } catch (Exception ex) {
            throw new IllegalStateException("AI embed failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * 按片段 ID 列表删除向量（不清 MySQL 片段行）。
     *
     * @param segmentIds 片段 ID 列表；空则直接返回
     */
    @Override
    public void deleteSegmentVectors(List<String> segmentIds) {
        // 空列表无需请求
        if (segmentIds == null || segmentIds.isEmpty()) {
            return;
        }
        // 用 ObjectMapper 构建 JSON
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode arr = root.putArray("segment_ids");
        // 过滤空白 ID
        for (String id : segmentIds) {
            // 仅保留非空 ID
            if (id != null && !id.isBlank()) {
                arr.add(id.trim());
            }
        }
        // 过滤后仍为空则返回
        if (arr.isEmpty()) {
            return;
        }
        byte[] body;
        // 序列化请求体
        try {
            // 依赖 ObjectMapper 写出
            body = objectMapper.writeValueAsBytes(root);
        // 序列化失败
        } catch (Exception ex) {
            throw new IllegalStateException("build delete-segment-vectors body failed", ex);
        }
        // 读取基址与 Token
        String url = trimSlash(properties.getBaseUrl()) + "/api/v1/ai/knowledge/segments/delete-vectors";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .header("Content-Type", "application/json")
                .header("X-AI-Engine-Token", properties.getToken())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        // 发送删除请求
        try {
            // 同步调用 AI 引擎
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // HTTP 错误
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "AI delete segment vectors failed: " + response.statusCode() + " " + response.body());
            }
        // 业务异常透传
        } catch (IllegalStateException ex) {
            throw ex;
        // 其它异常包装
        } catch (Exception ex) {
            throw new IllegalStateException("AI delete segment vectors failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * 解析文档纯文本（管理端预览模式：限页 OCR，避免大文件超时）。
     *
     * @param fileBytes 文件字节
     * @param filename  文件名
     * @return 解析文本结果
     */
    @Override
    public ParseTextResult extractText(byte[] fileBytes, String filename) {
        // 生成 parse 专用边界
        String boundary = "----NoteMindParse" + UUID.randomUUID().toString().replace("-", "");
        byte[] body;
        // 组装预览 multipart
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // 管理端预览：限页 OCR，避免大文件超时
            writeField(out, boundary, "preview", "true");
            String safeName = (filename == null ? "file.bin" : filename).replace("\"", "");
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(
                    ("Content-Disposition: form-data; name=\"file\"; filename=\"" + safeName + "\"\r\n")
                            .getBytes(StandardCharsets.UTF_8));
            out.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.write(fileBytes == null ? new byte[0] : fileBytes);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            body = out.toByteArray();
        // multipart 构建失败
        } catch (IOException ex) {
            throw new IllegalStateException("build parse multipart failed", ex);
        }

        // 取解析超时与读超时的较大值
        long timeoutMs = Math.max(properties.getParseTimeoutMs(), properties.getReadTimeoutMs());
        // 读取基址拼解析接口
        String url = trimSlash(properties.getBaseUrl()) + "/api/v1/ai/parse/extract";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-AI-Engine-Token", properties.getToken())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        // 发送解析请求
        try {
            // 同步调用 AI 引擎
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // HTTP 错误
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "AI parse failed: " + response.statusCode() + " " + response.body());
            }
            return mapParse(response.body());
        // 业务异常透传
        } catch (IllegalStateException ex) {
            throw ex;
        // 其它异常包装
        } catch (Exception ex) {
            throw new IllegalStateException("AI parse failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * 检索测试：按策略七开关调用 Python 混合检索管线。
     *
     * @param question        用户问题
     * @param knowledgeBaseId 知识库 ID
     * @param strategy        检索策略参数（可空则用默认 TopK）
     * @param chatHistory     对话历史（改写用）
     * @param documentIds     可选文档范围过滤
     * @return 命中片段、阶段耗时等
     */
    @Override
    public RetrievalTestResult retrievalTest(
            String question,
            String knowledgeBaseId,
            RetrievalStrategyParams strategy,
            List<Map<String, String>> chatHistory,
            List<String> documentIds) {
        // 整体 try：构建 JSON、发请求、映射结果
        try {
            // 用 ObjectMapper 创建请求体
            ObjectNode body = objectMapper.createObjectNode();
            body.put("question", question == null ? "" : question);
            // 系统配置解析知识库 ID
            body.put("knowledge_base_id", systemConfig.resolveKnowledgeBaseId(knowledgeBaseId));
            // 有策略时写入七开关与阈值
            if (strategy != null) {
                body.put("enable_vector", strategy.isEnableVector());
                body.put("enable_bm25", strategy.isEnableBm25());
                body.put("enable_rrf", strategy.isEnableRrf());
                body.put("enable_rerank", strategy.isEnableRerank());
                body.put("enable_rewrite", strategy.isEnableRewrite());
                body.put("enable_parent_fill", strategy.isEnableParentFill());
                // TopK 相关走系统配置钳制
                body.put("top_k", systemConfig.clampTopK(strategy.getTopK()));
                body.put("rerank_top_n", systemConfig.clampTopK(strategy.getRerankTopN()));
                body.put("vector_top_k", systemConfig.clampTopK(strategy.getVectorTopK()));
                body.put("bm25_top_k", strategy.getBm25TopK());
                body.put("rrf_k", strategy.getRrfK());
                // 余弦阈值可选
                if (strategy.getCosineThreshold() != null) {
                    body.put("cosine_threshold", strategy.getCosineThreshold());
                }
                // 重排阈值可选
                if (strategy.getRerankThreshold() != null) {
                    body.put("rerank_threshold", strategy.getRerankThreshold());
                }
                body.put(
                        "rewrite_mode",
                        strategy.getRewriteMode() == null ? "multi_query" : strategy.getRewriteMode());
                body.put("rewrite_count", strategy.getRewriteCount());
                // 策略 ID
                if (strategy.getStrategyId() != null) {
                    body.put("strategy_id", strategy.getStrategyId());
                }
                // 策略名称
                if (strategy.getStrategyName() != null) {
                    body.put("strategy_name", strategy.getStrategyName());
                }
            // 无策略时仅写入默认 TopK
            } else {
                // 系统配置默认 TopK
                body.put("top_k", systemConfig.defaultTopK());
            }
            // 文档范围过滤
            if (documentIds != null && !documentIds.isEmpty()) {
                ArrayNode docs = body.putArray("document_ids");
                // 逐个加入非空文档 ID
                for (String id : documentIds) {
                    // 跳过空白
                    if (id != null && !id.isBlank()) {
                        docs.add(id.trim());
                    }
                }
            }
            // 对话历史（供改写）
            if (chatHistory != null && !chatHistory.isEmpty()) {
                ArrayNode hist = body.putArray("chat_history");
                // 逐轮写入 role/content
                for (Map<String, String> turn : chatHistory) {
                    ObjectNode t = hist.addObject();
                    // 有 role 才写
                    if (turn.get("role") != null) t.put("role", turn.get("role"));
                    // 有 content 才写
                    if (turn.get("content") != null) t.put("content", turn.get("content"));
                }
            }
            // 依赖 ObjectMapper 序列化
            byte[] payload = objectMapper.writeValueAsBytes(body);
            // 读取基址；超时至少 120s
            String url = trimSlash(properties.getBaseUrl()) + "/api/v1/ai/retrieval/test";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(properties.getReadTimeoutMs(), 120_000)))
                    .header("Content-Type", "application/json")
                    .header("X-AI-Engine-Token", properties.getToken())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();
            // 同步调用 AI 引擎
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // HTTP 错误
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "AI retrieval failed: " + response.statusCode() + " " + response.body());
            }
            return mapRetrieval(response.body(), strategy);
        // 业务异常透传
        } catch (IllegalStateException ex) {
            throw ex;
        // 其它异常包装
        } catch (Exception ex) {
            throw new IllegalStateException("AI retrieval failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * 调用 AI 引擎 LLM 对话补全接口。
     *
     * @param request 模型、温度、消息列表与可选密钥/BaseUrl
     * @return 模型回复内容
     */
    @Override
    public ChatCompletionResult chatCompletion(ChatCompletionRequest request) {
        // 整体 try 覆盖序列化与 HTTP
        try {
            // ObjectMapper 构建请求体
            ObjectNode body = objectMapper.createObjectNode();
            // 模型名可选
            if (request.getModel() != null) body.put("model", request.getModel());
            // 温度：请求指定优先，否则系统默认
            if (request.getTemperature() != null) {
                body.put("temperature", request.getTemperature());
            // 走系统配置聊天温度
            } else {
                body.put("temperature", systemConfig.chatTemperature());
            }
            // 可选透传 api_key
            if (request.getApiKey() != null) body.put("api_key", request.getApiKey());
            // 可选透传 base_url
            if (request.getBaseUrl() != null) body.put("base_url", request.getBaseUrl());
            ArrayNode messages = body.putArray("messages");
            // 有消息列表则逐条写入
            if (request.getMessages() != null) {
                // 映射每条 ChatMessage
                for (ChatCompletionRequest.ChatMessage m : request.getMessages()) {
                    ObjectNode n = messages.addObject();
                    n.put("role", m.getRole() == null ? "user" : m.getRole());
                    n.put("content", m.getContent() == null ? "" : m.getContent());
                }
            }
            // 读取基址；超时至少 120s
            String url = trimSlash(properties.getBaseUrl()) + "/api/v1/ai/llm/chat";
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(properties.getReadTimeoutMs(), 120_000)))
                    .header("Content-Type", "application/json")
                    .header("X-AI-Engine-Token", properties.getToken())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)))
                    .build();
            // 同步调用
            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // HTTP 错误
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "AI llm chat failed: " + response.statusCode() + " " + response.body());
            }
            // ObjectMapper 解析响应 JSON
            JsonNode node = objectMapper.readTree(response.body());
            ChatCompletionResult result = new ChatCompletionResult();
            result.setContent(text(node, "content"));
            return result;
        // 业务异常透传
        } catch (IllegalStateException ex) {
            throw ex;
        // 其它异常包装
        } catch (Exception ex) {
            throw new IllegalStateException("AI llm chat failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * 运行 Agentic RAG 图（LangGraph）：检索/判断/改写/生成。
     *
     * @param request 问题、知识库、轮次、对话模型与检索策略
     * @return 回答、sources、步骤时间线等
     */
    @Override
    public AgentGraphRunResult runAgentic(AgentGraphRunRequest request) {
        // 整体 try
        try {
            // ObjectMapper 构建 body
            ObjectNode body = objectMapper.createObjectNode();
            body.put("question", request.getQuestion() == null ? "" : request.getQuestion());
            // 系统配置解析 KB
            body.put("knowledge_base_id", systemConfig.resolveKnowledgeBaseId(request.getKnowledgeBaseId()));
            // 最大轮次可选
            if (request.getMaxRounds() != null) {
                body.put("max_rounds", request.getMaxRounds());
            }
            // 有 chat 配置时透传模型与密钥
            if (request.getChat() != null) {
                ObjectNode chat = body.putObject("chat");
                // 模型名
                if (request.getChat().getModel() != null) chat.put("model", request.getChat().getModel());
                // API Key
                if (request.getChat().getApiKey() != null) chat.put("api_key", request.getChat().getApiKey());
                // Base URL
                if (request.getChat().getBaseUrl() != null) chat.put("base_url", request.getChat().getBaseUrl());
                // 温度：请求优先
                if (request.getChat().getTemperature() != null) {
                    chat.put("temperature", request.getChat().getTemperature());
                // 否则系统聊天温度
                } else {
                    chat.put("temperature", systemConfig.chatTemperature());
                }
                // Judge 温度固定走系统配置（通常为 0）
                chat.put("judge_temperature", systemConfig.judgeTemperature());
            // 无 chat 时仅写入默认温度
            } else {
                ObjectNode chat = body.putObject("chat");
                chat.put("temperature", systemConfig.chatTemperature());
                chat.put("judge_temperature", systemConfig.judgeTemperature());
            }
            RetrievalStrategyParams strategy = request.getStrategy();
            // 有检索策略时写入嵌套 strategy 对象
            if (strategy != null) {
                ObjectNode s = body.putObject("strategy");
                s.put("enable_vector", strategy.isEnableVector());
                s.put("enable_bm25", strategy.isEnableBm25());
                s.put("enable_rrf", strategy.isEnableRrf());
                s.put("enable_rerank", strategy.isEnableRerank());
                s.put("enable_rewrite", strategy.isEnableRewrite());
                s.put("enable_parent_fill", strategy.isEnableParentFill());
                // TopK 钳制
                s.put("top_k", systemConfig.clampTopK(strategy.getTopK()));
                s.put("rerank_top_n", systemConfig.clampTopK(strategy.getRerankTopN()));
                s.put("vector_top_k", systemConfig.clampTopK(strategy.getVectorTopK()));
                s.put("bm25_top_k", strategy.getBm25TopK());
                s.put("rrf_k", strategy.getRrfK());
                // 余弦阈值
                if (strategy.getCosineThreshold() != null) {
                    s.put("cosine_threshold", strategy.getCosineThreshold());
                }
                // 重排阈值
                if (strategy.getRerankThreshold() != null) {
                    s.put("rerank_threshold", strategy.getRerankThreshold());
                }
                s.put(
                        "rewrite_mode",
                        strategy.getRewriteMode() == null ? "multi_query" : strategy.getRewriteMode());
                s.put("rewrite_count", strategy.getRewriteCount());
                // 策略 ID
                if (strategy.getStrategyId() != null) s.put("strategy_id", strategy.getStrategyId());
                // 策略名
                if (strategy.getStrategyName() != null) s.put("strategy_name", strategy.getStrategyName());
            }
            // 读取基址；Agent 超时至少 300s
            String url = trimSlash(properties.getBaseUrl()) + "/api/v1/ai/agent/run";
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(properties.getReadTimeoutMs(), 300_000)))
                    .header("Content-Type", "application/json")
                    .header("X-AI-Engine-Token", properties.getToken())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)))
                    .build();
            // 同步调用
            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // HTTP 错误
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "AI agent run failed: " + response.statusCode() + " " + response.body());
            }
            return mapAgentGraph(response.body());
        // 业务异常透传
        } catch (IllegalStateException ex) {
            throw ex;
        // 其它异常包装
        } catch (Exception ex) {
            throw new IllegalStateException("AI agent run failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * 将 Agent 运行 JSON 响应映射为 {@link AgentGraphRunResult}。
     *
     * @param raw 响应原文
     * @return 结构化结果
     */
    private AgentGraphRunResult mapAgentGraph(String raw) {
        // 解析 JSON
        try {
            // ObjectMapper 读树
            JsonNode node = objectMapper.readTree(raw);
            AgentGraphRunResult result = new AgentGraphRunResult();
            result.setAnswer(text(node, "answer"));
            result.setConclusion(text(node, "conclusion"));
            result.setRelevant(node.path("relevant").asBoolean(false));
            result.setGrounded(node.path("grounded").asBoolean(false));
            result.setRetrievalRounds(node.path("retrieval_rounds").asInt(0));
            result.setRewriteRounds(node.path("rewrite_rounds").asInt(0));
            List<Map<String, Object>> sources = new ArrayList<>();
            JsonNode src = node.path("sources");
            // sources 为数组时逐条转换
            if (src.isArray()) {
                // ObjectMapper 转 Map
                for (JsonNode item : src) {
                    sources.add(objectMapper.convertValue(item, Map.class));
                }
            }
            result.setSources(sources);
            List<AgentGraphRunResult.Step> steps = new ArrayList<>();
            JsonNode st = node.path("steps");
            // steps 时间线
            if (st.isArray()) {
                // 映射每个节点步骤
                for (JsonNode item : st) {
                    AgentGraphRunResult.Step step = new AgentGraphRunResult.Step();
                    step.setNodeName(text(item, "node_name"));
                    step.setTitle(text(item, "title"));
                    step.setStatus(text(item, "status"));
                    // 有耗时字段
                    if (item.has("latency_ms") && !item.get("latency_ms").isNull()) {
                        step.setLatencyMs(item.get("latency_ms").asInt());
                    }
                    // input 为对象时转换
                    if (item.has("input") && item.get("input").isObject()) {
                        // ObjectMapper 转 Map
                        step.setInput(objectMapper.convertValue(item.get("input"), Map.class));
                    }
                    // output 为对象时转换
                    if (item.has("output") && item.get("output").isObject()) {
                        // ObjectMapper 转 Map
                        step.setOutput(objectMapper.convertValue(item.get("output"), Map.class));
                    }
                    steps.add(step);
                }
            }
            result.setSteps(steps);
            return result;
        // 解析失败带上原文
        } catch (Exception ex) {
            throw new IllegalStateException("AI agent response parse failed: " + raw, ex);
        }
    }

    /**
     * 将检索测试 JSON 映射为 {@link RetrievalTestResult}；策略 ID/名可回退请求侧。
     *
     * @param raw      响应原文
     * @param strategy 请求时策略（用于回填）
     * @return 检索结果
     */
    private RetrievalTestResult mapRetrieval(String raw, RetrievalStrategyParams strategy) {
        // 解析检索响应
        try {
            // ObjectMapper 读树
            JsonNode node = objectMapper.readTree(raw);
            RetrievalTestResult result = new RetrievalTestResult();
            result.setQuestion(text(node, "question"));
            result.setKnowledgeBaseId(text(node, "knowledge_base_id"));
            result.setScoreScale(text(node, "score_scale"));
            result.setScoreScaleLabel(text(node, "score_scale_label"));
            result.setStrategyId(text(node, "strategy_id"));
            result.setStrategyName(text(node, "strategy_name"));
            // 响应缺策略 ID 时用请求侧回填
            if (result.getStrategyId() == null && strategy != null) {
                result.setStrategyId(strategy.getStrategyId());
            }
            // 响应缺策略名时回填
            if (result.getStrategyName() == null && strategy != null) {
                result.setStrategyName(strategy.getStrategyName());
            }
            List<String> rewritten = new ArrayList<>();
            JsonNode rq = node.path("rewritten_queries");
            // 改写查询列表
            if (rq.isArray()) {
                // 收集非空文本
                for (JsonNode q : rq) {
                    // 跳过 null 节点
                    if (q != null && !q.isNull()) rewritten.add(q.asText());
                }
            }
            result.setRewrittenQueries(rewritten);
            List<String> retrievalQs = new ArrayList<>();
            JsonNode rqs = node.path("retrieval_queries");
            // 实际检索用查询列表
            if (rqs.isArray()) {
                // 收集文本
                for (JsonNode q : rqs) {
                    // 跳过 null
                    if (q != null && !q.isNull()) retrievalQs.add(q.asText());
                }
            }
            result.setRetrievalQueries(retrievalQs);
            List<Map<String, Object>> stages = new ArrayList<>();
            JsonNode st = node.path("stages");
            // 阶段耗时
            if (st.isArray()) {
                // ObjectMapper 转 Map
                for (JsonNode item : st) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = objectMapper.convertValue(item, Map.class);
                    stages.add(m == null ? new HashMap<>() : m);
                }
            }
            result.setStages(stages);
            List<Map<String, Object>> sources = new ArrayList<>();
            JsonNode src = node.path("sources");
            // 兼容旧字段 segments
            if (!src.isArray()) {
                src = node.path("segments");
            }
            // 命中明细
            if (src.isArray()) {
                // ObjectMapper 转 Map
                for (JsonNode item : src) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = objectMapper.convertValue(item, Map.class);
                    sources.add(m == null ? new HashMap<>() : m);
                }
            }
            result.setSources(sources);
            result.setHitCount(node.path("hit_count").asInt(sources.size()));
            // 总耗时可选
            if (node.has("elapsed_ms") && !node.get("elapsed_ms").isNull()) {
                result.setElapsedMs(node.path("elapsed_ms").asInt());
            }
            return result;
        // 解析失败
        } catch (Exception ex) {
            throw new IllegalStateException("AI retrieval response parse failed: " + raw, ex);
        }
    }

    /**
     * 将解析接口 JSON 映射为 {@link ParseTextResult}。
     *
     * @param raw 响应原文
     * @return 解析结果
     */
    private ParseTextResult mapParse(String raw) {
        // 解析 parse 响应
        try {
            // ObjectMapper 读树
            JsonNode node = objectMapper.readTree(raw);
            ParseTextResult result = new ParseTextResult();
            result.setFilename(text(node, "filename"));
            result.setFileType(text(node, "file_type"));
            // 字符数可选
            if (node.has("char_count") && !node.get("char_count").isNull()) {
                result.setCharCount(node.path("char_count").asInt());
            }
            result.setText(text(node, "text"));
            result.setStatus(text(node, "status"));
            result.setHint(text(node, "hint"));
            return result;
        // 解析失败
        } catch (Exception ex) {
            throw new IllegalStateException("AI parse response parse failed: " + raw, ex);
        }
    }

    /**
     * 将知识库向量状态 JSON 映射为 {@link KnowledgeVectorStatusResult}。
     *
     * @param raw 响应原文
     * @return 状态汇总（含文档明细）
     */
    private KnowledgeVectorStatusResult mapVectorStatus(String raw) {
        // 解析 vector-status
        try {
            // ObjectMapper 读树
            JsonNode node = objectMapper.readTree(raw);
            KnowledgeVectorStatusResult result = new KnowledgeVectorStatusResult();
            result.setKnowledgeBaseId(text(node, "knowledge_base_id"));
            result.setSegmentCount(node.path("segment_count").asLong(0));
            result.setVectorDoneCount(node.path("vector_done_count").asLong(0));
            result.setVectorPendingCount(node.path("vector_pending_count").asLong(0));
            result.setVectorFailedCount(node.path("vector_failed_count").asLong(0));
            result.setVectorStatus(text(node, "vector_status"));
            List<KnowledgeVectorStatusResult.DocumentVectorStat> docs = new ArrayList<>();
            JsonNode arr = node.path("documents");
            // 文档级统计数组
            if (arr.isArray()) {
                // 映射每个文档
                for (JsonNode item : arr) {
                    KnowledgeVectorStatusResult.DocumentVectorStat d =
                            new KnowledgeVectorStatusResult.DocumentVectorStat();
                    d.setDocumentId(text(item, "document_id"));
                    d.setSegmentCount(item.path("segment_count").asLong(0));
                    d.setVectorDoneCount(item.path("vector_done_count").asLong(0));
                    d.setVectorPendingCount(item.path("vector_pending_count").asLong(0));
                    d.setVectorFailedCount(item.path("vector_failed_count").asLong(0));
                    d.setVectorStatus(text(item, "vector_status"));
                    docs.add(d);
                }
            }
            result.setDocuments(docs);
            return result;
        // 解析失败
        } catch (Exception ex) {
            throw new IllegalStateException("AI vector-status parse failed: " + raw, ex);
        }
    }

    /**
     * 将片段分页 JSON 映射为 {@link SegmentPageResult}。
     *
     * @param raw 响应原文
     * @return 分页片段列表
     */
    private SegmentPageResult mapSegments(String raw) {
        // 解析 segments 分页
        try {
            // ObjectMapper 读树
            JsonNode node = objectMapper.readTree(raw);
            SegmentPageResult page = new SegmentPageResult();
            page.setTotal(node.path("total").asLong(0));
            page.setPage(node.path("page").asInt(1));
            page.setPageSize(node.path("page_size").asInt(node.path("pageSize").asInt(8)));
            List<SegmentPageResult.SegmentItem> records = new ArrayList<>();
            JsonNode arr = node.path("records");
            // records 数组
            if (arr.isArray()) {
                // 映射每条片段
                for (JsonNode item : arr) {
                    SegmentPageResult.SegmentItem seg = new SegmentPageResult.SegmentItem();
                    seg.setSegmentId(text(item, "segment_id"));
                    seg.setDocumentId(text(item, "document_id"));
                    seg.setKnowledgeBaseId(text(item, "knowledge_base_id"));
                    seg.setParentSegmentId(text(item, "parent_segment_id"));
                    seg.setContent(text(item, "content"));
                    seg.setContentPreview(text(item, "content_preview"));
                    // chunk_index 可选
                    if (item.has("chunk_index") && !item.get("chunk_index").isNull()) {
                        seg.setChunkIndex(item.get("chunk_index").asInt());
                    }
                    records.add(seg);
                }
            }
            page.setRecords(records);
            return page;
        // 解析失败
        } catch (Exception ex) {
            throw new IllegalStateException("AI segments response parse failed: " + raw, ex);
        }
    }

    /**
     * 构建 ingest 用 multipart/form-data 字节（知识库字段 + 切分参数 + 文件）。
     *
     * @param boundary        边界串
     * @param filename        文件名
     * @param fileBytes       文件内容
     * @param knowledgeBaseId 知识库 ID
     * @param chunkParams     切分参数（可空）
     * @return multipart 原始字节
     * @throws IOException 写出失败
     */
    private static byte[] buildMultipart(
            String boundary,
            String filename,
            byte[] fileBytes,
            String knowledgeBaseId,
            IngestChunkParams chunkParams)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String safeName = filename.replace("\"", "");

        writeField(out, boundary, "knowledge_base_id", knowledgeBaseId);
        // 有切分参数时写入各可选字段
        if (chunkParams != null) {
            // 块大小
            if (chunkParams.getChunkSize() != null) {
                writeField(out, boundary, "chunk_size", String.valueOf(chunkParams.getChunkSize()));
            }
            // 重叠
            if (chunkParams.getChunkOverlap() != null) {
                writeField(out, boundary, "chunk_overlap", String.valueOf(chunkParams.getChunkOverlap()));
            }
            // 策略类型
            if (chunkParams.getStrategyType() != null && !chunkParams.getStrategyType().isBlank()) {
                writeField(out, boundary, "strategy_type", chunkParams.getStrategyType());
            }
            // 父块
            if (chunkParams.getParentChunkSize() != null) {
                writeField(out, boundary, "parent_chunk_size", String.valueOf(chunkParams.getParentChunkSize()));
            }
            // 子块
            if (chunkParams.getChildChunkSize() != null) {
                writeField(out, boundary, "child_chunk_size", String.valueOf(chunkParams.getChildChunkSize()));
            }
            // 子重叠
            if (chunkParams.getChildOverlap() != null) {
                writeField(out, boundary, "child_overlap", String.valueOf(chunkParams.getChildOverlap()));
            }
            // 分隔符
            if (chunkParams.getSeparatorsJson() != null && !chunkParams.getSeparatorsJson().isBlank()) {
                writeField(out, boundary, "separators_json", chunkParams.getSeparatorsJson());
            }
            // 策略业务 ID
            if (chunkParams.getChunkStrategyId() != null && !chunkParams.getChunkStrategyId().isBlank()) {
                writeField(out, boundary, "chunk_strategy_id", chunkParams.getChunkStrategyId());
            }
        }

        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(
                ("Content-Disposition: form-data; name=\"file\"; filename=\"" + safeName + "\"\r\n")
                        .getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(fileBytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));

        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /**
     * 向 multipart 流写入单个文本表单字段。
     *
     * @param out      输出流
     * @param boundary 边界
     * @param name     字段名
     * @param value    字段值
     * @throws IOException 写出失败
     */
    private static void writeField(ByteArrayOutputStream out, String boundary, String name, String value)
            throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(
                ("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将 ingest/embed 响应 JSON 映射为 {@link IngestResult}。
     *
     * @param raw 响应原文
     * @return 入库摘要
     */
    private IngestResult mapIngest(String raw) {
        // 解析 ingest 响应
        try {
            // ObjectMapper 读树
            JsonNode node = objectMapper.readTree(raw);
            IngestResult result = new IngestResult();
            result.setDocumentId(text(node, "document_id"));
            result.setKnowledgeBaseId(text(node, "knowledge_base_id"));
            result.setSegmentCount(node.path("segment_count").asInt(0));
            // 字符数可选
            if (node.has("char_count") && !node.get("char_count").isNull()) {
                result.setCharCount(node.path("char_count").asInt());
            }
            result.setStatus(text(node, "status"));
            return result;
        // 解析失败
        } catch (Exception ex) {
            throw new IllegalStateException("AI ingest response parse failed: " + raw, ex);
        }
    }

    /**
     * 将切分响应 JSON 映射为 {@link SplitResult}（含片段明细）。
     *
     * @param raw 响应原文
     * @return 切分结果
     */
    private SplitResult mapSplit(String raw) {
        // 解析 split 响应
        try {
            // ObjectMapper 读树
            JsonNode node = objectMapper.readTree(raw);
            SplitResult result = new SplitResult();
            result.setDocumentId(text(node, "document_id"));
            result.setKnowledgeBaseId(text(node, "knowledge_base_id"));
            result.setStrategyType(text(node, "strategy_type"));
            result.setChunkStrategyId(text(node, "chunk_strategy_id"));
            // 字符数可选
            if (node.has("char_count") && !node.get("char_count").isNull()) {
                result.setCharCount(node.path("char_count").asInt());
            }
            result.setSegmentCount(node.path("segment_count").asInt(0));
            result.setVectorizableCount(node.path("vectorizable_count").asInt(0));
            result.setStatus(text(node, "status"));
            List<SplitResult.SplitSegment> segs = new ArrayList<>();
            JsonNode arr = node.path("segments");
            // 片段数组
            if (arr.isArray()) {
                // 映射每个切分片段
                for (JsonNode item : arr) {
                    SplitResult.SplitSegment s = new SplitResult.SplitSegment();
                    s.setId(text(item, "id"));
                    s.setParentId(text(item, "parent_id"));
                    s.setSegmentType(text(item, "segment_type"));
                    s.setSegmentIndex(item.path("segment_index").asInt(0));
                    s.setContent(text(item, "content"));
                    // token 数可选
                    if (item.has("content_tokens") && !item.get("content_tokens").isNull()) {
                        s.setContentTokens(item.path("content_tokens").asInt());
                    }
                    // 页码可选（可能为数字或对象）
                    if (item.has("page") && !item.get("page").isNull()) {
                        // ObjectMapper 转 Object
                        s.setPage(objectMapper.treeToValue(item.get("page"), Object.class));
                    }
                    s.setVectorStatus(text(item, "vector_status"));
                    // meta 对象可选
                    if (item.has("meta") && item.get("meta").isObject()) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> meta =
                                // ObjectMapper 转 Map
                                objectMapper.convertValue(item.get("meta"), java.util.Map.class);
                        s.setMeta(meta);
                    }
                    segs.add(s);
                }
            }
            result.setSegments(segs);
            return result;
        // 解析失败
        } catch (Exception ex) {
            throw new IllegalStateException("AI split response parse failed: " + raw, ex);
        }
    }

    /**
     * 安全读取 JSON 文本字段；缺失或 null 返回 null。
     *
     * @param node  JSON 节点
     * @param field 字段名
     * @return 文本或 null
     */
    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /**
     * 去掉 URL 末尾斜杠；空入参时回退到配置基址或本地默认 8000。
     *
     * @param url 原始 URL
     * @return 规范化基址
     */
    private String trimSlash(String url) {
        // 入参为空时回退配置
        if (url == null || url.isBlank()) {
            // 读取配置中的 baseUrl
            String configured = properties.getBaseUrl();
            return (configured == null || configured.isBlank())
                    ? "http://127.0.0.1:8000"
                    : configured.replaceAll("/+$", "");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
