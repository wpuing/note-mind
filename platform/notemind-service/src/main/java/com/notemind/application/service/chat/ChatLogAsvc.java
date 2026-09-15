package com.notemind.application.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notemind.common.result.PageResult;
import com.notemind.common.util.DateTimes;
import com.notemind.interfaces.chat.vo.ChatFeedbackItemVo;
import com.notemind.interfaces.chat.vo.ChatFeedbackStatVo;
import com.notemind.interfaces.chat.vo.ChatMessageVo;
import com.notemind.interfaces.chat.vo.ChatSessionLogVo;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 对话日志与反馈应用服务：满意度统计、会话/消息列表、反馈沉淀标记。
 */

@Service
public class ChatLogAsvc {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 注入 JDBC 与 JSON 工具。
     *
     * @param jdbcTemplate Spring JdbcTemplate
     * @param objectMapper Jackson ObjectMapper
     */
    public ChatLogAsvc(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 统计赞踩数量与满意度。
     *
     * @return 反馈统计 VO
     */
    public ChatFeedbackStatVo stats() {
        // 统计点赞数
        Long like = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1) FROM t_chat_message
                WHERE deleted = 0 AND feedback = 'LIKE'
                """,
                Long.class);
        // 统计点踩数
        Long dislike = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1) FROM t_chat_message
                WHERE deleted = 0 AND feedback = 'DISLIKE'
                """,
                Long.class);
        long lc = like == null ? 0L : like;
        long dc = dislike == null ? 0L : dislike;
        ChatFeedbackStatVo vo = new ChatFeedbackStatVo();
        vo.setLikeCount(lc);
        vo.setDislikeCount(dc);
        long total = lc + dc;
        vo.setSatisfactionRate(total == 0 ? 0D : Math.round(lc * 10000.0 / total) / 100.0);
        return vo;
    }

    /**
     * 分页查询会话日志（含赞踩计数）。
     *
     * @param title    标题模糊，可空
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public PageResult<ChatSessionLogVo> pageSessions(String title, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        StringBuilder where = new StringBuilder(" WHERE s.deleted = 0");
        ArrayList<Object> args = new ArrayList<>();
        // 按会话标题模糊过滤
        if (title != null && !title.isBlank()) {
            where.append(" AND s.title LIKE ?");
            args.add("%" + title.trim() + "%");
        }
        // 统计会话总数
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_chat_session s" + where, Long.class, args.toArray());
        ArrayList<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeSize);
        listArgs.add((safePage - 1) * safeSize);
        // 分页查询会话及消息/赞踩聚合
        List<ChatSessionLogVo> records = jdbcTemplate.query(
                """
                SELECT s.*, a.name AS app_name,
                  (SELECT COUNT(1) FROM t_chat_message m
                     WHERE m.deleted = 0 AND m.session_id = s.id) AS msg_count,
                  (SELECT COUNT(1) FROM t_chat_message m
                     WHERE m.deleted = 0 AND m.session_id = s.id AND m.feedback = 'LIKE') AS like_count,
                  (SELECT COUNT(1) FROM t_chat_message m
                     WHERE m.deleted = 0 AND m.session_id = s.id AND m.feedback = 'DISLIKE') AS dislike_count
                FROM t_chat_session s
                LEFT JOIN t_qa_app a ON a.id = s.app_id AND a.deleted = 0
                """
                        + where
                        + " ORDER BY s.create_time DESC LIMIT ? OFFSET ?",
                (rs, n) -> {
                    ChatSessionLogVo vo = new ChatSessionLogVo();
                    vo.setId(rs.getString("id"));
                    vo.setAppId(rs.getString("app_id"));
                    vo.setAppName(rs.getString("app_name"));
                    vo.setTitle(rs.getString("title"));
                    vo.setMessageCount(rs.getInt("msg_count"));
                    // 尝试执行
                    try {
                        vo.setClientSource(rs.getString("client_source"));
                    } catch (Exception e) {
                        // 旧表无列时默认 ADMIN
                        vo.setClientSource("ADMIN");
                    }
                    // 来源为空时默认 ADMIN
                    if (vo.getClientSource() == null || vo.getClientSource().isBlank()) {
                        vo.setClientSource("ADMIN");
                    }
                    vo.setLikeCount(rs.getInt("like_count"));
                    vo.setDislikeCount(rs.getInt("dislike_count"));
                    Timestamp ct = rs.getTimestamp("create_time");
                    vo.setCreateTime(DateTimes.format(ct));
                    return vo;
                },
                listArgs.toArray());
        return PageResult.of(total == null ? 0L : total, safePage, safeSize, records);
    }

    /**
     * 查询某会话下全部消息（含 sources_json 反序列化）。
     *
     * @param sessionId 会话 ID
     * @return 消息列表
     */
    public List<ChatMessageVo> sessionMessages(String sessionId) {
        // 校验会话存在
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_chat_session WHERE deleted = 0 AND id = ?",
                Long.class,
                sessionId);
        // 会话不存在
        if (n == null || n == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found");
        }
        // 按时间正序拉取消息
        return jdbcTemplate.query(
                """
                SELECT * FROM t_chat_message
                WHERE deleted = 0 AND session_id = ?
                ORDER BY create_time ASC
                """,
                (rs, i) -> {
                    ChatMessageVo vo = new ChatMessageVo();
                    vo.setId(rs.getString("id"));
                    vo.setSessionId(rs.getString("session_id"));
                    vo.setRole(rs.getString("role"));
                    vo.setContent(rs.getString("content"));
                    vo.setAgentRunId(rs.getString("agent_run_id"));
                    vo.setFeedback(rs.getString("feedback"));
                    Timestamp ct = rs.getTimestamp("create_time");
                    vo.setCreateTime(DateTimes.format(ct));
                    Object sj = rs.getObject("sources_json");
                    // 有 sources_json 则反序列化
                    if (sj != null) {
                        // 尝试执行
                        try {
                            // 调用 ObjectMapper 解析引用来源列表
                            vo.setSources(objectMapper.readValue(
                                    String.valueOf(sj),
                                    // 调用 objectMapper.getTypeFactory
                                    objectMapper.getTypeFactory()
                                            .constructCollectionType(List.class, Map.class)));
                        } catch (Exception ignored) {
                        }
                    }
                    return vo;
                },
                sessionId);
    }

    /**
     * 分页查询助手消息反馈列表。
     *
     * @param feedback 反馈类型过滤
     * @param settled  是否已沉淀
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public PageResult<ChatFeedbackItemVo> pageFeedback(
            String feedback, Integer settled, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        StringBuilder where = new StringBuilder(
                " WHERE m.deleted = 0 AND m.role = 'ASSISTANT' AND m.feedback IS NOT NULL AND m.feedback <> ''");
        ArrayList<Object> args = new ArrayList<>();
        // 按反馈类型过滤
        if (feedback != null && !feedback.isBlank()) {
            where.append(" AND m.feedback = ?");
            args.add(feedback.trim().toUpperCase(Locale.ROOT));
        }
        // 按是否沉淀过滤
        if (settled != null) {
            where.append(" AND IFNULL(m.settled, 0) = ?");
            args.add(settled);
        }
        // 统计反馈总数
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_chat_message m" + where, Long.class, args.toArray());
        ArrayList<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeSize);
        listArgs.add((safePage - 1) * safeSize);
        // 分页查询反馈及对应用户问题
        List<ChatFeedbackItemVo> records = jdbcTemplate.query(
                """
                SELECT m.*, a.name AS app_name,
                  (SELECT u.content FROM t_chat_message u
                     WHERE u.deleted = 0 AND u.session_id = m.session_id AND u.role = 'USER'
                       AND u.create_time <= m.create_time
                     ORDER BY u.create_time DESC LIMIT 1) AS question
                FROM t_chat_message m
                LEFT JOIN t_chat_session s ON s.id = m.session_id
                LEFT JOIN t_qa_app a ON a.id = s.app_id AND a.deleted = 0
                """
                        + where
                        + " ORDER BY m.create_time DESC LIMIT ? OFFSET ?",
                (rs, n) -> {
                    ChatFeedbackItemVo vo = new ChatFeedbackItemVo();
                    vo.setId(rs.getString("id"));
                    vo.setSessionId(rs.getString("session_id"));
                    vo.setFeedback(rs.getString("feedback"));
                    vo.setQuestion(rs.getString("question"));
                    vo.setAnswer(rs.getString("content"));
                    vo.setFeedbackReason(rs.getString("feedback_reason"));
                    // 尝试执行
                    try {
                        vo.setSettled(rs.getInt("settled"));
                        // NULL 视为未沉淀
                        if (rs.wasNull()) vo.setSettled(0);
                    } catch (Exception e) {
                        // 旧表无 settled 列
                        vo.setSettled(0);
                    }
                    vo.setAppName(rs.getString("app_name"));
                    Timestamp ct = rs.getTimestamp("create_time");
                    vo.setCreateTime(DateTimes.format(ct));
                    return vo;
                },
                listArgs.toArray());
        return PageResult.of(total == null ? 0L : total, safePage, safeSize, records);
    }

    /**
     * 更新反馈是否已沉淀到评测集。
     *
     * @param messageId 消息 ID
     * @param settled   0/1
     */

    @Transactional
    public void updateSettled(String messageId, int settled) {
        // settled 仅允许 0 或 1
        if (settled != 0 && settled != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "settled must be 0 or 1");
        }
        // 更新沉淀标记
        int n = jdbcTemplate.update(
                """
                UPDATE t_chat_message
                SET settled = ?, update_time = ?
                WHERE id = ? AND deleted = 0 AND feedback IS NOT NULL AND feedback <> ''
                """,
                settled,
                Timestamp.valueOf(LocalDateTime.now()),
                messageId);
        // 未命中则 404
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "feedback not found");
        }
    }
}

