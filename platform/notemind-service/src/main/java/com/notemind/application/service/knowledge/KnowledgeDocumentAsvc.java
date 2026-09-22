package com.notemind.application.service.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notemind.client.ai.AiEngineClient;
import com.notemind.client.ai.EmbedSegmentRequest;
import com.notemind.client.ai.IngestChunkParams;
import com.notemind.client.ai.IngestResult;
import com.notemind.client.ai.ParseTextResult;
import com.notemind.client.ai.SplitResult;
import com.notemind.common.result.PageResult;
import com.notemind.common.util.DateTimes;
import com.notemind.infrastructure.ai.StorageProperties;
import com.notemind.application.service.system.SystemConfigAsvc;
import com.notemind.interfaces.knowledge.vo.ChunkStrategyVo;
import com.notemind.interfaces.knowledge.vo.DocumentParsedTextVo;
import com.notemind.interfaces.knowledge.vo.KnowledgeDocumentUpdateRequest;
import com.notemind.interfaces.knowledge.vo.KnowledgeDocumentVo;
import com.notemind.interfaces.knowledge.vo.KnowledgeSegmentVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 知识文档应用服务：上传、解析预览、切分向量化与批量删除。
 */
@Service
public class KnowledgeDocumentAsvc {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentAsvc.class);
    /** EMBEDDING 卡住超过此时长则允许回收后重新向量化。 */
    private static final Duration EMBEDDING_STALE = Duration.ofHours(2);

    private final JdbcTemplate jdbcTemplate;
    private final AiEngineClient aiEngineClient;
    private final StorageProperties storageProperties;
    private final SystemConfigAsvc systemConfig;
    private final ObjectMapper objectMapper;
    private final ChunkStrategyAsvc chunkStrategyAsvc;
    private final KnowledgeSegmentAsvc knowledgeSegmentAsvc;

    /**
     * 构造 KnowledgeDocumentAsvc 并注入依赖。
     * @param jdbcTemplate 参数 jdbcTemplate
     * @param aiEngineClient 参数 aiEngineClient
     * @param storageProperties 参数 storageProperties
     * @param systemConfig 参数 systemConfig
     * @param objectMapper 参数 objectMapper
     * @param chunkStrategyAsvc 参数 chunkStrategyAsvc
     * @param knowledgeSegmentAsvc 参数 knowledgeSegmentAsvc
     */
    public KnowledgeDocumentAsvc(
            JdbcTemplate jdbcTemplate,
            AiEngineClient aiEngineClient,
            StorageProperties storageProperties,
            SystemConfigAsvc systemConfig,
            ObjectMapper objectMapper,
            ChunkStrategyAsvc chunkStrategyAsvc,
            KnowledgeSegmentAsvc knowledgeSegmentAsvc) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiEngineClient = aiEngineClient;
        this.storageProperties = storageProperties;
        this.systemConfig = systemConfig;
        this.objectMapper = objectMapper;
        this.chunkStrategyAsvc = chunkStrategyAsvc;
        this.knowledgeSegmentAsvc = knowledgeSegmentAsvc;
    }

    /** 仅落盘 + 绑知识库，不解析/切分/向量化。 */
    public KnowledgeDocumentVo upload(MultipartFile file, String knowledgeBaseId) {
        // 条件判断
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file required");
        }
        String originalName = file.getOriginalFilename();
        // 条件判断
        if (originalName == null || originalName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filename required");
        }
        String ext = extensionOf(originalName);
        // 条件判断
        if (!isAllowedExt(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported file type: " + ext);
        }

        String kbId = normalizeKb(knowledgeBaseId);
        ensureKnowledgeBase(kbId);

        String documentId = UUID.randomUUID().toString().replace("-", "");
        String fileType = mapFileType(ext);
        String title = stripExtension(originalName);
        Path dest = resolveDest(documentId, originalName);

        // 尝试执行
        try {
            Files.createDirectories(dest.getParent());
            file.transferTo(dest.toFile());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "save file failed: " + ex.getMessage());
        }

        long size = file.getSize();
        LocalDateTime now = LocalDateTime.now();
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                """
                INSERT INTO t_knowledge_document
                  (id, create_time, update_time, deleted, knowledge_base_id, title, file_name, file_type,
                   file_path, file_size, parse_status)
                VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?, ?, 'UPLOADED')
                """,
                documentId,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                kbId,
                title,
                originalName,
                fileType,
                dest.toAbsolutePath().toString(),
                size);

        return getById(documentId);
    }

    /**
     * chunk：业务处理。
     * @param documentId 参数 documentId
     * @param chunkStrategyId 参数 chunkStrategyId
     * @return 返回结果
     */
    public KnowledgeDocumentVo chunk(String documentId, String chunkStrategyId) {
        Map<String, Object> row = loadDocRow(documentId);
        assertNotEmbedding(row);
        String kbId = String.valueOf(row.get("knowledge_base_id"));
        String fileName = String.valueOf(row.get("file_name"));
        Path path = resolveSafeExistingFile(row);

        IngestChunkParams chunkParams = resolveChunkParams(kbId, chunkStrategyId, null, null);
        // 重切分前必须清掉旧向量；失败则中止，避免新旧向量并存召回
        clearRemoteVectors(documentId);
        knowledgeSegmentAsvc.softDeleteByDocument(documentId);
        try {
            byte[] bytes = Files.readAllBytes(path);
            // 调用 aiEngineClient.splitDocument
            SplitResult split = aiEngineClient.splitDocument(documentId, kbId, bytes, fileName, chunkParams);
            // 调用 knowledgeSegmentAsvc.replaceDocumentSegments
            knowledgeSegmentAsvc.replaceDocumentSegments(documentId, kbId, split.getSegments());

            Map<String, Object> metaMap = readMeta(row.get("meta_json"));
            metaMap.put("chunkSize", chunkParams.getChunkSize());
            metaMap.put("chunkOverlap", chunkParams.getChunkOverlap());
            metaMap.put("chunkStrategyId", chunkParams.getChunkStrategyId());
            metaMap.put("strategyType", chunkParams.getStrategyType());
            metaMap.put("segmentCount", split.getSegmentCount());
            // 条件判断
            if (split.getCharCount() != null) {
                metaMap.put("charCount", split.getCharCount());
            }
            metaMap.put("vectorizableCount", split.getVectorizableCount());

            // 调用 jdbcTemplate.update
            jdbcTemplate.update(
                    """
                    UPDATE t_knowledge_document
                    SET parse_status = 'CHUNKED', error_message = NULL, meta_json = ?,
                        parse_text = NULL, update_time = ?
                    WHERE id = ?
                    """,
                    // 调用 objectMapper.writeValueAsString
                    objectMapper.writeValueAsString(metaMap),
                    Timestamp.valueOf(LocalDateTime.now()),
                    documentId);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            String err = truncate(ex.getMessage(), 1000);
            // 调用 jdbcTemplate.update
            jdbcTemplate.update(
                    "UPDATE t_knowledge_document SET parse_status = 'FAILED', error_message = ?, update_time = ? WHERE id = ?",
                    err,
                    Timestamp.valueOf(LocalDateTime.now()),
                    documentId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "chunk failed: " + err);
        }
        return getById(documentId);
    }

    /**
     * vectorize：业务处理。
     * @param documentId 参数 documentId
     * @return 返回结果
     */
    public KnowledgeDocumentVo vectorize(String documentId) {
        Map<String, Object> row = loadDocRow(documentId);
        String kbId = String.valueOf(row.get("knowledge_base_id"));
        // 仅向量化待处理 / 失败片段；切分后不自动向量化
        List<Map<String, Object>> segs = knowledgeSegmentAsvc.listVectorizableRows(documentId);
        // 条件判断
        if (segs.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "no pending/failed segments to vectorize; please chunk first");
        }

        // CAS：仅允许非 EMBEDDING 状态进入；超时卡住的 EMBEDDING 先回收
        reclaimStaleEmbedding(documentId);
        int claimed = jdbcTemplate.update(
                """
                UPDATE t_knowledge_document
                SET parse_status = 'EMBEDDING', update_time = ?
                WHERE id = ? AND deleted = 0
                  AND parse_status <> 'EMBEDDING'
                """,
                Timestamp.valueOf(LocalDateTime.now()),
                documentId);
        // 条件判断
        if (claimed == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该文档正在向量化，请稍后再试");
        }

        List<String> ids = new ArrayList<>();
        // 尝试执行
        try {
            EmbedSegmentRequest req = new EmbedSegmentRequest();
            req.setDocumentId(documentId);
            req.setKnowledgeBaseId(kbId);
            // 遍历处理
            for (Map<String, Object> s : segs) {
                EmbedSegmentRequest.Item item = new EmbedSegmentRequest.Item();
                String sid = String.valueOf(s.get("id"));
                item.setId(sid);
                item.setContent(s.get("content") == null ? "" : String.valueOf(s.get("content")));
                // 条件判断
                if (s.get("parent_id") != null) {
                    item.setParentSegmentId(String.valueOf(s.get("parent_id")));
                }
                Map<String, Object> meta = new HashMap<>();
                meta.put("segment_index", s.get("segment_index"));
                meta.put("segment_type", s.get("segment_type"));
                // 条件判断
                if (s.get("meta_json") != null) {
                    // 尝试执行
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> existing =
                                // 调用 objectMapper.readValue
                                objectMapper.readValue(String.valueOf(s.get("meta_json")), Map.class);
                        // 条件判断
                        if (existing != null) meta.putAll(existing);
                    } catch (Exception ignored) {
                    }
                }
                item.setMeta(meta);
                req.getSegments().add(item);
                ids.add(sid);
            }
            // 父块一并提交：Python 只写 BM25，不入向量表，供 parent_fill
            for (Map<String, Object> s : knowledgeSegmentAsvc.listParentRows(documentId)) {
                EmbedSegmentRequest.Item item = new EmbedSegmentRequest.Item();
                String sid = String.valueOf(s.get("id"));
                item.setId(sid);
                item.setContent(s.get("content") == null ? "" : String.valueOf(s.get("content")));
                Map<String, Object> meta = new HashMap<>();
                meta.put("segment_index", s.get("segment_index"));
                meta.put("segment_type", "PARENT");
                item.setMeta(meta);
                req.getSegments().add(item);
            }

            // 先写入/覆盖向量；失败时保留旧向量，避免「先删后挂」导致检索全空
            IngestResult result = aiEngineClient.embedSegments(req);
            knowledgeSegmentAsvc.markVectorDone(ids);

            Map<String, Object> metaMap = readMeta(row.get("meta_json"));
            // 调用 knowledgeSegmentAsvc.countByDocument
            metaMap.put("segmentCount", knowledgeSegmentAsvc.countByDocument(documentId));
            metaMap.put("vectorCount", result.getSegmentCount());
            metaMap.put("aiStatus", result.getStatus() == null ? "ready" : result.getStatus());

            // 调用 jdbcTemplate.update
            jdbcTemplate.update(
                    """
                    UPDATE t_knowledge_document
                    SET parse_status = 'READY', error_message = NULL, meta_json = ?, update_time = ?
                    WHERE id = ?
                    """,
                    // 调用 objectMapper.writeValueAsString
                    objectMapper.writeValueAsString(metaMap),
                    Timestamp.valueOf(LocalDateTime.now()),
                    documentId);
        } catch (Exception ex) {
            // 调用 knowledgeSegmentAsvc.markVectorFailed
            knowledgeSegmentAsvc.markVectorFailed(ids);
            String err = truncate(ex.getMessage(), 1000);
            // 调用 jdbcTemplate.update
            jdbcTemplate.update(
                    "UPDATE t_knowledge_document SET parse_status = 'FAILED', error_message = ?, update_time = ? WHERE id = ?",
                    err,
                    Timestamp.valueOf(LocalDateTime.now()),
                    documentId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "vectorize failed: " + err);
        }
        return getById(documentId);
    }

    /**
     * clearVectors：业务处理。
     * @param documentId 参数 documentId
     * @return 返回结果
     */
    public KnowledgeDocumentVo clearVectors(String documentId) {
        Map<String, Object> row = loadDocRow(documentId);
        assertNotEmbedding(row);
        try {
            aiEngineClient.deleteDocumentVectors(documentId);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "clear vectors failed: " + truncate(ex.getMessage(), 500));
        }
        // 调用 knowledgeSegmentAsvc.resetVectorStatus
        knowledgeSegmentAsvc.resetVectorStatus(documentId);
        // 调用 knowledgeSegmentAsvc.countByDocument
        int segs = knowledgeSegmentAsvc.countByDocument(documentId);
        String status = segs > 0 ? "CHUNKED" : "UPLOADED";
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                "UPDATE t_knowledge_document SET parse_status = ?, error_message = NULL, update_time = ? WHERE id = ?",
                status,
                Timestamp.valueOf(LocalDateTime.now()),
                documentId);
        return getById(documentId);
    }

    /**
     * 更新记录。
     * @param id 参数 id
     * @param req 参数 req
     * @return 返回结果
     */
    public KnowledgeDocumentVo update(String id, KnowledgeDocumentUpdateRequest req) {
        loadDocRow(id);
        // 条件判断
        if (req == null || req.getTitle() == null || req.getTitle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title required");
        }
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                "UPDATE t_knowledge_document SET title = ?, update_time = ? WHERE id = ? AND deleted = 0",
                req.getTitle().trim(),
                Timestamp.valueOf(LocalDateTime.now()),
                id);
        return getById(id);
    }

    /**
     * reupload：业务处理。
     * @param documentId 参数 documentId
     * @param file 参数 file
     * @return 返回结果
     */
    public KnowledgeDocumentVo reupload(String documentId, MultipartFile file) {
        Map<String, Object> row = loadDocRow(documentId);
        assertNotEmbedding(row);
        // 先清向量再改元数据/文件，失败则中止，避免旧向量残留
        clearRemoteVectors(documentId);
        knowledgeSegmentAsvc.softDeleteByDocument(documentId);

        // 条件判断
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file required");
        }
        String originalName = file.getOriginalFilename();
        // 条件判断
        if (originalName == null || originalName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filename required");
        }
        String ext = extensionOf(originalName);
        // 条件判断
        if (!isAllowedExt(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported file type: " + ext);
        }

        // 删除旧文件（仅 upload-dir 内）
        try {
            Path old = resolveSafeFileForDelete(row);
            if (old != null && Files.isRegularFile(old)) {
                Files.deleteIfExists(old);
            }
        } catch (Exception ignored) {
        }

        Path dest = resolveDest(documentId, originalName);
        // 尝试执行
        try {
            Files.createDirectories(dest.getParent());
            file.transferTo(dest.toFile());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "save file failed: " + ex.getMessage());
        }

        String title = stripExtension(originalName);
        Map<String, Object> metaMap = new HashMap<>();
        // 尝试执行
        try {
            // 调用 jdbcTemplate.update
            jdbcTemplate.update(
                    """
                    UPDATE t_knowledge_document
                    SET title = ?, file_name = ?, file_type = ?, file_path = ?, file_size = ?,
                        parse_status = 'UPLOADED', parse_text = NULL, error_message = NULL,
                        meta_json = ?, update_time = ?
                    WHERE id = ?
                    """,
                    title,
                    originalName,
                    mapFileType(ext),
                    dest.toAbsolutePath().toString(),
                    file.getSize(),
                    // 调用 objectMapper.writeValueAsString
                    objectMapper.writeValueAsString(metaMap),
                    Timestamp.valueOf(LocalDateTime.now()),
                    documentId);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "reupload update failed");
        }
        return getById(documentId);
    }

    /**
     * 删除记录。
     * @param documentId 参数 documentId
     */
    public void delete(String documentId) {
        Map<String, Object> row = loadDocRow(documentId);
        assertNotEmbedding(row);
        // 先清向量再软删，失败可重试，避免本地已删无法再调清向量 API
        clearRemoteVectors(documentId);
        softDeleteLocal(documentId, row);
    }

    /** 批量删除：先清向量再软删；向量失败中止该条且不软删。 */
    public int batchDelete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ids required");
        }
        int n = 0;
        List<String> vectorFailed = new ArrayList<>();
        List<String> conflictIds = new ArrayList<>();
        for (String raw : ids) {
            if (raw == null || raw.isBlank()) continue;
            String id = raw.trim();
            try {
                Map<String, Object> row = loadDocRow(id);
                assertNotEmbedding(row);
                clearRemoteVectors(id);
                softDeleteLocal(id, row);
                n++;
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                    continue;
                }
                if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                    conflictIds.add(id);
                    continue;
                }
                if (ex.getStatusCode() == HttpStatus.BAD_GATEWAY) {
                    vectorFailed.add(id);
                    continue;
                }
                throw ex;
            }
        }
        if (!vectorFailed.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "部分文档向量清理失败，未删除: " + String.join(",", vectorFailed)
                            + (!conflictIds.isEmpty()
                                    ? "；另有向量化中未删: " + String.join(",", conflictIds)
                                    : "")
                            + (n > 0 ? "；已删除 " + n + " 个" : ""));
        }
        if (!conflictIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "部分文档正在向量化，未删除: " + String.join(",", conflictIds)
                            + (n > 0 ? "；已删除 " + n + " 个" : ""));
        }
        return n;
    }

    /**
     * softDeleteLocal：业务处理。
     * @param documentId 参数 documentId
     * @param row 参数 row
     */
    private void softDeleteLocal(String documentId, Map<String, Object> row) {
        // 调用 knowledgeSegmentAsvc.softDeleteByDocument
        knowledgeSegmentAsvc.softDeleteByDocument(documentId);
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                "UPDATE t_knowledge_document SET deleted = 1, delete_time = ?, update_time = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now()),
                documentId);
        // 条件判断
        if (row == null || row.get("file_path") == null) {
            return;
        }
        try {
            Path path = resolveSafeFileForDelete(row);
            if (path == null) {
                return;
            }
            Files.deleteIfExists(path);
            Path parent = path.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                try (var stream = Files.list(parent)) {
                    if (stream.findAny().isEmpty()) {
                        Files.deleteIfExists(parent);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 删除远端向量；失败抛错，避免静默残留孤儿向量。
     */
    private void clearRemoteVectors(String documentId) {
        try {
            aiEngineClient.deleteDocumentVectors(documentId);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "清除向量失败，请稍后重试或检查 AI 引擎: " + truncate(ex.getMessage(), 200));
        }
    }

    /** 将超时仍 EMBEDDING 的文档回收为 FAILED，避免永久挡向量化。 */
    private void reclaimStaleEmbedding(String documentId) {
        Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now().minus(EMBEDDING_STALE));
        int n = jdbcTemplate.update(
                """
                UPDATE t_knowledge_document
                SET parse_status = 'FAILED',
                    error_message = '向量化超时已自动回收，请重试',
                    update_time = ?
                WHERE id = ? AND deleted = 0
                  AND parse_status = 'EMBEDDING' AND update_time < ?
                """,
                Timestamp.valueOf(LocalDateTime.now()),
                documentId,
                cutoff);
        if (n > 0) {
            log.warn("reclaimed stale EMBEDDING document {}", documentId);
        }
    }

    private void assertNotEmbedding(Map<String, Object> row) {
        String status = row.get("parse_status") == null ? "" : String.valueOf(row.get("parse_status"));
        if (!"EMBEDDING".equalsIgnoreCase(status)) {
            return;
        }
        // 超时卡住则先回收，否则拒绝删除/切分
        Object idObj = row.get("id");
        if (idObj != null) {
            reclaimStaleEmbedding(String.valueOf(idObj));
            Map<String, Object> refreshed = loadDocRow(String.valueOf(idObj));
            String again =
                    refreshed.get("parse_status") == null
                            ? ""
                            : String.valueOf(refreshed.get("parse_status"));
            if (!"EMBEDDING".equalsIgnoreCase(again)) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "文档正在向量化，请稍后再试");
    }

    /**
     * DownloadPayload：业务处理。
     * @param resource 参数 resource
     * @param filename 参数 filename
     * @return 返回结果
     */
    public record DownloadPayload(Resource resource, String filename) {}

    /**
     * download：业务处理。
     * @param documentId 参数 documentId
     * @return 返回结果
     */
    public DownloadPayload download(String documentId) {
        Map<String, Object> row = loadDocRow(documentId);
        String fileName = row.get("file_name") == null ? "file" : String.valueOf(row.get("file_name"));
        Path path = resolveSafeExistingFile(row);
        return new DownloadPayload(new FileSystemResource(path.toFile()), fileName);
    }

    /**
     * listSegments：业务处理。
     * @param documentId 参数 documentId
     * @param keyword 参数 keyword
     * @param page 参数 page
     * @param pageSize 参数 pageSize
     * @return 返回结果
     */
    public PageResult<KnowledgeSegmentVo> listSegments(
            String documentId, String keyword, int page, int pageSize) {
        getById(documentId);
        // 调用 knowledgeSegmentAsvc.page
        return knowledgeSegmentAsvc.page(null, documentId, null, null, keyword, page, pageSize);
    }

    /**
     * 分页查询。
     * @param knowledgeBaseId 参数 knowledgeBaseId
     * @param title 参数 title
     * @param parseStatus 参数 parseStatus
     * @param vectorIssue 参数 vectorIssue
     * @param page 参数 page
     * @param pageSize 参数 pageSize
     * @return 返回结果
     */
    public PageResult<KnowledgeDocumentVo> page(
            String knowledgeBaseId, String title, String parseStatus, String vectorIssue, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        StringBuilder where = new StringBuilder(" WHERE d.deleted = 0");
        ArrayList<Object> args = new ArrayList<>();
        // 条件判断
        if (knowledgeBaseId != null && !knowledgeBaseId.isBlank()) {
            where.append(" AND d.knowledge_base_id = ?");
            args.add(knowledgeBaseId.trim());
        }
        // 条件判断
        if (title != null && !title.isBlank()) {
            where.append(" AND d.title LIKE ?");
            args.add("%" + title.trim() + "%");
        }
        // 条件判断
        if (parseStatus != null && !parseStatus.isBlank()) {
            where.append(" AND d.parse_status = ?");
            args.add(parseStatus.trim());
        }

        Long total;
        List<KnowledgeDocumentVo> records;

        // 条件判断
        if (vectorIssue != null && !vectorIssue.isBlank()
                && knowledgeBaseId != null && !knowledgeBaseId.isBlank()) {
            String issue = vectorIssue.trim().toUpperCase(Locale.ROOT);
            String issueWhere = "";
            // 条件判断
            if ("FAILED".equals(issue)) {
                issueWhere = """
                         AND (
                           d.parse_status = 'FAILED'
                           OR COALESCE(vs.failed_cnt, 0) > 0
                         )
                        """;
            } else if ("PENDING".equals(issue)) {
                issueWhere = " AND COALESCE(vs.pending_cnt, 0) > 0 ";
            } else if ("PARTIAL".equals(issue) || "ISSUE".equals(issue)) {
                issueWhere = """
                         AND (
                           d.parse_status = 'FAILED'
                           OR COALESCE(vs.pending_cnt, 0) > 0
                           OR COALESCE(vs.failed_cnt, 0) > 0
                           OR (
                             COALESCE(vs.done_cnt, 0) > 0
                             AND (COALESCE(vs.pending_cnt, 0) > 0 OR COALESCE(vs.failed_cnt, 0) > 0)
                           )
                         )
                        """;
            }
            String fromJoin = """
                    FROM t_knowledge_document d
                    LEFT JOIN t_knowledge_base kb ON kb.id = d.knowledge_base_id AND kb.deleted = 0
                    LEFT JOIN (
                      SELECT document_id,
                             SUM(CASE WHEN vector_status = 'DONE' THEN 1 ELSE 0 END) AS done_cnt,
                             SUM(CASE WHEN vector_status = 'PENDING' THEN 1 ELSE 0 END) AS pending_cnt,
                             SUM(CASE WHEN vector_status = 'FAILED' THEN 1 ELSE 0 END) AS failed_cnt,
                             COUNT(1) AS seg_cnt
                      FROM t_knowledge_segment
                      WHERE deleted = 0
                      GROUP BY document_id
                    ) vs ON vs.document_id = d.id
                    """;
            // 调用 jdbcTemplate.queryForObject
            total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) " + fromJoin + where + issueWhere,
                    Long.class,
                    args.toArray());
            String listSql =
                    """
                    SELECT d.id, d.knowledge_base_id, kb.name AS knowledge_base_name,
                           d.title, d.file_name, d.file_type, d.file_size,
                           d.parse_status, d.error_message, d.meta_json, d.create_time
                    """
                            + fromJoin
                            + where
                            + issueWhere
                            + " ORDER BY d.create_time DESC LIMIT ? OFFSET ?";
            ArrayList<Object> listArgs = new ArrayList<>(args);
            listArgs.add(safeSize);
            listArgs.add((safePage - 1) * safeSize);
            // 调用 jdbcTemplate.query
            records = jdbcTemplate.query(listSql, documentMapper(), listArgs.toArray());
            enrichDocuments(records);
        } else {
            // 调用 jdbcTemplate.queryForObject
            total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM t_knowledge_document d" + where, Long.class, args.toArray());
            String listSql =
                    """
                    SELECT d.id, d.knowledge_base_id, kb.name AS knowledge_base_name,
                           d.title, d.file_name, d.file_type, d.file_size,
                           d.parse_status, d.error_message, d.meta_json, d.create_time
                    FROM t_knowledge_document d
                    LEFT JOIN t_knowledge_base kb ON kb.id = d.knowledge_base_id AND kb.deleted = 0
                    """
                            + where
                            + " ORDER BY d.create_time DESC LIMIT ? OFFSET ?";
            ArrayList<Object> listArgs = new ArrayList<>(args);
            listArgs.add(safeSize);
            listArgs.add((safePage - 1) * safeSize);
            // 调用 jdbcTemplate.query
            records = jdbcTemplate.query(listSql, documentMapper(), listArgs.toArray());
            enrichDocuments(records);
        }
        long totalVal = total == null ? 0L : total;
        return PageResult.of(totalVal, safePage, safeSize, records);
    }

    /**
     * list：业务处理。
     * @param knowledgeBaseId 参数 knowledgeBaseId
     * @param title 参数 title
     * @param parseStatus 参数 parseStatus
     * @return 返回结果
     */
    public List<KnowledgeDocumentVo> list(String knowledgeBaseId, String title, String parseStatus) {
        return page(knowledgeBaseId, title, parseStatus, null, 1, 1000).getRecords();
    }

    /**
     * 按 ID 查询详情。
     * @param id 参数 id
     * @return 返回结果
     */
    public KnowledgeDocumentVo getById(String id) {
        // 调用 jdbcTemplate.query
        List<KnowledgeDocumentVo> rows = jdbcTemplate.query(
                """
                SELECT d.id, d.knowledge_base_id, kb.name AS knowledge_base_name,
                       d.title, d.file_name, d.file_type, d.file_size,
                       d.parse_status, d.error_message, d.meta_json, d.create_time
                FROM t_knowledge_document d
                LEFT JOIN t_knowledge_base kb ON kb.id = d.knowledge_base_id AND kb.deleted = 0
                WHERE d.deleted = 0 AND d.id = ?
                """,
                documentMapper(),
                id);
        // 条件判断
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found");
        }
        KnowledgeDocumentVo vo = rows.get(0);
        enrichDocuments(List.of(vo));
        return vo;
    }

    /**
     * previewParsedText：业务处理。
     * @param id 参数 id
     * @return 返回结果
     */
    public DocumentParsedTextVo previewParsedText(String id) {
        Map<String, Object> row = loadDocRow(id);
        String filePath = row.get("file_path") == null ? null : String.valueOf(row.get("file_path"));
        String fileName = row.get("file_name") == null ? "file" : String.valueOf(row.get("file_name"));
        // 条件判断
        if (filePath == null || filePath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file path missing");
        }
        Path path = resolveSafeExistingFile(row);
        // 尝试执行
        try {
            byte[] bytes = Files.readAllBytes(path);
            // 调用 aiEngineClient.extractText
            ParseTextResult parsed = aiEngineClient.extractText(bytes, fileName);
            DocumentParsedTextVo vo = new DocumentParsedTextVo();
            vo.setDocumentId(String.valueOf(row.get("id")));
            vo.setTitle(row.get("title") == null ? null : String.valueOf(row.get("title")));
            vo.setFileName(fileName);
            vo.setFileType(row.get("file_type") == null
                    ? parsed.getFileType()
                    : String.valueOf(row.get("file_type")));
            vo.setCharCount(parsed.getCharCount() == null
                    ? (parsed.getText() == null ? 0 : parsed.getText().length())
                    : parsed.getCharCount());
            vo.setText(parsed.getText() == null ? "" : parsed.getText());
            vo.setHint(parsed.getHint());

            Map<String, Object> metaMap = readMeta(row.get("meta_json"));
            metaMap.put("charCount", vo.getCharCount());
            String status = String.valueOf(row.get("parse_status"));
            // 条件判断
            if ("UPLOADED".equalsIgnoreCase(status)) {
                status = "PARSED";
            }
            // 调用 jdbcTemplate.update
            jdbcTemplate.update(
                    """
                    UPDATE t_knowledge_document
                    SET meta_json = ?, parse_text = ?, parse_status = ?, update_time = ?
                    WHERE id = ?
                    """,
                    // 调用 objectMapper.writeValueAsString
                    objectMapper.writeValueAsString(metaMap),
                    truncate(vo.getText(), 60000),
                    status,
                    Timestamp.valueOf(LocalDateTime.now()),
                    id);
            return vo;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "parse failed: " + truncate(ex.getMessage(), 500));
        }
    }

    /**
     * enrichDocuments：业务处理。
     * @param records 参数 records
     */
    private void enrichDocuments(List<KnowledgeDocumentVo> records) {
        // 条件判断
        if (records == null || records.isEmpty()) return;
        List<String> ids = records.stream().map(KnowledgeDocumentVo::getId).filter(Objects::nonNull).toList();
        // 条件判断
        if (ids.isEmpty()) return;

        Map<String, long[]> stats = new HashMap<>();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql =
                "SELECT document_id, "
                        + "SUM(CASE WHEN vector_status = 'DONE' THEN 1 ELSE 0 END) AS done_cnt, "
                        + "SUM(CASE WHEN vector_status = 'PENDING' THEN 1 ELSE 0 END) AS pending_cnt, "
                        + "SUM(CASE WHEN vector_status = 'FAILED' THEN 1 ELSE 0 END) AS failed_cnt, "
                        + "COUNT(1) AS seg_cnt "
                        + "FROM t_knowledge_segment "
                        + "WHERE deleted = 0 AND document_id IN (" + placeholders + ") "
                        + "GROUP BY document_id";
        // 调用 jdbcTemplate.queryForList
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, ids.toArray());
        // 遍历处理
        for (Map<String, Object> row : rows) {
            stats.put(
                    String.valueOf(row.get("document_id")),
                    new long[] {
                        ((Number) row.getOrDefault("done_cnt", 0)).longValue(),
                        ((Number) row.getOrDefault("pending_cnt", 0)).longValue(),
                        ((Number) row.getOrDefault("failed_cnt", 0)).longValue(),
                        ((Number) row.getOrDefault("seg_cnt", 0)).longValue()
                    });
        }

        // 遍历处理
        for (KnowledgeDocumentVo vo : records) {
            long[] s = stats.get(vo.getId());
            long doneL = s == null ? 0L : s[0];
            long pendingL = s == null ? 0L : s[1];
            long failedL = s == null ? 0L : s[2];
            int segs = s == null ? 0 : (int) s[3];
            // 条件判断
            if (segs > 0) {
                vo.setSegmentCount(segs);
            }
            vo.setVectorDoneCount(doneL);
            vo.setVectorPendingCount(pendingL);
            vo.setVectorFailedCount(failedL);
            // 条件判断
            if ("FAILED".equalsIgnoreCase(vo.getParseStatus())) {
                vo.setVectorStatus("FAILED");
            } else if (doneL > 0 && pendingL == 0 && failedL == 0) {
                vo.setVectorStatus("READY");
            } else if (doneL > 0 || pendingL > 0 || failedL > 0) {
                vo.setVectorStatus(pendingL > 0 || failedL > 0 ? "PARTIAL" : "READY");
            } else if (segs > 0) {
                vo.setVectorStatus("EMPTY");
            } else {
                // 无 MySQL 片段时不再按文档打 AI/PG 全库状态（列表 N×RTT）；标 EMPTY
                vo.setVectorStatus("EMPTY");
                vo.setSegmentCount(0);
                vo.setVectorDoneCount(0L);
                vo.setVectorPendingCount(0L);
                vo.setVectorFailedCount(0L);
            }
        }
    }

    /**
     * resolveChunkParams：业务处理。
     * @param kbId 参数 kbId
     * @param chunkStrategyId 参数 chunkStrategyId
     * @param chunkSize 参数 chunkSize
     * @param chunkOverlap 参数 chunkOverlap
     * @return 返回结果
     */
    private IngestChunkParams resolveChunkParams(
            String kbId, String chunkStrategyId, Integer chunkSize, Integer chunkOverlap) {
        ChunkStrategyVo strategy = null;
        // 条件判断
        if (chunkStrategyId != null && !chunkStrategyId.isBlank()) {
            // 尝试执行
            try {
                // 调用 chunkStrategyAsvc.getById
                strategy = chunkStrategyAsvc.getById(chunkStrategyId.trim());
            } catch (Exception ignored) {
                strategy = null;
            }
        }
        // 条件判断
        if (strategy == null) {
            // 调用 jdbcTemplate.query
            String kbStrategyId = jdbcTemplate.query(
                    "SELECT chunk_strategy_id FROM t_knowledge_base WHERE id = ? AND deleted = 0",
                    rs -> rs.next() ? rs.getString(1) : null,
                    kbId);
            // 条件判断
            if (kbStrategyId != null && !kbStrategyId.isBlank()) {
                // 尝试执行
                try {
                    // 调用 chunkStrategyAsvc.getById
                    strategy = chunkStrategyAsvc.getById(kbStrategyId);
                } catch (Exception ignored) {
                    strategy = null;
                }
            }
        }
        // 条件判断
        if (strategy == null) {
            // 尝试执行
            try {
                // 调用 chunkStrategyAsvc.getDefaultOrFallback
                strategy = chunkStrategyAsvc.getDefaultOrFallback();
            } catch (Exception ignored) {
                strategy = null;
            }
        }

        IngestChunkParams params = new IngestChunkParams();
        // 条件判断
        if (strategy != null) {
            params.setChunkStrategyId(strategy.getId());
            params.setStrategyType(strategy.getStrategyType());
            params.setChunkSize(strategy.getChunkSize());
            params.setChunkOverlap(strategy.getChunkOverlap());
            params.setParentChunkSize(strategy.getParentChunkSize());
            params.setChildChunkSize(strategy.getChildChunkSize());
            params.setChildOverlap(strategy.getChildOverlap());
            // 尝试执行
            try {
                // 条件判断
                if (strategy.getSeparators() != null) {
                    // 调用 objectMapper.writeValueAsString
                    params.setSeparatorsJson(objectMapper.writeValueAsString(strategy.getSeparators()));
                }
            } catch (Exception ignored) {
            }
        }
        // 条件判断
        if (chunkSize != null) params.setChunkSize(chunkSize);
        // 条件判断
        if (chunkOverlap != null) params.setChunkOverlap(chunkOverlap);
        // 条件判断
        if (params.getChunkSize() == null) params.setChunkSize(500);
        // 条件判断
        if (params.getChunkOverlap() == null) params.setChunkOverlap(50);
        // 条件判断
        if (params.getStrategyType() == null) params.setStrategyType("RECURSIVE");
        return params;
    }

    /**
     * loadDocRow：业务处理。
     * @param id 参数 id
     * @return 返回结果
     */
    private Map<String, Object> loadDocRow(String id) {
        // 调用 jdbcTemplate.queryForList
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, knowledge_base_id, title, file_name, file_type, file_path, file_size,
                       parse_status, meta_json, error_message
                FROM t_knowledge_document
                WHERE deleted = 0 AND id = ?
                """,
                id);
        // 条件判断
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found");
        }
        return rows.get(0);
    }

    /**
     * readMeta：业务处理。
     * @param metaJsonObj 参数 metaJsonObj
     * @return 返回结果
     */
    private Map<String, Object> readMeta(Object metaJsonObj) {
        Map<String, Object> metaMap = new HashMap<>();
        // 条件判断
        if (metaJsonObj == null) return metaMap;
        String metaJson = String.valueOf(metaJsonObj);
        // 条件判断
        if (metaJson.isBlank() || "null".equals(metaJson)) return metaMap;
        // 尝试执行
        try {
            @SuppressWarnings("unchecked")
            // 调用 objectMapper.readValue
            Map<String, Object> existing = objectMapper.readValue(metaJson, Map.class);
            // 条件判断
            if (existing != null) metaMap.putAll(existing);
        } catch (Exception ignored) {
        }
        return metaMap;
    }

    /**
     * normalizeKb：业务处理。
     * @param knowledgeBaseId 参数 knowledgeBaseId
     * @return 返回结果
     */
    private String normalizeKb(String knowledgeBaseId) {
        // 调用 systemConfig.resolveKnowledgeBaseId
        String resolved = systemConfig.resolveKnowledgeBaseId(knowledgeBaseId);
        // 条件判断
        if ("default".equals(resolved)) {
            // 调用 systemConfig.knowledgeBaseId
            return systemConfig.knowledgeBaseId();
        }
        return resolved;
    }

    /**
     * isAllowedExt：业务处理。
     * @param ext 参数 ext
     * @return 返回结果
     */
    private boolean isAllowedExt(String ext) {
        // 调用 systemConfig.uploadExtensions
        List<String> allowed = systemConfig.uploadExtensions();
        // 条件判断
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        String e = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        // 遍历处理
        for (String a : allowed) {
            // 条件判断
            if (a != null && a.equalsIgnoreCase(e)) {
                return true;
            }
        }
        return false;
    }

    /**
     * ensureKnowledgeBase：业务处理。
     * @param kbId 参数 kbId
     */
    private void ensureKnowledgeBase(String kbId) {
        // 调用 jdbcTemplate.queryForObject
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_knowledge_base WHERE id = ? AND deleted = 0", Integer.class, kbId);
        // 条件判断
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "knowledge base not found: " + kbId);
        }
    }

    /**
     * resolveDest：业务处理。
     * @param documentId 参数 documentId
     * @param filename 参数 filename
     * @return 返回结果
     */
    private Path resolveDest(String documentId, String filename) {
        Path root = uploadRoot();
        String safeName = Paths.get(filename).getFileName().toString();
        return root.resolve(documentId).resolve(safeName);
    }

    private Path uploadRoot() {
        return Paths.get(storageProperties.getUploadDir()).toAbsolutePath().normalize();
    }

    /** 读取 DB 中的 file_path，必须落在 upload-dir 根下。 */
    private Path resolveSafeExistingFile(Map<String, Object> row) {
        Object raw = row.get("file_path");
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file path missing");
        }
        Path root = uploadRoot();
        Path path;
        try {
            path = Paths.get(String.valueOf(raw)).toAbsolutePath().normalize();
            if (Files.exists(path)) {
                path = path.toRealPath();
            }
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid file path");
        }
        Path rootReal = root;
        try {
            if (Files.exists(root)) {
                rootReal = root.toRealPath();
            }
        } catch (Exception ignored) {
        }
        if (!path.startsWith(rootReal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "file path outside upload dir");
        }
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "file not found on disk");
        }
        return path;
    }

    /** 删除用：越界或不存在则返回 null（不抛），避免误删任意路径。 */
    private Path resolveSafeFileForDelete(Map<String, Object> row) {
        Object raw = row.get("file_path");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        try {
            Path root = uploadRoot();
            Path path = Paths.get(String.valueOf(raw)).toAbsolutePath().normalize();
            if (Files.exists(path)) {
                path = path.toRealPath();
            }
            Path rootReal = Files.exists(root) ? root.toRealPath() : root;
            if (!path.startsWith(rootReal)) {
                return null;
            }
            return path;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * documentMapper：业务处理。
     * @return 返回结果
     */
    private RowMapper<KnowledgeDocumentVo> documentMapper() {
        return (rs, rowNum) -> mapRow(rs);
    }

    /**
     * mapRow：业务处理。
     * @param rs 参数 rs
     * @return 返回结果
     */
    private KnowledgeDocumentVo mapRow(ResultSet rs) throws SQLException {
        KnowledgeDocumentVo vo = new KnowledgeDocumentVo();
        vo.setId(rs.getString("id"));
        vo.setKnowledgeBaseId(rs.getString("knowledge_base_id"));
        // 尝试执行
        try {
            vo.setKnowledgeBaseName(rs.getString("knowledge_base_name"));
        } catch (SQLException ignored) {
            vo.setKnowledgeBaseName(null);
        }
        vo.setTitle(rs.getString("title"));
        vo.setFileName(rs.getString("file_name"));
        vo.setFileType(rs.getString("file_type"));
        long size = rs.getLong("file_size");
        vo.setFileSize(rs.wasNull() ? null : size);
        vo.setParseStatus(rs.getString("parse_status"));
        vo.setErrorMessage(rs.getString("error_message"));
        fillMetaFields(vo, rs.getString("meta_json"));
        Timestamp ct = rs.getTimestamp("create_time");
        vo.setCreateTime(DateTimes.format(ct));
        return vo;
    }

    /**
     * fillMetaFields：业务处理。
     * @param vo 参数 vo
     * @param metaJson 参数 metaJson
     */
    private void fillMetaFields(KnowledgeDocumentVo vo, String metaJson) {
        // 条件判断
        if (metaJson == null || metaJson.isBlank()) {
            return;
        }
        // 尝试执行
        try {
            // 调用 objectMapper.readTree
            var node = objectMapper.readTree(metaJson);
            // 条件判断
            if (node.has("segmentCount") && !node.get("segmentCount").isNull()) {
                vo.setSegmentCount(node.path("segmentCount").asInt());
            }
            // 条件判断
            if (node.has("chunkSize") && !node.get("chunkSize").isNull()) {
                vo.setChunkSize(node.path("chunkSize").asInt());
            }
            // 条件判断
            if (node.has("chunkOverlap") && !node.get("chunkOverlap").isNull()) {
                vo.setChunkOverlap(node.path("chunkOverlap").asInt());
            }
            // 条件判断
            if (node.has("chunkStrategyId") && !node.get("chunkStrategyId").isNull()) {
                vo.setChunkStrategyId(node.path("chunkStrategyId").asText());
            }
            // 条件判断
            if (node.has("strategyType") && !node.get("strategyType").isNull()) {
                vo.setStrategyType(node.path("strategyType").asText());
            }
            // 条件判断
            if (node.has("charCount") && !node.get("charCount").isNull()) {
                vo.setCharCount(node.path("charCount").asInt());
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * extensionOf：业务处理。
     * @param name 参数 name
     * @return 返回结果
     */
    private static String extensionOf(String name) {
        int i = name.lastIndexOf('.');
        // 条件判断
        if (i < 0) {
            return "";
        }
        return name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * mapFileType：业务处理。
     * @param ext 参数 ext
     * @return 返回结果
     */
    private static String mapFileType(String ext) {
        return switch (ext) {
            // 匹配分支
            case "markdown" -> "MARKDOWN";
            // 匹配分支
            case "md" -> "MARKDOWN";
            // 匹配分支
            case "pdf" -> "PDF";
            // 匹配分支
            case "docx" -> "WORD";
            // 匹配分支
            case "xlsx" -> "EXCEL";
            // 匹配分支
            case "pptx" -> "PPT";
            // 匹配分支
            case "txt" -> "TXT";
            default -> ext.toUpperCase(Locale.ROOT);
        };
    }

    /**
     * stripExtension：业务处理。
     * @param name 参数 name
     * @return 返回结果
     */
    private static String stripExtension(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    /**
     * truncate：业务处理。
     * @param s 参数 s
     * @param max 参数 max
     * @return 返回结果
     */
    private static String truncate(String s, int max) {
        // 条件判断
        if (s == null) {
            return "unknown error";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
