package com.notemind.application.service.agent;

import com.notemind.common.result.PageResult;
import com.notemind.common.util.DateTimes;
import com.notemind.interfaces.agent.vo.AgentToolCallLogVo;
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
import java.util.Locale;
import java.util.UUID;

/**
 * Agent 工具调用日志应用服务：落库真实调用、分页查询与删除。
 */

@Service
public class AgentToolCallLogAsvc {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 注入 JDBC 模板。
     *
     * @param jdbcTemplate Spring JdbcTemplate
     */
    public AgentToolCallLogAsvc(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 记录一次真实工具调用（Agent / Agentic 问答检索等）。
     * status 建议：OK / FAILED
     *
     * @param toolCode     工具编码
     * @param agentRunId   关联 Agent 运行 ID，可空
     * @param inputJson    入参 JSON
     * @param outputJson   出参 JSON
     * @param status       状态
     * @param latencyMs    耗时毫秒
     * @param errorMessage 错误信息
     */

    @Transactional
    public void record(
            String toolCode,
            String agentRunId,
            String inputJson,
            String outputJson,
            String status,
            Integer latencyMs,
            String errorMessage) {
        // 无工具编码则跳过
        if (toolCode == null || toolCode.isBlank()) {
            return;
        }
        String code = toolCode.trim();
        String toolId = null;
        // 尝试执行
        try {
            // 按 code 反查工具主键
            List<String> ids = jdbcTemplate.query(
                    "SELECT id FROM t_agent_tool WHERE deleted = 0 AND code = ? LIMIT 1",
                    (rs, n) -> rs.getString("id"),
                    code);
            // 找到则绑定 toolId
            if (!ids.isEmpty()) {
                toolId = ids.get(0);
            }
        } catch (Exception ignored) {
            /* 工具未登记时仍落日志 */
        }
        String id = "tcl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String st = status == null || status.isBlank() ? "OK" : status.trim().toUpperCase(Locale.ROOT);
        // SUCCESS 统一规范为 OK
        if ("SUCCESS".equals(st)) {
            st = "OK";
        }
        // 插入调用日志
        jdbcTemplate.update(
                """
                INSERT INTO t_agent_tool_call_log (
                  id, create_time, tool_id, tool_code, agent_run_id,
                  input_json, output_json, status, latency_ms, error_message
                ) VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?, ?)
                """,
                id,
                Timestamp.valueOf(LocalDateTime.now()),
                toolId,
                code,
                blankToNull(agentRunId),
                blankToNull(inputJson) == null ? "{}" : inputJson,
                blankToNull(outputJson),
                st,
                latencyMs,
                abbreviate(errorMessage, 1000));
    }

    /**
     * 空白字符串转 null。
     *
     * @param s 原字符串
     * @return trim 后非空字符串或 null
     */
    private static String blankToNull(String s) {
        // 空则 null
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }

    /**
     * 截断过长错误信息。
     *
     * @param s   原文
     * @param max 最大长度
     * @return 截断后文本或 null
     */
    private static String abbreviate(String s, int max) {
        // 空则 null
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    /**
     * 分页查询工具调用日志。
     *
     * @param toolCode   工具编码过滤
     * @param status     状态过滤
     * @param agentRunId 运行 ID 模糊
     * @param page       页码
     * @param pageSize   每页条数
     * @return 分页结果
     */
    public PageResult<AgentToolCallLogVo> page(
            String toolCode, String status, String agentRunId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        ArrayList<Object> args = new ArrayList<>();
        // 按工具编码过滤
        if (toolCode != null && !toolCode.isBlank()) {
            where.append(" AND l.tool_code = ?");
            args.add(toolCode.trim());
        }
        // 按状态过滤（兼容 SUCCESS/FAILED 别名）
        if (status != null && !status.isBlank()) {
            String s = status.trim().toUpperCase(Locale.ROOT);
            // 成功态
            if ("OK".equals(s) || "SUCCESS".equals(s)) {
                where.append(" AND l.status = 'OK'");
            } else if ("FAIL".equals(s) || "FAILED".equals(s) || "ERROR".equals(s)) {
                // 失败态
                where.append(" AND l.status <> 'OK'");
            } else {
                // 其它精确匹配
                where.append(" AND l.status = ?");
                args.add(s);
            }
        }
        // 按运行 ID 模糊过滤
        if (agentRunId != null && !agentRunId.isBlank()) {
            where.append(" AND l.agent_run_id LIKE ?");
            args.add("%" + agentRunId.trim() + "%");
        }
        // 统计总数
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_agent_tool_call_log l" + where, Long.class, args.toArray());
        ArrayList<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeSize);
        listArgs.add((safePage - 1) * safeSize);
        // 分页列表（联表工具名）
        List<AgentToolCallLogVo> records = jdbcTemplate.query(
                """
                SELECT l.*, t.name AS tool_name
                FROM t_agent_tool_call_log l
                LEFT JOIN t_agent_tool t ON t.code = l.tool_code AND t.deleted = 0
                """
                        + where
                        + " ORDER BY l.create_time DESC LIMIT ? OFFSET ?",
                mapper(),
                listArgs.toArray());
        return PageResult.of(total == null ? 0L : total, safePage, safeSize, records);
    }

    /**
     * 按 ID 查询日志详情。
     *
     * @param id 日志 ID
     * @return 日志 VO
     */
    public AgentToolCallLogVo getById(String id) {
        // 按主键查询
        List<AgentToolCallLogVo> rows = jdbcTemplate.query(
                """
                SELECT l.*, t.name AS tool_name
                FROM t_agent_tool_call_log l
                LEFT JOIN t_agent_tool t ON t.code = l.tool_code AND t.deleted = 0
                WHERE l.id = ?
                """,
                mapper(),
                id);
        // 不存在则 404
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "tool call log not found");
        }
        return rows.get(0);
    }

    /**
     * 物理删除单条日志。
     *
     * @param id 日志 ID
     */

    @Transactional
    public void delete(String id) {
        getById(id);
        // 物理删除日志行
        jdbcTemplate.update("DELETE FROM t_agent_tool_call_log WHERE id = ?", id);
    }

    /**
     * 批量物理删除日志。
     *
     * @param ids 日志 ID 列表
     * @return 删除行数
     */

    @Transactional
    public int batchDelete(List<String> ids) {
        // 空列表直接 0
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        // 遍历处理
        for (String raw : ids) {
            // 跳过空 ID
            if (raw == null || raw.isBlank()) continue;
            // 按 ID 物理删除
            deleted += jdbcTemplate.update("DELETE FROM t_agent_tool_call_log WHERE id = ?", raw.trim());
        }
        return deleted;
    }

    /**
     * 构建日志行映射器。
     *
     * @return RowMapper
     */
    private RowMapper<AgentToolCallLogVo> mapper() {
        return (rs, rowNum) -> {
            AgentToolCallLogVo vo = new AgentToolCallLogVo();
            vo.setId(rs.getString("id"));
            vo.setToolId(rs.getString("tool_id"));
            vo.setToolCode(rs.getString("tool_code"));
            // 尝试执行
            try {
                vo.setToolName(rs.getString("tool_name"));
            } catch (Exception ignored) {
                // 无联表列时置空
                vo.setToolName(null);
            }
            vo.setAgentRunId(rs.getString("agent_run_id"));
            Object in = rs.getObject("input_json");
            Object out = rs.getObject("output_json");
            vo.setInputJson(in == null ? null : String.valueOf(in));
            vo.setOutputJson(out == null ? null : String.valueOf(out));
            vo.setStatus(rs.getString("status"));
            int lat = rs.getInt("latency_ms");
            vo.setLatencyMs(rs.wasNull() ? null : lat);
            vo.setErrorMessage(rs.getString("error_message"));
            Timestamp ct = rs.getTimestamp("create_time");
            vo.setCreateTime(DateTimes.format(ct));
            return vo;
        };
    }
}

