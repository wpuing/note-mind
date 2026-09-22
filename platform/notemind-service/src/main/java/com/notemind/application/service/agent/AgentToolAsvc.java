package com.notemind.application.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.notemind.common.result.PageResult;
import com.notemind.common.util.DateTimes;
import com.notemind.interfaces.agent.vo.AgentToolSaveRequest;
import com.notemind.interfaces.agent.vo.AgentToolStatVo;
import com.notemind.interfaces.agent.vo.AgentToolVo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 工具中心应用服务：工具登记 CRUD、启用开关与调用统计。
 */
@Service
public class AgentToolAsvc {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 构造 AgentToolAsvc 并注入依赖。
     * @param jdbcTemplate 参数 jdbcTemplate
     * @param objectMapper 参数 objectMapper
     */
    public AgentToolAsvc(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 分页查询。
     * @param name 参数 name
     * @param enabled 参数 enabled
     * @param page 参数 page
     * @param pageSize 参数 pageSize
     * @return 返回结果
     */
    public PageResult<AgentToolVo> page(String name, Integer enabled, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        StringBuilder where = new StringBuilder(" WHERE deleted = 0");
        ArrayList<Object> args = new ArrayList<>();
        // 条件判断
        if (name != null && !name.isBlank()) {
            where.append(" AND name LIKE ?");
            args.add("%" + name.trim() + "%");
        }
        // 条件判断
        if (enabled != null) {
            where.append(" AND enabled = ?");
            args.add(enabled);
        }
        // 调用 jdbcTemplate.queryForObject
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_agent_tool" + where, Long.class, args.toArray());
        ArrayList<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeSize);
        listArgs.add((safePage - 1) * safeSize);
        // 调用 jdbcTemplate.query
        List<AgentToolVo> records = jdbcTemplate.query(
                "SELECT * FROM t_agent_tool"
                        + where
                        + " ORDER BY sort_no ASC, create_time DESC LIMIT ? OFFSET ?",
                mapper(),
                listArgs.toArray());
        return PageResult.of(total == null ? 0L : total, safePage, safeSize, records);
    }

