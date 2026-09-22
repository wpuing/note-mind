package com.notemind.application.service.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notemind.client.ai.AiEngineClient;
import com.notemind.client.ai.SplitResult;
import com.notemind.common.result.PageResult;
import com.notemind.common.util.DateTimes;
import com.notemind.interfaces.knowledge.vo.KnowledgeSegmentUpdateRequest;
import com.notemind.interfaces.knowledge.vo.KnowledgeSegmentVo;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识片段应用服务：片段筛选分页、编辑删除与批量删除。
 */
@Service
public class KnowledgeSegmentAsvc {

    private final JdbcTemplate jdbcTemplate;
    private final AiEngineClient aiEngineClient;
    private final ObjectMapper objectMapper;

    /**
     * 构造 KnowledgeSegmentAsvc 并注入依赖。
     * @param jdbcTemplate 参数 jdbcTemplate
     * @param aiEngineClient 参数 aiEngineClient
     * @param objectMapper 参数 objectMapper
     */
    public KnowledgeSegmentAsvc(
            JdbcTemplate jdbcTemplate, AiEngineClient aiEngineClient, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiEngineClient = aiEngineClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 分页查询。
     * @param knowledgeBaseId 参数 knowledgeBaseId
     * @param documentId 参数 documentId
     * @param segmentType 参数 segmentType
     * @param vectorStatus 参数 vectorStatus
     * @param keyword 参数 keyword
     * @param page 参数 page
     * @param pageSize 参数 pageSize
     * @return 返回结果
     */
    public PageResult<KnowledgeSegmentVo> page(
            String knowledgeBaseId,
            String documentId,
            String segmentType,
            String vectorStatus,
            String keyword,
            int page,
            int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        StringBuilder where = new StringBuilder(" WHERE s.deleted = 0");
        ArrayList<Object> args = new ArrayList<>();
        // 条件判断
        if (knowledgeBaseId != null && !knowledgeBaseId.isBlank()) {
            where.append(" AND s.knowledge_base_id = ?");
            args.add(knowledgeBaseId.trim());
        }
        // 条件判断
        if (documentId != null && !documentId.isBlank()) {
            where.append(" AND s.document_id = ?");
            args.add(documentId.trim());
        }
        // 条件判断
        if (segmentType != null && !segmentType.isBlank()) {
            where.append(" AND s.segment_type = ?");
            args.add(segmentType.trim().toUpperCase());
        }
        // 条件判断
        if (vectorStatus != null && !vectorStatus.isBlank()) {
            where.append(" AND s.vector_status = ?");
            args.add(vectorStatus.trim().toUpperCase());
        }
        // 条件判断
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND s.content LIKE ?");
            args.add("%" + keyword.trim() + "%");
        }

        // 调用 jdbcTemplate.queryForObject
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_knowledge_segment s" + where, Long.class, args.toArray());
        String listSql =
                """
                SELECT s.id, s.knowledge_base_id, kb.name AS knowledge_base_name,
                       s.document_id, d.title AS document_title,
                       s.parent_id, s.segment_type, s.segment_index, s.content, s.content_tokens,
                       s.meta_json, s.vector_status, s.vector_id, s.manually_edited, s.create_time
                FROM t_knowledge_segment s
                LEFT JOIN t_knowledge_base kb ON kb.id = s.knowledge_base_id AND kb.deleted = 0
                LEFT JOIN t_knowledge_document d ON d.id = s.document_id AND d.deleted = 0
                """
                        + where
                        + " ORDER BY s.document_id, s.segment_index, s.create_time LIMIT ? OFFSET ?";
        ArrayList<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeSize);
        listArgs.add((safePage - 1) * safeSize);
        // 调用 jdbcTemplate.query
        List<KnowledgeSegmentVo> records = jdbcTemplate.query(listSql, segmentMapper(), listArgs.toArray());
        return PageResult.of(total == null ? 0L : total, safePage, safeSize, records);
    }

    /**
     * 按 ID 查询详情。
     * @param id 参数 id
     * @return 返回结果
     */
    public KnowledgeSegmentVo getById(String id) {
        // 调用 jdbcTemplate.query
        List<KnowledgeSegmentVo> rows = jdbcTemplate.query(
                """
                SELECT s.id, s.knowledge_base_id, kb.name AS knowledge_base_name,
                       s.document_id, d.title AS document_title,
                       s.parent_id, s.segment_type, s.segment_index, s.content, s.content_tokens,
                       s.meta_json, s.vector_status, s.vector_id, s.manually_edited, s.create_time
                FROM t_knowledge_segment s
                LEFT JOIN t_knowledge_base kb ON kb.id = s.knowledge_base_id AND kb.deleted = 0
                LEFT JOIN t_knowledge_document d ON d.id = s.document_id AND d.deleted = 0
                WHERE s.deleted = 0 AND s.id = ?
                """,
                segmentMapper(),
                id);
        // 条件判断
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "segment not found");
        }
        return rows.get(0);
    }

    /**
     * 更新记录。
     * @param id 参数 id
     * @param req 参数 req
     * @return 返回结果
     */
    public KnowledgeSegmentVo update(String id, KnowledgeSegmentUpdateRequest req) {
        // 条件判断
        if (req == null || req.getContent() == null || req.getContent().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content required");
        }
        KnowledgeSegmentVo existing = getById(id);
        String content = req.getContent().trim();
        boolean wasDone = "DONE".equalsIgnoreCase(existing.getVectorStatus());
        // 条件判断
        if (wasDone) {
            try {
                aiEngineClient.deleteSegmentVectors(List.of(id));
            } catch (ResponseStatusException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "清除旧向量失败，内容未修改，请稍后重试");
            }
        }
        String newStatus = "PARENT".equalsIgnoreCase(existing.getSegmentType())
                ? "SKIPPED"
                : "PENDING";
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                """
                UPDATE t_knowledge_segment
                SET content = ?, content_tokens = ?, vector_status = ?, vector_id = NULL,
                    manually_edited = 1, update_time = ?
                WHERE id = ? AND deleted = 0
                """,
                content,
                content.length(),
                newStatus,
                Timestamp.valueOf(LocalDateTime.now()),
                id);
        // 条件判断
        if (wasDone || "READY".equalsIgnoreCase(docStatus(existing.getDocumentId()))) {
            // 调用 jdbcTemplate.update
            jdbcTemplate.update(
                    "UPDATE t_knowledge_document SET parse_status = 'CHUNKED', update_time = ? WHERE id = ? AND deleted = 0",
                    Timestamp.valueOf(LocalDateTime.now()),
                    existing.getDocumentId());
        }
        return getById(id);
    }

    /**
     * 删除记录。
     * @param id 参数 id
     */
    public void delete(String id) {
        KnowledgeSegmentVo existing = getById(id);
        try {
            aiEngineClient.deleteSegmentVectors(List.of(id));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "清除片段向量失败，未删除，请稍后重试");
        }
        jdbcTemplate.update(
                "UPDATE t_knowledge_segment SET deleted = 1, delete_time = ?, update_time = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now()),
                id);
        refreshDocumentAfterSegmentChange(existing.getDocumentId());
    }

    /**
     * 批量删除。
     * @param ids 参数 ids
     * @return 返回结果
     */
    public int batchDelete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<String> clean = ids.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
        if (clean.isEmpty()) {
            return 0;
        }
        List<String> docIds = new ArrayList<>();
        for (String id : clean) {
            try {
                KnowledgeSegmentVo vo = getById(id);
                if (vo.getDocumentId() != null && !docIds.contains(vo.getDocumentId())) {
                    docIds.add(vo.getDocumentId());
                }
            } catch (Exception ignored) {
            }
        }
        try {
            aiEngineClient.deleteSegmentVectors(clean);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "批量清除片段向量失败，未删除，请稍后重试");
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        int n = 0;
        for (String id : clean) {
            n += jdbcTemplate.update(
                    "UPDATE t_knowledge_segment SET deleted = 1, delete_time = ?, update_time = ? WHERE id = ? AND deleted = 0",
                    now,
                    now,
                    id);
        }
        for (String docId : docIds) {
            refreshDocumentAfterSegmentChange(docId);
        }
        return n;
    }

    /**
     * softDeleteByDocument：业务处理。
     * @param documentId 参数 documentId
     */
    public void softDeleteByDocument(String documentId) {
        // 条件判断
        if (documentId == null || documentId.isBlank()) {
            return;
        }
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                "UPDATE t_knowledge_segment SET deleted = 1, delete_time = ?, update_time = ? WHERE document_id = ? AND deleted = 0",
                Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now()),
                documentId);
    }

    /**
     * replaceDocumentSegments：业务处理。
     * @param documentId 参数 documentId
     * @param knowledgeBaseId 参数 knowledgeBaseId
     * @param segments 参数 segments
     */
    public void replaceDocumentSegments(String documentId, String knowledgeBaseId, List<SplitResult.SplitSegment> segments)
            throws Exception {
        softDeleteByDocument(documentId);
        // 条件判断
        if (segments == null || segments.isEmpty()) {
            return;
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        // 遍历处理
        for (SplitResult.SplitSegment seg : segments) {
            String sid = seg.getId() == null || seg.getId().isBlank()
                    ? java.util.UUID.randomUUID().toString().replace("-", "")
                    : seg.getId();
            String type = seg.getSegmentType() == null ? "CHUNK" : seg.getSegmentType().toUpperCase();
            String vectorStatus = "PARENT".equals(type)
                    ? "SKIPPED"
                    : (seg.getVectorStatus() == null ? "PENDING" : seg.getVectorStatus());
            Map<String, Object> meta = seg.getMeta() == null ? new HashMap<>() : new HashMap<>(seg.getMeta());
            // 条件判断
            if (seg.getPage() != null) {
                meta.put("page", seg.getPage());
            }
            // 调用 objectMapper.writeValueAsString
            String metaJson = objectMapper.writeValueAsString(meta);
            Integer tokens = seg.getContentTokens() != null
                    ? seg.getContentTokens()
                    : (seg.getContent() == null ? 0 : seg.getContent().length());
            // 调用 jdbcTemplate.update
            jdbcTemplate.update(
                    """
                    INSERT INTO t_knowledge_segment
                      (id, create_time, update_time, deleted, knowledge_base_id, document_id, parent_id,
                       segment_type, segment_index, content, content_tokens, meta_json, vector_status, manually_edited)
                    VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """,
                    sid,
                    now,
                    now,
                    knowledgeBaseId,
                    documentId,
                    seg.getParentId(),
                    type,
                    seg.getSegmentIndex(),
                    seg.getContent() == null ? "" : seg.getContent(),
                    tokens,
                    metaJson,
                    vectorStatus);
        }
    }

    /**
     * listVectorizableRows：业务处理。
     * @param documentId 参数 documentId
     * @return 返回结果
     */
    public List<Map<String, Object>> listVectorizableRows(String documentId) {
        // 调用 jdbcTemplate.queryForList
        return jdbcTemplate.queryForList(
                """
                SELECT id, content, parent_id, segment_type, segment_index, meta_json, vector_status
                FROM t_knowledge_segment
                WHERE deleted = 0 AND document_id = ?
                  AND segment_type IN ('CHUNK', 'CHILD')
                  AND vector_status IN ('PENDING', 'FAILED')
                ORDER BY segment_index, create_time
                """,
                documentId);
    }

    /** 父子策略的父块：仅用于写入 BM25 供回填，不向量化。 */
    public List<Map<String, Object>> listParentRows(String documentId) {
        // 调用 jdbcTemplate.queryForList
        return jdbcTemplate.queryForList(
                """
                SELECT id, content, parent_id, segment_type, segment_index, meta_json, vector_status
                FROM t_knowledge_segment
                WHERE deleted = 0 AND document_id = ?
                  AND segment_type = 'PARENT'
                ORDER BY segment_index, create_time
                """,
                documentId);
    }

    /**
     * listAllVectorizableRows：业务处理。
     * @param documentId 参数 documentId
     * @return 返回结果
     */
    public List<Map<String, Object>> listAllVectorizableRows(String documentId) {
        // 调用 jdbcTemplate.queryForList
        return jdbcTemplate.queryForList(
                """
                SELECT id, content, parent_id, segment_type, segment_index, meta_json, vector_status
                FROM t_knowledge_segment
                WHERE deleted = 0 AND document_id = ?
                  AND segment_type IN ('CHUNK', 'CHILD')
                ORDER BY segment_index, create_time
                """,
                documentId);
    }

    /**
     * markVectorDone：业务处理。
     * @param segmentIds 参数 segmentIds
     */
    public void markVectorDone(List<String> segmentIds) {
        // 条件判断
        if (segmentIds == null || segmentIds.isEmpty()) {
            return;
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        String placeholders = String.join(",", java.util.Collections.nCopies(segmentIds.size(), "?"));
        List<Object> args = new ArrayList<>(segmentIds.size() + 1);
        args.add(now);
        args.addAll(segmentIds);
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                "UPDATE t_knowledge_segment SET vector_status = 'DONE', vector_id = id, update_time = ? "
                        + "WHERE deleted = 0 AND id IN (" + placeholders + ")",
                args.toArray());
    }

    /**
     * markVectorFailed：业务处理。
     * @param segmentIds 参数 segmentIds
     */
    public void markVectorFailed(List<String> segmentIds) {
        // 条件判断
        if (segmentIds == null || segmentIds.isEmpty()) {
            return;
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        // 遍历处理
        for (String id : segmentIds) {
            // 调用 jdbcTemplate.update
            jdbcTemplate.update(
                    """
                    UPDATE t_knowledge_segment
                    SET vector_status = 'FAILED', update_time = ?
                    WHERE id = ? AND deleted = 0 AND vector_status <> 'SKIPPED'
                    """,
                    now,
                    id);
        }
    }

    /**
     * resetVectorStatus：业务处理。
     * @param documentId 参数 documentId
     */
    public void resetVectorStatus(String documentId) {
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                """
                UPDATE t_knowledge_segment
                SET vector_status = CASE WHEN segment_type = 'PARENT' THEN 'SKIPPED' ELSE 'PENDING' END,
                    vector_id = NULL, update_time = ?
                WHERE document_id = ? AND deleted = 0
                """,
                Timestamp.valueOf(LocalDateTime.now()),
                documentId);
    }

    /**
     * countByDocument：业务处理。
     * @param documentId 参数 documentId
     * @return 返回结果
     */
    public int countByDocument(String documentId) {
        // 调用 jdbcTemplate.queryForObject
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_knowledge_segment WHERE document_id = ? AND deleted = 0",
                Integer.class,
                documentId);
        return n == null ? 0 : n;
    }

    /**
     * refreshDocumentAfterSegmentChange：业务处理。
     * @param documentId 参数 documentId
     */
    private void refreshDocumentAfterSegmentChange(String documentId) {
        // 条件判断
        if (documentId == null) {
            return;
        }
        int left = countByDocument(documentId);
        String status = left > 0 ? "CHUNKED" : "UPLOADED";
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                "UPDATE t_knowledge_document SET parse_status = ?, update_time = ? WHERE id = ? AND deleted = 0",
                status,
                Timestamp.valueOf(LocalDateTime.now()),
                documentId);
    }

    /**
     * docStatus：业务处理。
     * @param documentId 参数 documentId
     * @return 返回结果
     */
    private String docStatus(String documentId) {
        // 调用 jdbcTemplate.query
        return jdbcTemplate.query(
                "SELECT parse_status FROM t_knowledge_document WHERE id = ? AND deleted = 0",
                rs -> rs.next() ? rs.getString(1) : null,
                documentId);
    }

    /**
     * segmentMapper：业务处理。
     * @return 返回结果
     */
    private RowMapper<KnowledgeSegmentVo> segmentMapper() {
        return (rs, rowNum) -> mapRow(rs);
    }

    /**
     * mapRow：业务处理。
     * @param rs 参数 rs
     * @return 返回结果
     */
    private KnowledgeSegmentVo mapRow(ResultSet rs) throws SQLException {
        KnowledgeSegmentVo vo = new KnowledgeSegmentVo();
        vo.setId(rs.getString("id"));
        vo.setKnowledgeBaseId(rs.getString("knowledge_base_id"));
        // 尝试执行
        try {
            vo.setKnowledgeBaseName(rs.getString("knowledge_base_name"));
        } catch (SQLException ignored) {
        }
        vo.setDocumentId(rs.getString("document_id"));
        // 尝试执行
        try {
            vo.setDocumentTitle(rs.getString("document_title"));
        } catch (SQLException ignored) {
        }
        vo.setParentId(rs.getString("parent_id"));
        vo.setSegmentType(rs.getString("segment_type"));
        int idx = rs.getInt("segment_index");
        vo.setSegmentIndex(rs.wasNull() ? null : idx);
        vo.setContent(rs.getString("content"));
        int tokens = rs.getInt("content_tokens");
        vo.setContentTokens(rs.wasNull() ? null : tokens);
        vo.setVectorStatus(rs.getString("vector_status"));
        vo.setVectorId(rs.getString("vector_id"));
        int edited = rs.getInt("manually_edited");
        vo.setManuallyEdited(rs.wasNull() ? 0 : edited);
        Timestamp ct = rs.getTimestamp("create_time");
        vo.setCreateTime(DateTimes.format(ct));
        String metaJson = rs.getString("meta_json");
        // 条件判断
        if (metaJson != null && !metaJson.isBlank()) {
            // 尝试执行
            try {
                // 调用 objectMapper.readTree
                var node = objectMapper.readTree(metaJson);
                // 条件判断
                if (node.has("page") && !node.get("page").isNull()) {
                    // 条件判断
                    if (node.get("page").isNumber()) {
                        vo.setPageNo(node.get("page").asInt());
                    }
                } else if (node.has("page_number") && !node.get("page_number").isNull()) {
                    vo.setPageNo(node.get("page_number").asInt());
                }
            } catch (Exception ignored) {
            }
        }
        // 条件判断
        if (vo.getContentTokens() == null && vo.getContent() != null) {
            vo.setContentTokens(vo.getContent().length());
        }
        return vo;
    }
}
