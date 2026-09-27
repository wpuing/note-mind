package com.notemind.application.service.app;

import com.notemind.common.result.PageResult;
import com.notemind.common.util.DateTimes;
import com.notemind.interfaces.app.vo.QaAppSaveRequest;
import com.notemind.interfaces.app.vo.QaAppVo;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 问答应用管理应用服务：挂载知识库/策略、CRUD 与批量删除。
 */

@Service
public class QaAppAsvc {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 注入 JDBC 模板。
     *
     * @param jdbcTemplate Spring JdbcTemplate
     */
    public QaAppAsvc(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 分页查询问答应用。
     *
     * @param name     名称模糊，可空
     * @param enabled  启用状态，可空
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public PageResult<QaAppVo> page(String name, Integer enabled, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        StringBuilder where = new StringBuilder(" WHERE a.deleted = 0");
        ArrayList<Object> args = new ArrayList<>();
        // 按名称模糊过滤
        if (name != null && !name.isBlank()) {
            where.append(" AND a.name LIKE ?");
            args.add("%" + name.trim() + "%");
        }
        // 按启用状态过滤
        if (enabled != null) {
            where.append(" AND a.enabled = ?");
            args.add(enabled);
        }
        // 统计总数
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_qa_app a" + where, Long.class, args.toArray());
        ArrayList<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeSize);
        listArgs.add((safePage - 1) * safeSize);
        // 分页查询并关联知识库/策略名
        List<QaAppVo> records = jdbcTemplate.query(
                """
                SELECT a.*, kb.name AS kb_name, rs.name AS rs_name
                FROM t_qa_app a
                LEFT JOIN t_knowledge_base kb ON kb.id = a.knowledge_base_id AND kb.deleted = 0
                LEFT JOIN t_retrieval_strategy rs ON rs.id = a.retrieval_strategy_id AND rs.deleted = 0
                """
                        + where
                        + " ORDER BY a.create_time DESC LIMIT ? OFFSET ?",
                mapper(),
                listArgs.toArray());
        return PageResult.of(total == null ? 0L : total, safePage, safeSize, records);
    }

    /**
     * 查询启用中的问答应用（下拉用）。
     *
     * @return 启用应用列表
     */
    public List<QaAppVo> listEnabled() {
        // 查询启用应用
        return jdbcTemplate.query(
                """
                SELECT a.*, kb.name AS kb_name, rs.name AS rs_name
                FROM t_qa_app a
                LEFT JOIN t_knowledge_base kb ON kb.id = a.knowledge_base_id AND kb.deleted = 0
                LEFT JOIN t_retrieval_strategy rs ON rs.id = a.retrieval_strategy_id AND rs.deleted = 0
                WHERE a.deleted = 0 AND a.enabled = 1
                ORDER BY a.create_time DESC
                """,
                mapper());
    }

