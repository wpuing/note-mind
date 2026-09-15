package com.notemind.infrastructure.db.repositories.knowledge;

import com.notemind.domain.knowledge.entity.KnowledgeBase;
import com.notemind.domain.knowledge.repositories.KnowledgeBaseRepository;
import com.notemind.domain.knowledge.service.KnowledgeBaseDsvc;
import com.notemind.domain.shared.PageData;
import com.notemind.common.util.DateTimes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 知识库仓储 JDBC 实现：{@code t_knowledge_base} 及关联文档/片段统计与级联软删。
 */
@Repository
public class JdbcKnowledgeBaseRepository implements KnowledgeBaseRepository {

    /** 带文档数/片段数/向量状态子查询的 SELECT 骨架 */
    private static final String SELECT_WITH_STATS =
            """
            SELECT kb.id, kb.name, kb.category, kb.description, kb.status,
                   kb.embedding_model_id, kb.chunk_strategy_id, kb.retrieval_strategy_id, kb.create_time,
                   (SELECT COUNT(1) FROM t_knowledge_document d
                     WHERE d.knowledge_base_id = kb.id AND d.deleted = 0) AS document_count,
                   (SELECT COUNT(1) FROM t_knowledge_segment s
                     WHERE s.knowledge_base_id = kb.id AND s.deleted = 0) AS segment_count,
                   (SELECT COUNT(1) FROM t_knowledge_segment s
                     WHERE s.knowledge_base_id = kb.id AND s.deleted = 0
                       AND s.vector_status = 'DONE') AS vector_done,
                   (SELECT COUNT(1) FROM t_knowledge_segment s
                     WHERE s.knowledge_base_id = kb.id AND s.deleted = 0
                       AND s.vector_status = 'PENDING') AS vector_pending,
                   (SELECT COUNT(1) FROM t_knowledge_segment s
                     WHERE s.knowledge_base_id = kb.id AND s.deleted = 0
                       AND s.vector_status = 'FAILED') AS vector_failed
            FROM t_knowledge_base kb
            """;

    /** Spring JDBC 模板 */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 注入 JdbcTemplate。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public JdbcKnowledgeBaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 列出启用中的知识库（status=1），按名称排序。
     *
     * @return 知识库列表（含统计）
     */
    @Override
    public List<KnowledgeBase> listEnabled() {
        // 通过 JdbcTemplate 查询启用库
        return jdbcTemplate.query(
                SELECT_WITH_STATS
                        + " WHERE kb.deleted = 0 AND kb.status = 1 ORDER BY kb.name",
                entityMapper());
    }

    /**
     * 按名称/状态/分类筛选分页。
     *
     * @param name     名称关键字（可空）
     * @param status   状态（可空）
     * @param category 分类（可空）
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页数据
     */
    @Override
    public PageData<KnowledgeBase> page(String name, Integer status, String category, int page, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE kb.deleted = 0");
        ArrayList<Object> args = new ArrayList<>();
        // 名称模糊
        if (name != null && !name.isBlank()) {
            where.append(" AND kb.name LIKE ?");
            args.add("%" + name.trim() + "%");
        }
        // 状态精确
        if (status != null) {
            where.append(" AND kb.status = ?");
            args.add(status);
        }
        // 分类精确
        if (category != null && !category.isBlank()) {
            where.append(" AND kb.category = ?");
            args.add(category.trim());
        }

        // 通过 JdbcTemplate 统计总数
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_knowledge_base kb" + where,
                Long.class,
                args.toArray());
        long totalVal = total == null ? 0L : total;

