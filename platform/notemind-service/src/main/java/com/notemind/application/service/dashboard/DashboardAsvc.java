package com.notemind.application.service.dashboard;

import com.notemind.common.util.DateTimes;
import com.notemind.interfaces.dashboard.vo.DashboardOverviewVo;
import com.notemind.interfaces.dashboard.vo.DashboardOverviewVo.DailyUsage;
import com.notemind.interfaces.dashboard.vo.DashboardOverviewVo.HotQuestion;
import com.notemind.interfaces.dashboard.vo.DashboardOverviewVo.NamedCount;
import com.notemind.interfaces.dashboard.vo.DashboardOverviewVo.Summary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 工作台应用服务：KPI、热门知识库、高频提问与模型调用统计。
 */
@Service
public class DashboardAsvc {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造 DashboardAsvc 并注入依赖。
     * @param jdbcTemplate 参数 jdbcTemplate
     */
    public DashboardAsvc(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 汇总工作台概览数据。
     * @param days 参数 days
     * @return 返回结果
     */
    public DashboardOverviewVo overview(int days) {
        int safeDays = Math.min(Math.max(days, 7), 90);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(safeDays - 1L);
        Timestamp since = Timestamp.valueOf(LocalDateTime.of(start, LocalTime.MIN));

        DashboardOverviewVo vo = new DashboardOverviewVo();
        vo.setDays(safeDays);
        vo.setSummary(buildSummary(since));
        vo.setHotKnowledgeBases(hotKnowledgeBases(since));
        vo.setHotQuestions(hotQuestions(since));
        vo.setUsageTrend(usageTrend(start, end, since));
        List<NamedCount> models = modelCalls(since);
        vo.setModelCalls(models);
        vo.setModelTypeCalls(modelTypeCalls(models));
        return vo;
    }

    /**
     * buildSummary：业务处理。
     * @param since 参数 since
     * @return 返回结果
     */
    private Summary buildSummary(Timestamp since) {
        Summary s = new Summary();
        s.setKnowledgeBaseCount(count("SELECT COUNT(1) FROM t_knowledge_base WHERE deleted = 0"));
        s.setDocumentCount(count("SELECT COUNT(1) FROM t_knowledge_document WHERE deleted = 0"));
        s.setChatSessionCount(count(
                "SELECT COUNT(1) FROM t_chat_session WHERE deleted = 0 AND create_time >= ?", since));
        s.setQuestionCount(count(
                """
                SELECT COUNT(1) FROM t_chat_message
                WHERE deleted = 0 AND role = 'USER' AND create_time >= ?
                """,
                since)
                + count(
                        "SELECT COUNT(1) FROM t_agent_run WHERE deleted = 0 AND create_time >= ?",
                        since));
        s.setAgentRunCount(count(
                "SELECT COUNT(1) FROM t_agent_run WHERE deleted = 0 AND create_time >= ?", since));
        s.setToolCallCount(count(
                "SELECT COUNT(1) FROM t_agent_tool_call_log WHERE create_time >= ?", since));
        return s;
    }

    /**
     * hotKnowledgeBases：业务处理。
     * @param since 参数 since
     * @return 返回结果
     */
    private List<NamedCount> hotKnowledgeBases(Timestamp since) {
        String sql =
                """
                SELECT kb.id AS id,
                       COALESCE(NULLIF(kb.name, ''), kb.id) AS name,
                       SUM(u.cnt) AS cnt
                FROM (
                  SELECT a.knowledge_base_id AS kb_id, COUNT(1) AS cnt
                  FROM t_chat_message m
                  INNER JOIN t_chat_session s ON s.id = m.session_id AND s.deleted = 0
                  INNER JOIN t_qa_app a ON a.id = s.app_id AND a.deleted = 0
                  WHERE m.deleted = 0 AND m.role = 'USER' AND m.create_time >= ?
                    AND a.knowledge_base_id IS NOT NULL AND a.knowledge_base_id <> ''
                  GROUP BY a.knowledge_base_id
                  UNION ALL
                  SELECT r.knowledge_base_id AS kb_id, COUNT(1) AS cnt
                  FROM t_agent_run r
                  WHERE r.deleted = 0 AND r.create_time >= ?
                    AND r.knowledge_base_id IS NOT NULL AND r.knowledge_base_id <> ''
                  GROUP BY r.knowledge_base_id
                  UNION ALL
                  SELECT b.knowledge_base_id AS kb_id, COUNT(1) AS cnt
                  FROM t_recall_debug_run b
                  WHERE b.deleted = 0 AND b.create_time >= ?
                    AND b.knowledge_base_id IS NOT NULL AND b.knowledge_base_id <> ''
                  GROUP BY b.knowledge_base_id
                ) u
                INNER JOIN t_knowledge_base kb ON kb.id = u.kb_id AND kb.deleted = 0
                GROUP BY kb.id, kb.name
                ORDER BY cnt DESC
                LIMIT 8
                """;
        // 调用 jdbcTemplate.query
        return jdbcTemplate.query(
                sql,
                (rs, n) -> new NamedCount(rs.getString("id"), rs.getString("name"), rs.getLong("cnt")),
                since,
                since,
                since);
    }

    /**
     * hotQuestions：业务处理。
     * @param since 参数 since
     * @return 返回结果
     */
    private List<HotQuestion> hotQuestions(Timestamp since) {
        Map<String, HotQuestion> map = new LinkedHashMap<>();
        // 调用 jdbcTemplate.query
        jdbcTemplate.query(
                """
                SELECT TRIM(content) AS q, COUNT(1) AS cnt, MAX(create_time) AS last_t
                FROM t_chat_message
                WHERE deleted = 0 AND role = 'USER' AND create_time >= ?
                  AND TRIM(content) <> ''
                  AND CHAR_LENGTH(TRIM(content)) BETWEEN 2 AND 200
                GROUP BY TRIM(content)
                ORDER BY cnt DESC
                LIMIT 30
                """,
                rs -> {
                    // 循环处理
                    while (rs.next()) {
                        mergeQuestion(map, rs.getString("q"), rs.getLong("cnt"), rs.getTimestamp("last_t"));
                    }
                    return null;
                },
                since);
        // 调用 jdbcTemplate.query
        jdbcTemplate.query(
                """
                SELECT TRIM(question) AS q, COUNT(1) AS cnt, MAX(create_time) AS last_t
                FROM t_agent_run
                WHERE deleted = 0 AND create_time >= ?
                  AND TRIM(question) <> ''
                  AND CHAR_LENGTH(TRIM(question)) BETWEEN 2 AND 200
                GROUP BY TRIM(question)
                ORDER BY cnt DESC
                LIMIT 30
                """,
                rs -> {
                    // 循环处理
                    while (rs.next()) {
                        mergeQuestion(map, rs.getString("q"), rs.getLong("cnt"), rs.getTimestamp("last_t"));
                    }
                    return null;
                },
                since);
        List<HotQuestion> list = new ArrayList<>(map.values());
        list.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));
        // 条件判断
        if (list.size() > 10) {
            return list.subList(0, 10);
        }
        return list;
    }

    /**
     * mergeQuestion：业务处理。
     * @param map 参数 map
     * @param q 参数 q
     * @param cnt 参数 cnt
     * @param lastT 参数 lastT
     */
    private static void mergeQuestion(
            Map<String, HotQuestion> map, String q, long cnt, Timestamp lastT) {
        // 条件判断
        if (q == null || q.isBlank()) return;
        String key = q.trim();
        HotQuestion hq = map.get(key);
        // 条件判断
        if (hq == null) {
            hq = new HotQuestion();
            hq.setQuestion(key.length() > 80 ? key.substring(0, 80) + "…" : key);
            hq.setCount(cnt);
            hq.setLastAskTime(lastT == null ? null : DateTimes.format(lastT.toLocalDateTime()));
            map.put(key, hq);
        } else {
            hq.setCount(hq.getCount() + cnt);
            String t = lastT == null ? null : DateTimes.format(lastT.toLocalDateTime());
            // 条件判断
            if (t != null && (hq.getLastAskTime() == null || t.compareTo(hq.getLastAskTime()) > 0)) {
                hq.setLastAskTime(t);
            }
        }
    }

    /**
     * usageTrend：业务处理。
     * @param start 参数 start
     * @param end 参数 end
     * @param since 参数 since
     * @return 返回结果
     */
    private List<DailyUsage> usageTrend(LocalDate start, LocalDate end, Timestamp since) {
        Map<String, DailyUsage> byDay = new LinkedHashMap<>();
        // 遍历处理
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            DailyUsage u = new DailyUsage();
            u.setDate(d.toString());
            byDay.put(d.toString(), u);
        }
        fillDaily(
                byDay,
                """
                SELECT DATE(create_time) AS d, COUNT(1) AS c
                FROM t_chat_message
                WHERE deleted = 0 AND role = 'USER' AND create_time >= ?
                GROUP BY DATE(create_time)
                """,
                since,
                (u, c) -> u.setQuestions(u.getQuestions() + c));
        fillDaily(
                byDay,
                """
                SELECT DATE(create_time) AS d, COUNT(1) AS c
                FROM t_agent_run
                WHERE deleted = 0 AND create_time >= ?
                GROUP BY DATE(create_time)
                """,
                since,
                (u, c) -> {
                    u.setAgentRuns(c);
                    u.setQuestions(u.getQuestions() + c);
                });
        fillDaily(
                byDay,
                """
                SELECT DATE(create_time) AS d, COUNT(1) AS c
                FROM t_agent_tool_call_log
                WHERE create_time >= ?
                GROUP BY DATE(create_time)
                """,
                since,
                (u, c) -> u.setToolCalls(c));
        fillDaily(
                byDay,
                """
                SELECT DATE(create_time) AS d, COUNT(1) AS c
                FROM t_chat_message
                WHERE deleted = 0 AND role = 'ASSISTANT' AND create_time >= ?
                GROUP BY DATE(create_time)
                """,
                since,
                (u, c) -> u.setChatCalls(u.getChatCalls() + c));
        fillDaily(
                byDay,
                """
                SELECT DATE(create_time) AS d, COUNT(1) AS c
                FROM t_agent_run
                WHERE deleted = 0 AND create_time >= ?
                GROUP BY DATE(create_time)
                """,
                since,
                (u, c) -> u.setChatCalls(u.getChatCalls() + c));
        return new ArrayList<>(byDay.values());
    }

    @FunctionalInterface
    private interface DailySetter {
        void apply(DailyUsage u, long c);
    }

    /**
     * fillDaily：业务处理。
     * @param byDay 参数 byDay
     * @param sql 参数 sql
     * @param since 参数 since
     * @param setter 参数 setter
     */
    private void fillDaily(
            Map<String, DailyUsage> byDay, String sql, Timestamp since, DailySetter setter) {
        // 调用 jdbcTemplate.query
        jdbcTemplate.query(
                sql,
                rs -> {
                    // 循环处理
                    while (rs.next()) {
                        Object dObj = rs.getObject("d");
                        String day = dObj == null ? null : String.valueOf(dObj);
                        // 条件判断
                        if (day != null && day.length() >= 10) {
                            day = day.substring(0, 10);
                        }
                        DailyUsage u = day == null ? null : byDay.get(day);
                        // 条件判断
                        if (u != null) {
                            setter.apply(u, rs.getLong("c"));
                        }
                    }
                    return null;
                },
                since);
    }

    /** 对话模型调用：会话助手消息 + Agent 运行，按应用绑定的 chat_model 聚合 */
    private List<NamedCount> modelCalls(Timestamp since) {
        Map<String, NamedCount> map = new LinkedHashMap<>();
        // 调用 jdbcTemplate.query
        jdbcTemplate.query(
                """
                SELECT COALESCE(mc.id, 'unknown') AS id,
                       COALESCE(NULLIF(mc.name, ''), mc.model_name, '未命名对话模型') AS name,
                       COALESCE(mc.model_type, 'CHAT') AS mtype,
                       COUNT(1) AS cnt
                FROM t_chat_message m
                INNER JOIN t_chat_session s ON s.id = m.session_id AND s.deleted = 0
                INNER JOIN t_qa_app a ON a.id = s.app_id AND a.deleted = 0
                LEFT JOIN t_ai_model_config mc ON mc.id = a.chat_model_id AND mc.deleted = 0
                WHERE m.deleted = 0 AND m.role = 'ASSISTANT' AND m.create_time >= ?
                GROUP BY mc.id, mc.name, mc.model_name, mc.model_type
                """,
                rs -> {
                    // 循环处理
                    while (rs.next()) {
                        addNamed(map, rs.getString("id"), rs.getString("name"), rs.getString("mtype"), rs.getLong("cnt"));
                    }
                    return null;
                },
                since);
        // 调用 jdbcTemplate.query
        jdbcTemplate.query(
                """
                SELECT COALESCE(mc.id, 'unknown') AS id,
                       COALESCE(NULLIF(mc.name, ''), mc.model_name, '未命名对话模型') AS name,
                       COALESCE(mc.model_type, 'CHAT') AS mtype,
                       COUNT(1) AS cnt
                FROM t_agent_run r
                LEFT JOIN t_qa_app a ON a.id = r.app_id AND a.deleted = 0
                LEFT JOIN t_ai_model_config mc ON mc.id = a.chat_model_id AND mc.deleted = 0
                WHERE r.deleted = 0 AND r.create_time >= ?
                GROUP BY mc.id, mc.name, mc.model_name, mc.model_type
                """,
                rs -> {
                    // 循环处理
                    while (rs.next()) {
                        addNamed(map, rs.getString("id"), rs.getString("name"), rs.getString("mtype"), rs.getLong("cnt"));
                    }
                    return null;
                },
                since);
        // 检索工具调用 → 记到默认向量 / 重排模型（有配置时）
        long retrieveCalls = count(
                "SELECT COUNT(1) FROM t_agent_tool_call_log WHERE create_time >= ? AND tool_code = 'search_knowledge'",
                since);
        // 条件判断
        if (retrieveCalls > 0) {
            addDefaultModel(map, "EMBEDDING", retrieveCalls);
            addDefaultModel(map, "RERANK", retrieveCalls);
        }
        List<NamedCount> list = new ArrayList<>(map.values());
        list.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));
        // 条件判断
        if (list.size() > 10) {
            return list.subList(0, 10);
        }
        return list;
    }

    /**
     * modelTypeCalls：业务处理。
     * @param models 参数 models
     * @return 返回结果
     */
    private List<NamedCount> modelTypeCalls(List<NamedCount> models) {
        Map<String, Long> typeMap = new HashMap<>();
        // 遍历处理
        for (NamedCount n : models) {
            String t = n.getExtra() == null || n.getExtra().isBlank()
                    ? "CHAT"
                    : n.getExtra().toUpperCase(Locale.ROOT);
            typeMap.merge(t, n.getCount(), Long::sum);
        }
        List<NamedCount> list = new ArrayList<>();
        // 遍历处理
        for (String t : List.of("CHAT", "EMBEDDING", "RERANK")) {
            Long c = typeMap.get(t);
            // 条件判断
            if (c != null && c > 0) {
                list.add(new NamedCount(t, typeLabel(t), t, c));
            }
        }
        // 遍历处理
        for (Map.Entry<String, Long> e : typeMap.entrySet()) {
            // 条件判断
            if (!List.of("CHAT", "EMBEDDING", "RERANK").contains(e.getKey())) {
                list.add(new NamedCount(e.getKey(), e.getKey(), e.getKey(), e.getValue()));
            }
        }
        return list;
    }

    /**
     * addDefaultModel：业务处理。
     * @param map 参数 map
     * @param modelType 参数 modelType
     * @param cnt 参数 cnt
     */
    private void addDefaultModel(Map<String, NamedCount> map, String modelType, long cnt) {
        // 调用 jdbcTemplate.queryForList
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, name, model_name, model_type
                FROM t_ai_model_config
                WHERE deleted = 0 AND enabled = 1 AND UPPER(model_type) = ?
                ORDER BY update_time DESC
                LIMIT 1
                """,
                modelType);
        // 条件判断
        if (rows.isEmpty()) {
            addNamed(map, modelType.toLowerCase(Locale.ROOT), typeLabel(modelType) + "（未配置）", modelType, cnt);
            return;
        }
        Map<String, Object> row = rows.get(0);
        String id = String.valueOf(row.get("id"));
        String name = row.get("name") != null && !String.valueOf(row.get("name")).isBlank()
                ? String.valueOf(row.get("name"))
                : String.valueOf(row.get("model_name"));
        addNamed(map, id, name, modelType, cnt);
    }

    /**
     * addNamed：业务处理。
     * @param map 参数 map
     * @param id 参数 id
     * @param name 参数 name
     * @param type 参数 type
     * @param cnt 参数 cnt
     */
    private static void addNamed(
            Map<String, NamedCount> map, String id, String name, String type, long cnt) {
        String key = id == null ? "unknown" : id;
        NamedCount n = map.get(key);
        // 条件判断
        if (n == null) {
            map.put(key, new NamedCount(key, name == null ? key : name, type, cnt));
        } else {
            n.setCount(n.getCount() + cnt);
        }
    }

    /**
     * typeLabel：业务处理。
     * @param t 参数 t
     * @return 返回结果
     */
    private static String typeLabel(String t) {
        // 条件判断
        if ("CHAT".equalsIgnoreCase(t)) return "对话模型";
        // 条件判断
        if ("EMBEDDING".equalsIgnoreCase(t)) return "向量模型";
        // 条件判断
        if ("RERANK".equalsIgnoreCase(t)) return "重排模型";
        return t;
    }

    /**
     * count：业务处理。
     * @param sql 参数 sql
     * @param args 参数 args
     * @return 返回结果
     */
    private long count(String sql, Object... args) {
        Long n = args == null || args.length == 0
                // 调用 jdbcTemplate.queryForObject
                ? jdbcTemplate.queryForObject(sql, Long.class)
                // 调用 jdbcTemplate.queryForObject
                : jdbcTemplate.queryForObject(sql, Long.class, args);
        return n == null ? 0L : n;
    }
}
