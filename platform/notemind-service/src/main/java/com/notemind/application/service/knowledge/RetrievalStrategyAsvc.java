package com.notemind.application.service.knowledge;

import com.notemind.common.result.PageResult;
import com.notemind.common.util.DateTimes;
import com.notemind.interfaces.knowledge.vo.RetrievalStrategySaveRequest;
import com.notemind.interfaces.knowledge.vo.RetrievalStrategyVo;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 检索策略应用服务：七开关 CRUD、知识库策略解析与批量删除。
 */

@Service
public class RetrievalStrategyAsvc {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 注入 JDBC 模板。
     *
     * @param jdbcTemplate Spring JdbcTemplate
     */
    public RetrievalStrategyAsvc(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询启用中的检索策略（含引用数）。
     *
     * @return 启用策略列表
     */
    public List<RetrievalStrategyVo> listEnabled() {
        // 查询启用检索策略及知识库引用数
        return jdbcTemplate.query(
                """
                SELECT r.*,
                       (SELECT COUNT(1) FROM t_knowledge_base kb
                         WHERE kb.deleted = 0 AND kb.retrieval_strategy_id = r.id) AS ref_count
                FROM t_retrieval_strategy r
                WHERE r.deleted = 0 AND r.enabled = 1
                ORDER BY r.is_default DESC, r.name ASC
                """,
                mapper());
    }

    /**
     * 分页查询检索策略。
     *
     * @param name     名称模糊，可空
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public PageResult<RetrievalStrategyVo> page(String name, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        StringBuilder where = new StringBuilder(" WHERE r.deleted = 0");
        ArrayList<Object> args = new ArrayList<>();
        // 按名称模糊过滤
        if (name != null && !name.isBlank()) {
            where.append(" AND r.name LIKE ?");
            args.add("%" + name.trim() + "%");
        }
        // 统计总数
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_retrieval_strategy r" + where, Long.class, args.toArray());
        ArrayList<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeSize);
        listArgs.add((safePage - 1) * safeSize);
        // 分页查询列表
        List<RetrievalStrategyVo> records = jdbcTemplate.query(
                """
                SELECT r.*,
                       (SELECT COUNT(1) FROM t_knowledge_base kb
                         WHERE kb.deleted = 0 AND kb.retrieval_strategy_id = r.id) AS ref_count
                FROM t_retrieval_strategy r
                """
                        + where
                        + " ORDER BY r.is_default DESC, r.create_time DESC LIMIT ? OFFSET ?",
                mapper(),
                listArgs.toArray());
        return PageResult.of(total == null ? 0L : total, safePage, safeSize, records);
    }

    /**
     * 按 ID 查询检索策略。
     *
     * @param id 策略 ID
     * @return 策略 VO
     */
    public RetrievalStrategyVo getById(String id) {
        // 按主键查询
        List<RetrievalStrategyVo> rows = jdbcTemplate.query(
                """
                SELECT r.*,
                       (SELECT COUNT(1) FROM t_knowledge_base kb
                         WHERE kb.deleted = 0 AND kb.retrieval_strategy_id = r.id) AS ref_count
                FROM t_retrieval_strategy r
                WHERE r.deleted = 0 AND r.id = ?
                """,
                mapper(),
                id);
        // 不存在则 404
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "retrieval strategy not found");
        }
        return rows.get(0);
    }

    /**
     * 获取默认检索策略；无默认则回退到任意启用策略。
     *
     * @return 策略 VO
     */
    public RetrievalStrategyVo getDefaultOrFallback() {
        // 优先查默认启用策略
        List<RetrievalStrategyVo> rows = jdbcTemplate.query(
                """
                SELECT r.*, 0 AS ref_count
                FROM t_retrieval_strategy r
                WHERE r.deleted = 0 AND r.enabled = 1 AND r.is_default = 1
                ORDER BY r.update_time DESC LIMIT 1
                """,
                mapper());
        // 有默认则返回
        if (!rows.isEmpty()) return rows.get(0);
        // 回退任意启用策略
        List<RetrievalStrategyVo> any = jdbcTemplate.query(
                """
                SELECT r.*, 0 AS ref_count
                FROM t_retrieval_strategy r
                WHERE r.deleted = 0 AND r.enabled = 1
                ORDER BY r.name LIMIT 1
                """,
                mapper());
        // 仍无则 404
        if (any.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no enabled retrieval strategy");
        }
        return any.get(0);
    }

    /**
     * 按知识库绑定策略解析；无效则回退默认。
     *
     * @param knowledgeBaseId 知识库 ID，可空
     * @return 策略 VO
     */
    public RetrievalStrategyVo resolveForKnowledgeBase(String knowledgeBaseId) {
        // 有知识库 ID 时尝试读取绑定策略
        if (knowledgeBaseId != null && !knowledgeBaseId.isBlank()) {
            // 查询知识库绑定的检索策略 ID
            List<String> ids = jdbcTemplate.query(
                    """
                    SELECT retrieval_strategy_id FROM t_knowledge_base
                    WHERE id = ? AND deleted = 0
                    """,
                    (rs, i) -> rs.getString(1),
                    knowledgeBaseId.trim());
            // 绑定非空则尝试加载
            if (!ids.isEmpty() && ids.get(0) != null && !ids.get(0).isBlank()) {
                // 尝试执行
                try {
                    return getById(ids.get(0));
                } catch (ResponseStatusException ignored) {
                    // fall through to default
                }
            }
        }
        return getDefaultOrFallback();
    }

    /**
     * 新建检索策略。
     *
     * @param req 保存请求
     * @return 新建后的 VO
     */

    @Transactional
    public RetrievalStrategyVo create(RetrievalStrategySaveRequest req) {
        validate(req);
        String id = "rs_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        LocalDateTime now = LocalDateTime.now();
        boolean makeDefault = req.getIsDefault() != null && req.getIsDefault() == 1;
        // 设为默认前清空其它默认
        if (makeDefault) {
            // 清除全部默认标记
            jdbcTemplate.update("UPDATE t_retrieval_strategy SET is_default = 0 WHERE deleted = 0");
        }
        int enableVector = flag(req.getEnableVector(), 1);
        int enableBm25 = flag(req.getEnableBm25(), 0);
        int enableRerank = flag(req.getEnableRerank(), 0);
        int enableRewrite = flag(req.getEnableRewrite(), 0);
        int enableParentFill = flag(req.getEnableParentFill(), 0);
        int enableRrf = req.getEnableRrf() != null
                ? flag(req.getEnableRrf(), 0)
                : (enableVector == 1 && enableBm25 == 1 ? 1 : 0);
        // 插入检索策略
        jdbcTemplate.update(
                """
                INSERT INTO t_retrieval_strategy (
                  id, create_time, update_time, deleted, name,
                  enable_vector, enable_bm25, enable_rrf, enable_rerank, enable_rewrite, enable_parent_fill,
                  top_k, rerank_top_n, vector_top_k, bm25_top_k, rrf_k,
                  cosine_threshold, rerank_threshold, rewrite_mode, rewrite_count,
                  enabled, is_default, remark
                ) VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                req.getName().trim(),
                enableVector,
                enableBm25,
                enableRrf,
                enableRerank,
                enableRewrite,
                enableParentFill,
                clamp(req.getTopK(), 1, 50, 5),
                clamp(req.getRerankTopN(), 1, 50, 4),
                clamp(req.getVectorTopK(), 0, 100, enableVector == 1 ? 5 : 0),
                clamp(req.getBm25TopK(), 0, 100, enableBm25 == 1 ? 8 : 0),
                clamp(req.getRrfK(), 1, 200, 60),
                req.getCosineThreshold(),
                req.getRerankThreshold(),
                normalizeRewriteMode(req.getRewriteMode()),
                clamp(req.getRewriteCount(), 1, 5, 3),
                flag(req.getEnabled(), 1),
                makeDefault ? 1 : 0,
                blankToNull(req.getRemark()));
        return getById(id);
    }

    /**
     * 更新检索策略。
     *
     * @param id  策略 ID
     * @param req 保存请求
     * @return 更新后的 VO
     */

    @Transactional
    public RetrievalStrategyVo update(String id, RetrievalStrategySaveRequest req) {
        RetrievalStrategyVo existing = getById(id);
        validate(req);
        int enabled = req.getEnabled() == null
                ? (existing.getEnabled() == null ? 1 : existing.getEnabled())
                : flag(req.getEnabled(), 1);
        int isDefault;
        // 未传则沿用
        if (req.getIsDefault() == null) {
            isDefault = existing.getIsDefault() == null ? 0 : existing.getIsDefault();
        } else {
            isDefault = flag(req.getIsDefault(), 0);
        }
        // 设为默认时清除其它
        if (isDefault == 1) {
            // 清除除自身外的默认标记
            jdbcTemplate.update(
                    "UPDATE t_retrieval_strategy SET is_default = 0 WHERE deleted = 0 AND id <> ?", id);
        }
        int enableVector = flag(req.getEnableVector(), existing.getEnableVector());
        int enableBm25 = flag(req.getEnableBm25(), existing.getEnableBm25());
        int enableRerank = flag(req.getEnableRerank(), existing.getEnableRerank());
        int enableRewrite = flag(req.getEnableRewrite(), existing.getEnableRewrite());
        int enableParentFill = flag(req.getEnableParentFill(), existing.getEnableParentFill());
        int enableRrf = req.getEnableRrf() != null
                ? flag(req.getEnableRrf(), 0)
                : (enableVector == 1 && enableBm25 == 1 ? 1 : 0);
        // 更新策略字段
        jdbcTemplate.update(
                """
                UPDATE t_retrieval_strategy
                SET name = ?,
                    enable_vector = ?, enable_bm25 = ?, enable_rrf = ?, enable_rerank = ?,
                    enable_rewrite = ?, enable_parent_fill = ?,
                    top_k = ?, rerank_top_n = ?, vector_top_k = ?, bm25_top_k = ?, rrf_k = ?,
                    cosine_threshold = ?, rerank_threshold = ?,
                    rewrite_mode = ?, rewrite_count = ?,
                    enabled = ?, is_default = ?, remark = ?, update_time = ?
                WHERE id = ? AND deleted = 0
                """,
                req.getName().trim(),
                enableVector,
                enableBm25,
                enableRrf,
                enableRerank,
                enableRewrite,
                enableParentFill,
                clamp(req.getTopK(), 1, 50, existing.getTopK() == null ? 5 : existing.getTopK()),
                clamp(req.getRerankTopN(), 1, 50, existing.getRerankTopN() == null ? 4 : existing.getRerankTopN()),
                clamp(req.getVectorTopK(), 0, 100, existing.getVectorTopK() == null ? 5 : existing.getVectorTopK()),
                clamp(req.getBm25TopK(), 0, 100, existing.getBm25TopK() == null ? 0 : existing.getBm25TopK()),
                clamp(req.getRrfK(), 1, 200, existing.getRrfK() == null ? 60 : existing.getRrfK()),
                req.getCosineThreshold(),
                req.getRerankThreshold(),
                normalizeRewriteMode(req.getRewriteMode()),
                clamp(req.getRewriteCount(), 1, 5, existing.getRewriteCount() == null ? 3 : existing.getRewriteCount()),
                enabled,
                isDefault,
                blankToNull(req.getRemark()),
                Timestamp.valueOf(LocalDateTime.now()),
                id);
        return getById(id);
    }

    /**
     * 逻辑删除检索策略；有引用或为默认时拒绝。
     *
     * @param id 策略 ID
     */

    @Transactional
    public void delete(String id) {
        RetrievalStrategyVo existing = getById(id);
        // 统计引用数
        Long refs = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_knowledge_base WHERE deleted = 0 AND retrieval_strategy_id = ?",
                Long.class,
                id);
        // 仍被引用
        if (refs != null && refs > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "策略仍被 " + refs + " 个知识库引用，无法删除");
        }
        // 默认不可删
        if (existing.getIsDefault() != null && existing.getIsDefault() == 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "默认策略不可删除，请先指定其它默认策略");
        }
        // 逻辑删除
        jdbcTemplate.update(
                """
                UPDATE t_retrieval_strategy
                SET deleted = 1, delete_time = ?, update_time = ?
                WHERE id = ? AND deleted = 0
                """,
                Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now()),
                id);
    }

    /**
     * 批量删除检索策略。
     *
     * @param ids 策略 ID 列表
     * @return 成功删除条数
     */

    @Transactional
    public int batchDelete(List<String> ids) {
        // ids 必填
        if (ids == null || ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ids required");
        }
        int n = 0;
        List<String> errors = new ArrayList<>();
        // 遍历处理
        for (String id : ids) {
            // 跳过空 ID
            if (id == null || id.isBlank()) continue;
            String trimmed = id.trim();
            // 尝试执行
            try {
                delete(trimmed);
                n++;
            } catch (ResponseStatusException ex) {
                // 不存在跳过
                if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                    continue;
                }
                String name;
                // 尝试执行
                try {
                    name = getById(trimmed).getName();
                } catch (Exception ignored) {
                    // 取名失败用 ID
                    name = trimmed;
                }
                errors.add(name + ": " + ex.getReason());
            }
        }
        // 全部失败则冲突
        if (n == 0 && !errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, String.join("; ", errors));
        }
        return n;
    }

    /**
     * 校验检索策略请求：名称与至少一路召回。
     *
     * @param req 保存请求
     */
    private void validate(RetrievalStrategySaveRequest req) {
        // 名称必填
        if (req == null || req.getName() == null || req.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name required");
        }
        // 名称长度
        if (req.getName().trim().length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name too long");
        }
        int v = flag(req.getEnableVector(), 0);
        int b = flag(req.getEnableBm25(), 0);
        // 至少启用一路召回
        if (v == 0 && b == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "至少启用向量或 BM25 一路召回");
        }
    }

    /**
     * 整型开关规范化为 0/1。
     *
     * @param v        请求值
     * @param fallback 空时回退
     * @return 0 或 1
     */
    private static int flag(Integer v, Integer fallback) {
        // 空则用回退
        if (v == null) {
            return fallback == null ? 0 : (fallback == 0 ? 0 : 1);
        }
        return v == 0 ? 0 : 1;
    }

    /**
     * 将数值钳制到 [min, max]，空用 fallback。
     *
     * @param v        原值
     * @param min      下限
     * @param max      上限
     * @param fallback 空时默认
     * @return 钳制后的值
     */
    private static int clamp(Integer v, int min, int max, int fallback) {
        int x = v == null ? fallback : v;
        // 低于下限
        if (x < min) return min;
        // 高于上限
        if (x > max) return max;
        return x;
    }

    /**
     * 规范化改写模式枚举。
     *
     * @param mode 原始模式
     * @return 合法模式字符串
     */
    private static String normalizeRewriteMode(String mode) {
        // 空则默认 multi_query
        if (mode == null || mode.isBlank()) return "multi_query";
        String m = mode.trim().toLowerCase(Locale.ROOT);
        // 按合法枚举匹配
        return switch (m) {
            // 合法改写模式
            case "multi_query", "coref", "hyde", "all" -> m;
            // 未知则回退
            default -> "multi_query";
        };
    }

    /**
     * 空白字符串转 null。
     *
     * @param s 原字符串
     * @return trim 后非空或 null
     */
    private static String blankToNull(String s) {
        // 空则 null
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }

    /**
     * 构建检索策略行映射器。
     *
     * @return RowMapper
     */
    private RowMapper<RetrievalStrategyVo> mapper() {
        return (rs, rowNum) -> mapRow(rs);
    }

    /**
     * 将结果集行映射为检索策略 VO。
     *
     * @param rs 结果集
     * @return VO
     * @throws SQLException JDBC 异常
     */
    private RetrievalStrategyVo mapRow(ResultSet rs) throws SQLException {
        RetrievalStrategyVo vo = new RetrievalStrategyVo();
        vo.setId(rs.getString("id"));
        vo.setName(rs.getString("name"));
        vo.setEnableVector(rs.getInt("enable_vector"));
        vo.setEnableBm25(rs.getInt("enable_bm25"));
        vo.setEnableRrf(rs.getInt("enable_rrf"));
        vo.setEnableRerank(rs.getInt("enable_rerank"));
        vo.setEnableRewrite(rs.getInt("enable_rewrite"));
        vo.setEnableParentFill(rs.getInt("enable_parent_fill"));
        vo.setTopK(rs.getInt("top_k"));
        // 尝试执行
        try {
            vo.setRerankTopN(rs.getInt("rerank_top_n"));
        } catch (SQLException ex) {
            // 旧表无列时用 topK
            vo.setRerankTopN(vo.getTopK());
        }
        vo.setVectorTopK(rs.getInt("vector_top_k"));
        vo.setBm25TopK(rs.getInt("bm25_top_k"));
        vo.setRrfK(rs.getInt("rrf_k"));
        BigDecimal cos = rs.getBigDecimal("cosine_threshold");
        vo.setCosineThreshold(rs.wasNull() ? null : cos);
        BigDecimal rr = rs.getBigDecimal("rerank_threshold");
        vo.setRerankThreshold(rs.wasNull() ? null : rr);
        vo.setRewriteMode(rs.getString("rewrite_mode"));
        // 尝试执行
        try {
            vo.setRewriteCount(rs.getInt("rewrite_count"));
        } catch (SQLException ex) {
            // 旧表默认 3
            vo.setRewriteCount(3);
        }
        vo.setEnabled(rs.getInt("enabled"));
        // 尝试执行
        try {
            vo.setIsDefault(rs.getInt("is_default"));
        } catch (SQLException ex) {
            // 旧表默认 0
            vo.setIsDefault(0);
        }
        vo.setRemark(rs.getString("remark"));
        Timestamp ct = rs.getTimestamp("create_time");
        vo.setCreateTime(ct == null ? null : DateTimes.format(ct.toLocalDateTime()));
        // 尝试执行
        try {
            vo.setRefCount(rs.getLong("ref_count"));
        } catch (SQLException ex) {
            // 无引用数列
            vo.setRefCount(0L);
        }
        return vo;
    }
}