    /**
     * 按 ID 查询详情。
     * @param id 参数 id
     * @return 返回结果
     */
    public AgentToolVo getById(String id) {
        // 调用 jdbcTemplate.query
        List<AgentToolVo> rows = jdbcTemplate.query(
                "SELECT * FROM t_agent_tool WHERE deleted = 0 AND id = ?", mapper(), id);
        // 条件判断
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "agent tool not found");
        }
        return rows.get(0);
    }

    /**
     * 新建记录。
     * @param req 参数 req
     * @return 返回结果
     */
    @Transactional
    public AgentToolVo create(AgentToolSaveRequest req) {
        validate(req);
        String id = "tool_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        LocalDateTime now = LocalDateTime.now();
        String code = normalizeCode(req.getCode());
        String schema = normalizeSchemaJson(req.getSchemaJson());
        // 尝试执行
        try {
            // 调用 jdbcTemplate.update
            jdbcTemplate.update(
                    """
                    INSERT INTO t_agent_tool (
                      id, create_time, update_time, deleted,
                      code, name, description, schema_json, enabled, sort_no, implemented
                    ) VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    id,
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now),
                    code,
                    req.getName().trim(),
                    blankToNull(req.getDescription()),
                    schema,
                    req.getEnabled() == null ? 1 : req.getEnabled(),
                    req.getSortNo() == null ? 0 : req.getSortNo(),
                    req.getImplemented() == null ? 1 : req.getImplemented());
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工具编码已存在");
        }
        return getById(id);
    }

    /**
     * 更新记录。
     * @param id 参数 id
     * @param req 参数 req
     * @return 返回结果
     */
    @Transactional
    public AgentToolVo update(String id, AgentToolSaveRequest req) {
        getById(id);
        validate(req);
        LocalDateTime now = LocalDateTime.now();
        String code = normalizeCode(req.getCode());
        String schema = normalizeSchemaJson(req.getSchemaJson());
        // 尝试执行
        try {
            // 调用 jdbcTemplate.update
            jdbcTemplate.update(
                    """
                    UPDATE t_agent_tool
                    SET code = ?, name = ?, description = ?, schema_json = ?,
                        enabled = ?, sort_no = ?, implemented = ?, update_time = ?
                    WHERE id = ? AND deleted = 0
                    """,
                    code,
                    req.getName().trim(),
                    blankToNull(req.getDescription()),
                    schema,
                    req.getEnabled() == null ? 1 : req.getEnabled(),
                    req.getSortNo() == null ? 0 : req.getSortNo(),
                    req.getImplemented() == null ? 1 : req.getImplemented(),
                    Timestamp.valueOf(now),
                    id);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工具编码已存在");
        }
        return getById(id);
    }

    /**
     * updateEnabled：业务处理。
     * @param id 参数 id
     * @param enabled 参数 enabled
     * @return 返回结果
     */
    @Transactional
    public AgentToolVo updateEnabled(String id, int enabled) {
        getById(id);
        // 条件判断
        if (enabled != 0 && enabled != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enabled must be 0 or 1");
        }
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                "UPDATE t_agent_tool SET enabled = ?, update_time = ? WHERE id = ? AND deleted = 0",
                enabled,
                Timestamp.valueOf(LocalDateTime.now()),
                id);
        return getById(id);
    }

    /**
     * 删除记录。
     * @param id 参数 id
     */
    @Transactional
    public void delete(String id) {
        getById(id);
        LocalDateTime now = LocalDateTime.now();
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                """
                UPDATE t_agent_tool
                SET deleted = 1, delete_time = ?, update_time = ?
                WHERE id = ? AND deleted = 0
                """,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                id);
    }

    /**
     * 批量删除。
     * @param ids 参数 ids
     * @return 返回结果
     */
    @Transactional
    public int batchDelete(List<String> ids) {
        // 条件判断
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        Timestamp ts = Timestamp.valueOf(now);
        int deleted = 0;
        // 遍历处理
        for (String raw : ids) {
            // 条件判断
            if (raw == null || raw.isBlank()) continue;
            // 调用 jdbcTemplate.update
            deleted += jdbcTemplate.update(
                    """
                    UPDATE t_agent_tool
                    SET deleted = 1, delete_time = ?, update_time = ?
                    WHERE id = ? AND deleted = 0
                    """,
                    ts,
                    ts,
                    raw.trim());
        }
        return deleted;
    }

    /** 已启用工具 → OpenAI tools[]；codes 逗号分隔时仅导出指定编码 */
    public String openaiDefinitionsJson(String codesCsv) {
        List<String> codeFilter = new ArrayList<>();
        // 条件判断
        if (codesCsv != null && !codesCsv.isBlank()) {
            // 遍历处理
            for (String p : codesCsv.split(",")) {
                String c = p.trim();
                // 条件判断
                if (!c.isEmpty()) codeFilter.add(c.toLowerCase(Locale.ROOT));
            }
        }
        StringBuilder sql = new StringBuilder(
                """
                SELECT * FROM t_agent_tool
                WHERE deleted = 0 AND enabled = 1 AND implemented = 1
                """);
        ArrayList<Object> args = new ArrayList<>();
        // 条件判断
        if (!codeFilter.isEmpty()) {
            sql.append(" AND code IN (");
            // 遍历处理
            for (int i = 0; i < codeFilter.size(); i++) {
                // 条件判断
                if (i > 0) sql.append(',');
                sql.append('?');
                args.add(codeFilter.get(i));
            }
            sql.append(')');
        }
        sql.append(" ORDER BY sort_no ASC, create_time ASC");
        List<AgentToolVo> tools = args.isEmpty()
                // 调用 jdbcTemplate.query
                ? jdbcTemplate.query(sql.toString(), mapper())
                // 调用 jdbcTemplate.query
                : jdbcTemplate.query(sql.toString(), mapper(), args.toArray());
        // 尝试执行
        try {
            // 调用 objectMapper.createArrayNode
            ArrayNode arr = objectMapper.createArrayNode();
            // 遍历处理
            for (AgentToolVo tool : tools) {
                arr.add(toOpenAiTool(tool));
            }
            // 调用 objectMapper.writerWithDefaultPrettyPrinter
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "build tools json failed");
        }
    }

    /**
     * 统计汇总。
     * @param codesCsv 参数 codesCsv
     * @return 返回结果
     */
    public List<AgentToolStatVo> stats(String codesCsv) {
        List<String> codeFilter = new ArrayList<>();
        // 条件判断
        if (codesCsv != null && !codesCsv.isBlank()) {
            // 遍历处理
            for (String p : codesCsv.split(",")) {
                String c = p.trim();
                // 条件判断
                if (!c.isEmpty()) codeFilter.add(c.toLowerCase(Locale.ROOT));
            }
        }
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM t_agent_tool WHERE deleted = 0");
        ArrayList<Object> args = new ArrayList<>();
        // 条件判断
        if (!codeFilter.isEmpty()) {
            sql.append(" AND code IN (");
            // 遍历处理
            for (int i = 0; i < codeFilter.size(); i++) {
                // 条件判断
                if (i > 0) sql.append(',');
                sql.append('?');
                args.add(codeFilter.get(i));
            }
            sql.append(')');
        }
        sql.append(" ORDER BY sort_no ASC");
        List<AgentToolVo> tools = args.isEmpty()
                // 调用 jdbcTemplate.query
                ? jdbcTemplate.query(sql.toString(), mapper())
                // 调用 jdbcTemplate.query
                : jdbcTemplate.query(sql.toString(), mapper(), args.toArray());
        Map<String, AgentToolStatVo> byCode = new LinkedHashMap<>();
        // 遍历处理
        for (AgentToolVo t : tools) {
            AgentToolStatVo s = new AgentToolStatVo();
            s.setToolCode(t.getCode());
            s.setToolName(t.getName());
            s.setCallCount(0);
            s.setSuccessCount(0);
            s.setFailCount(0);
            s.setAvgLatencyMs(null);
            byCode.put(t.getCode(), s);
        }
        // 条件判断
        if (byCode.isEmpty()) {
            return List.of();
        }
        StringBuilder logSql = new StringBuilder(
                """
                SELECT tool_code,
                       COUNT(1) AS call_count,
                       SUM(CASE WHEN status = 'OK' THEN 1 ELSE 0 END) AS success_count,
                       SUM(CASE WHEN status = 'OK' THEN 0 ELSE 1 END) AS fail_count,
                       AVG(latency_ms) AS avg_latency
                FROM t_agent_tool_call_log
                WHERE tool_code IN (
                """);
        ArrayList<Object> logArgs = new ArrayList<>();
        int i = 0;
        // 遍历处理
        for (String code : byCode.keySet()) {
            // 条件判断
            if (i++ > 0) logSql.append(',');
            logSql.append('?');
            logArgs.add(code);
        }
        logSql.append(") GROUP BY tool_code");
        // 调用 jdbcTemplate.query
        jdbcTemplate.query(
                logSql.toString(),
                rs -> {
                    // 循环处理
                    while (rs.next()) {
                        String code = rs.getString("tool_code");
                        AgentToolStatVo s = byCode.get(code);
                        // 条件判断
                        if (s == null) continue;
                        s.setCallCount(rs.getLong("call_count"));
                        s.setSuccessCount(rs.getLong("success_count"));
                        s.setFailCount(rs.getLong("fail_count"));
                        double avg = rs.getDouble("avg_latency");
                        s.setAvgLatencyMs(rs.wasNull() ? null : avg);
                    }
                    return null;
                },
                logArgs.toArray());
        return new ArrayList<>(byCode.values());
    }

    /**
     * toOpenAiTool：业务处理。
     * @param tool 参数 tool
     * @return 返回结果
     */
    private ObjectNode toOpenAiTool(AgentToolVo tool) throws Exception {
        // 调用 objectMapper.createObjectNode
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "function");
        ObjectNode fn = root.putObject("function");
        fn.put("name", tool.getCode());
        fn.put("description", tool.getDescription() == null ? "" : tool.getDescription());
        ObjectNode parameters = fn.putObject("parameters");
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        Map<String, String> params = parseParamDescs(tool.getSchemaJson());
        ArrayNode required = parameters.putArray("required");
        // 遍历处理
        for (Map.Entry<String, String> e : params.entrySet()) {
            ObjectNode prop = properties.putObject(e.getKey());
            prop.put("type", "string");
            prop.put("description", e.getValue() == null ? "" : e.getValue());
            required.add(e.getKey());
        }
        return root;
    }

    /**
     * parseParamDescs：业务处理。
     * @param schemaJson 参数 schemaJson
     * @return 返回结果
     */
    private Map<String, String> parseParamDescs(String schemaJson) throws Exception {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        // 条件判断
        if (schemaJson == null || schemaJson.isBlank()) {
            return map;
        }
        // 调用 objectMapper.readTree
        JsonNode node = objectMapper.readTree(schemaJson);
        // 条件判断
        if (node.isObject()) {
            // 已是 OpenAI parameters 形态
            if (node.has("properties") && node.get("properties").isObject()) {
                node.get("properties").fields().forEachRemaining(entry -> {
                    String desc = "";
                    // 条件判断
                    if (entry.getValue().has("description")) {
                        desc = entry.getValue().get("description").asText("");
                    }
                    map.put(entry.getKey(), desc);
                });
                return map;
            }
            node.fields().forEachRemaining(entry -> {
                JsonNode v = entry.getValue();
                map.put(entry.getKey(), v.isTextual() ? v.asText() : v.toString());
            });
        }
        return map;
    }

    /**
     * 校验请求参数。
     * @param req 参数 req
     */
    private void validate(AgentToolSaveRequest req) {
        // 条件判断
        if (req == null || req.getCode() == null || req.getCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code required");
        }
        // 条件判断
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name required");
        }
        // 条件判断
        if (req.getDescription() == null || req.getDescription().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "description required");
        }
        // 条件判断
        if (req.getEnabled() != null && req.getEnabled() != 0 && req.getEnabled() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enabled must be 0 or 1");
        }
        // 条件判断
        if (req.getImplemented() != null && req.getImplemented() != 0 && req.getImplemented() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "implemented must be 0 or 1");
        }
        normalizeSchemaJson(req.getSchemaJson());
    }

    /**
     * normalizeCode：业务处理。
     * @param code 参数 code
     * @return 返回结果
     */
    private String normalizeCode(String code) {
        return code.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    /**
     * normalizeSchemaJson：业务处理。
     * @param raw 参数 raw
     * @return 返回结果
     */
    private String normalizeSchemaJson(String raw) {
        // 条件判断
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        // 尝试执行
        try {
            // 调用 objectMapper.readTree
            JsonNode node = objectMapper.readTree(raw.trim());
            // 条件判断
            if (!node.isObject()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "schemaJson must be JSON object");
            }
            // 调用 objectMapper.writeValueAsString
            return objectMapper.writeValueAsString(node);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "schemaJson invalid JSON");
        }
    }

    /**
     * blankToNull：业务处理。
     * @param s 参数 s
     * @return 返回结果
     */
    private String blankToNull(String s) {
        // 条件判断
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }

    /**
     * 构建行映射器。
     * @return 返回结果
     */
    private RowMapper<AgentToolVo> mapper() {
        return (rs, rowNum) -> {
            AgentToolVo vo = new AgentToolVo();
            vo.setId(rs.getString("id"));
            vo.setCode(rs.getString("code"));
            vo.setName(rs.getString("name"));
            vo.setDescription(rs.getString("description"));
            Object schema = rs.getObject("schema_json");
            vo.setSchemaJson(schema == null ? "{}" : String.valueOf(schema));
            vo.setEnabled(rs.getInt("enabled"));
            // 尝试执行
            try {
                vo.setSortNo(rs.getInt("sort_no"));
            } catch (Exception e) {
                vo.setSortNo(0);
            }
            // 尝试执行
            try {
                vo.setImplemented(rs.getInt("implemented"));
            } catch (Exception e) {
                vo.setImplemented(1);
            }
            Timestamp ct = rs.getTimestamp("create_time");
            vo.setCreateTime(DateTimes.format(ct));
            return vo;
        };
    }
}