    /**
     * 按 ID 查询应用详情。
     *
     * @param id 应用 ID
     * @return 应用 VO
     */
    public QaAppVo getById(String id) {
        // 按主键查询
        List<QaAppVo> rows = jdbcTemplate.query(
                """
                SELECT a.*, kb.name AS kb_name, rs.name AS rs_name
                FROM t_qa_app a
                LEFT JOIN t_knowledge_base kb ON kb.id = a.knowledge_base_id AND kb.deleted = 0
                LEFT JOIN t_retrieval_strategy rs ON rs.id = a.retrieval_strategy_id AND rs.deleted = 0
                WHERE a.deleted = 0 AND a.id = ?
                """,
                mapper(),
                id);
        // 不存在则 404
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "qa app not found");
        }
        return rows.get(0);
    }

    /**
     * 新建问答应用。
     *
     * @param req 保存请求
     * @return 新建后的 VO
     */

    @Transactional
    public QaAppVo create(QaAppSaveRequest req) {
        validate(req);
        String id = "app_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        LocalDateTime now = LocalDateTime.now();
        String chatModelId = resolveChatModelId(req.getChatModelId());
        String strategyId = resolveStrategyId(req.getRetrievalStrategyId());
        ensureKb(req.getKnowledgeBaseId());
        // 插入应用
        jdbcTemplate.update(
                """
                INSERT INTO t_qa_app (
                  id, create_time, update_time, deleted,
                  name, description, knowledge_base_id, retrieval_strategy_id,
                  chat_model_id, answer_prompt_id, enable_agentic, history_limit, fallback_reply, enabled
                ) VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                req.getName().trim(),
                blankToNull(req.getDescription()),
                req.getKnowledgeBaseId().trim(),
                strategyId,
                chatModelId,
                blankToNull(req.getAnswerPromptId()),
                req.getEnableAgentic() == null ? 1 : (req.getEnableAgentic() == 0 ? 0 : 1),
                normalizeHistory(req.getHistoryLimit()),
                blankToNull(req.getFallbackReply()),
                req.getEnabled() == null ? 1 : (req.getEnabled() == 0 ? 0 : 1));
        return getById(id);
    }

    /**
     * 更新问答应用。
     *
     * @param id  应用 ID
     * @param req 保存请求
     * @return 更新后的 VO
     */

    @Transactional
    public QaAppVo update(String id, QaAppSaveRequest req) {
        getById(id);
        validate(req);
        String chatModelId = resolveChatModelId(req.getChatModelId());
        String strategyId = resolveStrategyId(req.getRetrievalStrategyId());
        ensureKb(req.getKnowledgeBaseId());
        // 更新应用字段
        jdbcTemplate.update(
                """
                UPDATE t_qa_app
                SET name = ?, description = ?, knowledge_base_id = ?, retrieval_strategy_id = ?,
                    chat_model_id = ?, answer_prompt_id = ?, enable_agentic = ?, history_limit = ?,
                    fallback_reply = ?, enabled = ?, update_time = ?
                WHERE id = ? AND deleted = 0
                """,
                req.getName().trim(),
                blankToNull(req.getDescription()),
                req.getKnowledgeBaseId().trim(),
                strategyId,
                chatModelId,
                blankToNull(req.getAnswerPromptId()),
                req.getEnableAgentic() == null ? 1 : (req.getEnableAgentic() == 0 ? 0 : 1),
                normalizeHistory(req.getHistoryLimit()),
                blankToNull(req.getFallbackReply()),
                req.getEnabled() == null ? 1 : (req.getEnabled() == 0 ? 0 : 1),
                Timestamp.valueOf(LocalDateTime.now()),
                id);
        return getById(id);
    }

    /**
     * 逻辑删除问答应用。
     *
     * @param id 应用 ID
     */

    @Transactional
    public void delete(String id) {
        getById(id);
        LocalDateTime now = LocalDateTime.now();
        // 逻辑删除
        jdbcTemplate.update(
                """
                UPDATE t_qa_app
                SET deleted = 1, delete_time = ?, update_time = ?
                WHERE id = ? AND deleted = 0
                """,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                id);
    }

    /**
     * 批量逻辑删除问答应用。
     *
     * @param ids 应用 ID 列表
     * @return 删除行数
     */

    @Transactional
    public int batchDelete(List<String> ids) {
        // 空列表直接 0
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        Timestamp ts = Timestamp.valueOf(now);
        int deleted = 0;
        // 遍历处理
        for (String raw : ids) {
            // 跳过空 ID
            if (raw == null || raw.isBlank()) continue;
            // 逻辑删除
            deleted += jdbcTemplate.update(
                    """
                    UPDATE t_qa_app
                    SET deleted = 1, delete_time = ?, update_time = ?
                    WHERE id = ? AND deleted = 0
                    """,
                    ts,
                    ts,
                    raw.trim());
        }
        return deleted;
    }

    /**
     * 校验保存请求必填项。
     *
     * @param req 保存请求
     */
    private void validate(QaAppSaveRequest req) {
        // 名称必填
        if (req == null || req.getName() == null || req.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "应用名称必填");
        }
        // 知识库必选
        if (req.getKnowledgeBaseId() == null || req.getKnowledgeBaseId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择知识库");
        }
    }

    /**
     * 断言知识库存在。
     *
     * @param kbId 知识库 ID
     */
    private void ensureKb(String kbId) {
        // 统计知识库是否存在
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_knowledge_base WHERE deleted = 0 AND id = ?",
                Long.class,
                kbId.trim());
        // 不存在则拒绝
        if (n == null || n == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "知识库不存在");
        }
    }

    /**
     * 解析检索策略 ID：指定则校验，否则取默认/任意。
     *
     * @param strategyId 请求中的策略 ID，可空
     * @return 可用策略 ID
     */
    private String resolveStrategyId(String strategyId) {
        // 指定了策略则校验存在
        if (strategyId != null && !strategyId.isBlank()) {
            // 统计策略是否存在
            Long n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM t_retrieval_strategy WHERE deleted = 0 AND id = ?",
                    Long.class,
                    strategyId.trim());
            // 存在则返回
            if (n != null && n > 0) {
                return strategyId.trim();
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "检索策略不存在");
        }
        // 取默认策略
        List<String> def = jdbcTemplate.query(
                "SELECT id FROM t_retrieval_strategy WHERE deleted = 0 AND is_default = 1 LIMIT 1",
                (rs, i) -> rs.getString("id"));
        // 有默认则用
        if (!def.isEmpty()) {
            return def.get(0);
        }
        // 回退任意策略
        List<String> any = jdbcTemplate.query(
                "SELECT id FROM t_retrieval_strategy WHERE deleted = 0 ORDER BY create_time ASC LIMIT 1",
                (rs, i) -> rs.getString("id"));
        // 仍无则提示先配置
        if (any.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先配置检索策略");
        }
        return any.get(0);
    }

    /**
     * 解析对话模型 ID：指定则校验，否则取首个启用 CHAT 模型。
     *
     * @param chatModelId 请求中的模型 ID，可空
     * @return 可用对话模型 ID
     */
    private String resolveChatModelId(String chatModelId) {
        // 指定了模型则校验
        if (chatModelId != null && !chatModelId.isBlank()) {
            // 统计模型是否存在
            Long n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM t_ai_model_config WHERE deleted = 0 AND id = ?",
                    Long.class,
                    chatModelId.trim());
            // 存在则返回
            if (n != null && n > 0) {
                return chatModelId.trim();
            }
        }
        // 取首个启用对话模型
        List<String> chat = jdbcTemplate.query(
                """
                SELECT id FROM t_ai_model_config
                WHERE deleted = 0 AND enabled = 1 AND model_type = 'CHAT'
                ORDER BY create_time ASC LIMIT 1
                """,
                (rs, i) -> rs.getString("id"));
        // 有则返回
        if (!chat.isEmpty()) {
            return chat.get(0);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先配置对话模型");
    }

    /**
     * 规范化历史条数到 0～50，默认 6。
     *
     * @param v 请求值
     * @return 规范化后的历史条数
     */
    private static int normalizeHistory(Integer v) {
        // 空用默认 6
        if (v == null) return 6;
        return Math.min(Math.max(v, 0), 50);
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
     * 构建问答应用行映射器。
     *
     * @return RowMapper
     */
    private RowMapper<QaAppVo> mapper() {
        return (rs, rowNum) -> {
            QaAppVo vo = new QaAppVo();
            vo.setId(rs.getString("id"));
            vo.setName(rs.getString("name"));
            vo.setDescription(rs.getString("description"));
            vo.setKnowledgeBaseId(rs.getString("knowledge_base_id"));
            // 尝试执行
            try {
                vo.setKnowledgeBaseName(rs.getString("kb_name"));
            } catch (Exception ignored) {
            }
            vo.setRetrievalStrategyId(rs.getString("retrieval_strategy_id"));
            // 尝试执行
            try {
                vo.setRetrievalStrategyName(rs.getString("rs_name"));
            } catch (Exception ignored) {
            }
            vo.setChatModelId(rs.getString("chat_model_id"));
            vo.setAnswerPromptId(rs.getString("answer_prompt_id"));
            vo.setEnableAgentic(rs.getInt("enable_agentic"));
            // 尝试执行
            try {
                vo.setHistoryLimit(rs.getInt("history_limit"));
                // NULL 用默认 6
                if (rs.wasNull()) vo.setHistoryLimit(6);
            } catch (Exception e) {
                // 旧表无列
                vo.setHistoryLimit(6);
            }
            // 尝试执行
            try {
                vo.setFallbackReply(rs.getString("fallback_reply"));
            } catch (Exception ignored) {
            }
            vo.setEnabled(rs.getInt("enabled"));
            Timestamp ct = rs.getTimestamp("create_time");
            Timestamp ut = rs.getTimestamp("update_time");
            vo.setCreateTime(DateTimes.format(ct));
            vo.setUpdateTime(DateTimes.format(ut));
            return vo;
        };
    }
}