        ArrayList<Object> listArgs = new ArrayList<>(args);
        listArgs.add(pageSize);
        listArgs.add((page - 1) * pageSize);
        // 通过 JdbcTemplate 查分页列表
        List<KnowledgeBase> records = jdbcTemplate.query(
                SELECT_WITH_STATS + where + " ORDER BY kb.create_time DESC LIMIT ? OFFSET ?",
                entityMapper(),
                listArgs.toArray());
        return new PageData<>(totalVal, page, pageSize, records);
    }

    /**
     * 按主键查未删除知识库（含统计）。
     *
     * @param id 知识库 ID
     * @return Optional 实体
     */
    @Override
    public Optional<KnowledgeBase> findById(String id) {
        // 通过 JdbcTemplate 按 id 查询
        List<KnowledgeBase> rows = jdbcTemplate.query(
                SELECT_WITH_STATS + " WHERE kb.deleted = 0 AND kb.id = ?",
                entityMapper(),
                id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * 插入新知识库。
     *
     * @param kb 领域实体
     */
    @Override
    public void insert(KnowledgeBase kb) {
        LocalDateTime now = LocalDateTime.now();
        // 通过 JdbcTemplate 执行 INSERT
        jdbcTemplate.update(
                """
                INSERT INTO t_knowledge_base (
                  id, create_time, update_time, deleted,
                  name, category, description, embedding_model_id, chunk_strategy_id, retrieval_strategy_id, status
                ) VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?)
                """,
                kb.getId(),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                kb.getName(),
                kb.getCategory(),
                kb.getDescription(),
                kb.getEmbeddingModelId(),
                kb.getChunkStrategyId(),
                kb.getRetrievalStrategyId(),
                kb.getStatus());
    }

    /**
     * 更新知识库业务字段。
     *
     * @param kb 含 id 的实体
     * @return 是否更新到行
     */
    @Override
    public boolean update(KnowledgeBase kb) {
        // 通过 JdbcTemplate 执行 UPDATE
        int updated = jdbcTemplate.update(
                """
                UPDATE t_knowledge_base
                SET name = ?, category = ?, description = ?,
                    embedding_model_id = ?, chunk_strategy_id = ?, retrieval_strategy_id = ?, status = ?,
                    update_time = ?
                WHERE id = ? AND deleted = 0
                """,
                kb.getName(),
                kb.getCategory(),
                kb.getDescription(),
                kb.getEmbeddingModelId(),
                kb.getChunkStrategyId(),
                kb.getRetrievalStrategyId(),
                kb.getStatus(),
                Timestamp.valueOf(LocalDateTime.now()),
                kb.getId());
        return updated > 0;
    }

    /**
     * 软删知识库并级联软删其下文档与片段。
     *
     * @param id 知识库 ID
     * @return 知识库是否存在并已软删
     */
    @Override
    public boolean softDeleteCascade(String id) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        // 通过 JdbcTemplate 软删知识库
        int updated = jdbcTemplate.update(
                """
                UPDATE t_knowledge_base
                SET deleted = 1, delete_time = ?, update_time = ?
                WHERE id = ? AND deleted = 0
                """,
                now,
                now,
                id);
        // 未命中则直接失败
        if (updated == 0) {
            return false;
        }
        // 通过 JdbcTemplate 级联软删文档
        jdbcTemplate.update(
                """
                UPDATE t_knowledge_document
                SET deleted = 1, delete_time = ?, update_time = ?
                WHERE knowledge_base_id = ? AND deleted = 0
                """,
                now,
                now,
                id);
        // 通过 JdbcTemplate 级联软删片段
        jdbcTemplate.update(
                """
                UPDATE t_knowledge_segment
                SET deleted = 1, delete_time = ?, update_time = ?
                WHERE knowledge_base_id = ? AND deleted = 0
                """,
                now,
                now,
                id);
        return true;
    }

    /**
     * 列出知识库下未删除文档 ID 集合（用于向量清理对照）。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return 文档 ID 集合
     */
    @Override
    public Set<String> listLiveDocumentIds(String knowledgeBaseId) {
        // 通过 JdbcTemplate 查询文档 id
        List<String> ids = jdbcTemplate.query(
                """
                SELECT id FROM t_knowledge_document
                WHERE deleted = 0 AND knowledge_base_id = ?
                """,
                (rs, rowNum) -> rs.getString(1),
                knowledgeBaseId);
        return new HashSet<>(ids);
    }

    /**
     * 统计解析失败的文档数。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return 失败文档数
     */
    @Override
    public long countParseFailedDocuments(String knowledgeBaseId) {
        // 通过 JdbcTemplate 计数
        Long n = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1) FROM t_knowledge_document
                WHERE deleted = 0 AND knowledge_base_id = ? AND parse_status = 'FAILED'
                """,
                Long.class,
                knowledgeBaseId);
        return n == null ? 0L : n;
    }

    /**
     * 汇总 parse_status=READY 文档 meta_json.segmentCount（用于待切分/已就绪对照）。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return 片段数合计
     */
    @Override
    public long sumMetaSegmentCountReady(String knowledgeBaseId) {
        // 通过 JdbcTemplate 汇总 JSON 字段
        Long metaSum = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(meta_json, '$.segmentCount')) AS UNSIGNED)), 0)
                FROM t_knowledge_document
                WHERE deleted = 0 AND knowledge_base_id = ? AND parse_status = 'READY'
                """,
                Long.class,
                knowledgeBaseId);
        return metaSum == null ? 0L : metaSum;
    }

    /**
     * 列出知识库下文档元信息行（标题、文件名、解析状态、meta 等）。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return 文档元信息列表
     */
    @Override
    public List<DocumentMetaRow> listDocumentMetas(String knowledgeBaseId) {
        // 通过 JdbcTemplate 查询文档元数据
        return jdbcTemplate.query(
                """
                SELECT d.id AS document_id, d.title, d.file_name, d.parse_status, d.error_message, d.create_time,
                       d.meta_json
                FROM t_knowledge_document d
                WHERE d.deleted = 0 AND d.knowledge_base_id = ?
                ORDER BY d.create_time DESC
                """,
                (rs, rowNum) -> new DocumentMetaRow(
                        rs.getString("document_id"),
                        rs.getString("title"),
                        rs.getString("file_name"),
                        rs.getString("parse_status"),
                        rs.getString("error_message"),
                        rs.getString("meta_json"),
                        DateTimes.format(rs.getTimestamp("create_time"))),
                knowledgeBaseId);
    }

    /**
     * 构造带统计字段的知识库实体映射器，并计算汇总向量状态。
     *
     * @return RowMapper
     */
    private RowMapper<KnowledgeBase> entityMapper() {
        return (rs, rowNum) -> {
            KnowledgeBase kb = new KnowledgeBase();
            fillBase(kb, rs);
            kb.setDocumentCount(rs.getLong("document_count"));
            kb.setSegmentCount(rs.getLong("segment_count"));
            long done = rs.getLong("vector_done");
            long pending = rs.getLong("vector_pending");
            long failed = rs.getLong("vector_failed");
            kb.setVectorDoneCount(done);
            kb.setVectorPendingCount(pending);
            kb.setVectorFailedCount(failed);
            kb.setVectorStatus(KnowledgeBaseDsvc.resolveVectorStatus(kb.getSegmentCount(), done, pending, failed));
            return kb;
        };
    }

    /**
     * 填充知识库基础列；策略相关列与 create_time 兼容缺列。
     *
     * @param kb 目标实体
     * @param rs 结果集当前行
     * @throws SQLException JDBC 读列异常（部分被吞）
     */
    private void fillBase(KnowledgeBase kb, ResultSet rs) throws SQLException {
        kb.setId(rs.getString("id"));
        kb.setName(rs.getString("name"));
        kb.setCategory(rs.getString("category"));
        kb.setDescription(rs.getString("description"));
        kb.setStatus(rs.getInt("status"));
        // 旧库可能缺策略绑定列
        try {
            kb.setEmbeddingModelId(rs.getString("embedding_model_id"));
            kb.setChunkStrategyId(rs.getString("chunk_strategy_id"));
            kb.setRetrievalStrategyId(rs.getString("retrieval_strategy_id"));
        // 缺列忽略
        } catch (SQLException ignored) {
        }
        // create_time 兼容
        try {
            kb.setCreateTime(DateTimes.format(rs.getTimestamp("create_time")));
        // 缺列忽略
        } catch (SQLException ignored) {
        }
    }
}
