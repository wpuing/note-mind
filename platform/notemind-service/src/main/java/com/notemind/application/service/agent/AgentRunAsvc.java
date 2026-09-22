package com.notemind.application.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notemind.client.ai.AgentGraphRunRequest;
import com.notemind.client.ai.AgentGraphRunResult;
import com.notemind.client.ai.AiEngineClient;
import com.notemind.client.ai.ChatCompletionRequest;
import com.notemind.client.ai.ChatCompletionResult;
import com.notemind.client.ai.RetrievalStrategyParams;
import com.notemind.common.result.PageResult;
import com.notemind.common.util.DateTimes;
import com.notemind.interfaces.agent.vo.AgentRunExecuteRequest;
import com.notemind.interfaces.agent.vo.AgentRunVo;
import com.notemind.interfaces.agent.vo.AgentStepVo;
import com.notemind.interfaces.knowledge.vo.RetrievalStrategyVo;
import com.notemind.application.service.knowledge.KnowledgeRetrievalAsvc;
import com.notemind.application.service.knowledge.RetrievalStrategyAsvc;
import com.notemind.application.service.system.SystemConfigAsvc;
import com.notemind.infrastructure.security.ApiKeyCrypto;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 运行应用服务：触发 LangGraph、落库步骤时间线与运行记录。
 */
@Service
public class AgentRunAsvc {

    private static final Logger log = LoggerFactory.getLogger(AgentRunAsvc.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RetrievalStrategyAsvc retrievalStrategyAsvc;
    private final AgentToolCallLogAsvc agentToolCallLogAsvc;
    private final ApiKeyCrypto apiKeyCrypto;
    private final AiEngineClient aiEngineClient;
    private final SystemConfigAsvc systemConfig;
    private final KnowledgeRetrievalAsvc knowledgeRetrievalAsvc;

    /**
     * 构造 AgentRunAsvc 并注入依赖。
     * @param jdbcTemplate 参数 jdbcTemplate
     * @param objectMapper 参数 objectMapper
     * @param retrievalStrategyAsvc 参数 retrievalStrategyAsvc
     * @param agentToolCallLogAsvc 参数 agentToolCallLogAsvc
     * @param apiKeyCrypto 参数 apiKeyCrypto
     * @param aiEngineClient 参数 aiEngineClient
     * @param systemConfig 参数 systemConfig
     * @param knowledgeRetrievalAsvc 参数 knowledgeRetrievalAsvc
     */
    public AgentRunAsvc(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            RetrievalStrategyAsvc retrievalStrategyAsvc,
            AgentToolCallLogAsvc agentToolCallLogAsvc,
            ApiKeyCrypto apiKeyCrypto,
            AiEngineClient aiEngineClient,
            SystemConfigAsvc systemConfig,
            KnowledgeRetrievalAsvc knowledgeRetrievalAsvc) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.retrievalStrategyAsvc = retrievalStrategyAsvc;
        this.agentToolCallLogAsvc = agentToolCallLogAsvc;
        this.apiKeyCrypto = apiKeyCrypto;
        this.aiEngineClient = aiEngineClient;
        this.systemConfig = systemConfig;
        this.knowledgeRetrievalAsvc = knowledgeRetrievalAsvc;
    }

    /**
     * 分页查询。
     * @param status 参数 status
     * @param page 参数 page
     * @param pageSize 参数 pageSize
     * @return 返回结果
     */
    public PageResult<AgentRunVo> page(String status, int page, int pageSize) {
        reclaimStaleRunning();
        int safePage = Math.max(page, 1);
        // 调用 systemConfig.clampPageSize
        int safeSize = systemConfig.clampPageSize(pageSize);
        StringBuilder where = new StringBuilder(" WHERE deleted = 0");
        ArrayList<Object> args = new ArrayList<>();
        // 条件判断
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        // 调用 jdbcTemplate.queryForObject
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_agent_run" + where, Long.class, args.toArray());
        ArrayList<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeSize);
        listArgs.add((safePage - 1) * safeSize);
        // 调用 jdbcTemplate.query
        List<AgentRunVo> records = jdbcTemplate.query(
                "SELECT * FROM t_agent_run"
                        + where
                        + " ORDER BY create_time DESC LIMIT ? OFFSET ?",
                runMapper(false),
                listArgs.toArray());
        return PageResult.of(total == null ? 0L : total, safePage, safeSize, records);
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
                UPDATE t_agent_run
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
                    UPDATE t_agent_run
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
     * 按 ID 查询详情。
     * @param id 参数 id
     * @return 返回结果
     */
    public AgentRunVo getById(String id) {
        // 调用 jdbcTemplate.query
        List<AgentRunVo> rows = jdbcTemplate.query(
                "SELECT * FROM t_agent_run WHERE deleted = 0 AND id = ?", runMapper(true), id);
        // 条件判断
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "agent run not found");
        }
        AgentRunVo vo = rows.get(0);
        vo.setSteps(listSteps(id));
        return vo;
    }

    @FunctionalInterface
    public interface ProgressListener {
        void onStatus(String text);
    }

    /**
     * execute：业务处理。
     * @param req 参数 req
     * @return 返回结果
     */
    public AgentRunVo execute(AgentRunExecuteRequest req) {
        return execute(req, null);
    }

    /**
     * 远程检索/LLM 不包在事务内，避免长时间占用连接。
     * 各步骤写库使用单条 JDBC 自动提交。
     */
    public AgentRunVo execute(AgentRunExecuteRequest req, ProgressListener listener) {
        // 条件判断
        if (req == null || req.getQuestion() == null || req.getQuestion().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question required");
        }
        String question = req.getQuestion().trim();
        // 调用 systemConfig.resolveKnowledgeBaseId
        String kbId = systemConfig.resolveKnowledgeBaseId(req.getKnowledgeBaseId());
        String strategyId = blankToNull(req.getRetrievalStrategyId());
        String appId = blankToNull(req.getAppId());

        String runId = "ar_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        LocalDateTime now = LocalDateTime.now();
        long t0 = System.currentTimeMillis();
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                """
                INSERT INTO t_agent_run (
                  id, create_time, update_time, deleted,
                  app_id, knowledge_base_id, run_type, question, status,
                  step_count, retrieval_rounds, rewrite_rounds
                ) VALUES (?, ?, ?, 0, ?, ?, 'AGENTIC_QA', ?, 'RUNNING', 0, 0, 0)
                """,
                runId,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                appId,
                kbId,
                question);

        int stepIndex = 0;
        int retrievalRounds = 0;
        int rewriteRounds = 0;
        String finalAnswer = null;
        String conclusion = null;
        String sourcesJson = "[]";
        String status = "SUCCESS";
        String errorMessage = null;
        List<Map<String, Object>> sources = List.of();

        // 尝试执行
        try {
            notify(listener, "正在执行 Agentic 链路（LangGraph）…");
            RetrievalStrategyVo strategyVo;
            // 条件判断
            if (strategyId != null) {
                // 调用 retrievalStrategyAsvc.getById
                strategyVo = retrievalStrategyAsvc.getById(strategyId);
            } else {
                // 调用 retrievalStrategyAsvc.resolveForKnowledgeBase
                strategyVo = retrievalStrategyAsvc.resolveForKnowledgeBase(kbId);
            }
            // 调用 knowledgeRetrievalAsvc.toParams
            RetrievalStrategyParams params = knowledgeRetrievalAsvc.toParams(strategyVo);
            ChatModelCfg cfg = loadChatModel();

            AgentGraphRunRequest greq = new AgentGraphRunRequest();
            greq.setQuestion(question);
            greq.setKnowledgeBaseId(kbId);
            greq.setStrategy(params);
            greq.setMaxRounds(3);
            AgentGraphRunRequest.ChatCreds chat = new AgentGraphRunRequest.ChatCreds();
            chat.setModel(cfg.modelName);
            chat.setApiKey(cfg.apiKey);
            chat.setBaseUrl(cfg.baseUrl);
            // 调用 systemConfig.chatTemperature
            chat.setTemperature(cfg.temperature != null ? cfg.temperature : systemConfig.chatTemperature());
            greq.setChat(chat);

            // 调用 aiEngineClient.runAgentic
            AgentGraphRunResult graph = aiEngineClient.runAgentic(greq);
            retrievalRounds = graph.getRetrievalRounds();
            rewriteRounds = graph.getRewriteRounds();
            sources = graph.getSources() == null ? List.of() : graph.getSources();
            // 调用 objectMapper.writeValueAsString
            sourcesJson = objectMapper.writeValueAsString(sources);
            finalAnswer = graph.getAnswer();
            conclusion = graph.getConclusion();

            // 条件判断
            if (graph.getSteps() != null) {
                // 遍历处理
                for (AgentGraphRunResult.Step step : graph.getSteps()) {
                    // 调用 objectMapper.writeValueAsString
                    String inJson = step.getInput() == null ? "{}" : objectMapper.writeValueAsString(step.getInput());
                    // 调用 objectMapper.writeValueAsString
                    String outJson = step.getOutput() == null ? "{}" : objectMapper.writeValueAsString(step.getOutput());
                    String node = step.getNodeName() == null ? "step" : step.getNodeName();
                    String title = step.getTitle() == null ? node : step.getTitle();
                    String st = step.getStatus() == null ? "OK" : step.getStatus();
                    int ms = step.getLatencyMs() == null ? 0 : step.getLatencyMs();
                    insertStep(runId, ++stepIndex, node, title, inJson, outJson, st, ms);
                    // 条件判断
                    if ("retrieve".equals(node)) {
                        notify(listener, "正在检索知识库…");
                        // 调用 agentToolCallLogAsvc.record
                        agentToolCallLogAsvc.record(
                                "search_knowledge",
                                runId,
                                inJson,
                                outJson,
                                st,
                                ms,
                                "FAILED".equalsIgnoreCase(st) ? outJson : null);
                    } else if ("grade".equals(node)) {
                        notify(listener, "正在判断召回的资料相不相关…");
                    } else if ("generate".equals(node)) {
                        notify(listener, "已检索到资料，正在生成答案…");
                    } else if ("check".equals(node)) {
                        notify(listener, "正在校验答案是否有资料支撑…");
                    } else if ("rewrite".equals(node)) {
                        notify(listener, title);
                    }
                }
            }
            // 条件判断
            if (finalAnswer == null || finalAnswer.isBlank()) {
                finalAnswer = "知识库中未找到相关内容";
                // 条件判断
                if (conclusion == null || conclusion.isBlank()) {
                    conclusion = "未找到相关内容";
                }
            }
        } catch (Exception ex) {
            status = "FAILED";
            errorMessage = abbreviate(ex.getMessage(), 500);
            // 条件判断
            if (finalAnswer == null) {
                finalAnswer = "执行失败：" + errorMessage;
            }
            // 条件判断
            if (conclusion == null) {
                conclusion = "执行失败";
            }
        }

        int totalMs = (int) (System.currentTimeMillis() - t0);
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                """
                UPDATE t_agent_run
                SET status = ?, final_answer = ?, error_message = ?, conclusion_label = ?,
                    total_latency_ms = ?, step_count = ?, retrieval_rounds = ?, rewrite_rounds = ?,
                    sources_json = ?, update_time = ?
                WHERE id = ?
                """,
                status,
                finalAnswer,
                errorMessage,
                conclusion,
                totalMs,
                stepIndex,
                retrievalRounds,
                rewriteRounds,
                sourcesJson,
                Timestamp.valueOf(LocalDateTime.now()),
                runId);
        return getById(runId);
    }

    /**
     * notify：业务处理。
     * @param listener 参数 listener
     * @param text 参数 text
     */
    private static void notify(ProgressListener listener, String text) {
        // 条件判断
        if (listener != null) {
            // 尝试执行
            try {
                listener.onStatus(text);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * blankToNull：业务处理。
     * @param s 参数 s
     * @return 返回结果
     */
    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }

    private void reclaimStaleRunning() {
        Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now().minus(Duration.ofHours(2)));
        int n = jdbcTemplate.update(
                """
                UPDATE t_agent_run
                SET status = 'FAILED',
                    error_message = '运行超时未完成（进程中断或卡死），已自动回收',
                    update_time = ?
                WHERE deleted = 0 AND status = 'RUNNING' AND update_time < ?
                """,
                Timestamp.valueOf(LocalDateTime.now()),
                cutoff);
        if (n > 0) {
            log.warn("reclaimed {} stale RUNNING agent run(s)", n);
        }
    }

    /** 直线链路生成：供问答会话复用同一对话模型 */
    public String generateRagAnswer(String question, List<String> contexts) throws Exception {
        return generateAnswer(question, contexts);
    }

    /**
     * generateAnswer：业务处理。
     * @param question 参数 question
     * @param contexts 参数 contexts
     * @return 返回结果
     */
    private String generateAnswer(String question, List<String> contexts) throws Exception {
        ChatModelCfg cfg = loadChatModel();
        StringBuilder ctx = new StringBuilder();
        int i = 1;
        // 遍历处理
        for (String c : contexts) {
            ctx.append('[').append(i++).append("]\n").append(c).append("\n\n");
            // 条件判断
            if (ctx.length() > 12000) break;
        }
        String system = "你是企业知识库助手。请严格根据下面提供的资料回答用户问题。"
                + "资料中没有的内容不要编造，直接回答\"知识库中未找到相关内容\"。";
        String user = "资料：\n" + ctx + "\n用户问题：" + question;
        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setModel(cfg.modelName);
        req.setApiKey(cfg.apiKey);
        req.setBaseUrl(cfg.baseUrl);
        // 调用 systemConfig.chatTemperature
        req.setTemperature(cfg.temperature != null ? cfg.temperature : systemConfig.chatTemperature());
        req.setMessages(List.of(
                new ChatCompletionRequest.ChatMessage("system", system),
                new ChatCompletionRequest.ChatMessage("user", user)));
        // 调用 aiEngineClient.chatCompletion
        ChatCompletionResult result = aiEngineClient.chatCompletion(req);
        // 条件判断
        if (result == null || result.getContent() == null || result.getContent().isBlank()) {
            throw new IllegalStateException("chat empty response");
        }
        return result.getContent().trim();
    }

    /**
     * loadChatModel：业务处理。
     * @return 返回结果
     */
    private ChatModelCfg loadChatModel() {
        // 调用 jdbcTemplate.query
        List<ChatModelCfg> rows = jdbcTemplate.query(
                """
                SELECT model_name, base_url, api_key_enc, temperature
                FROM t_ai_model_config
                WHERE deleted = 0 AND enabled = 1 AND model_type = 'CHAT'
                ORDER BY create_time ASC LIMIT 1
                """,
                (rs, n) -> {
                    ChatModelCfg c = new ChatModelCfg();
                    c.modelName = rs.getString("model_name");
                    c.baseUrl = rs.getString("base_url");
                    c.apiKey = rs.getString("api_key_enc");
                    double t = rs.getDouble("temperature");
                    c.temperature = rs.wasNull() ? null : t;
                    return c;
                });
        // 条件判断
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未配置可用的对话模型");
        }
        ChatModelCfg c = rows.get(0);
        // 条件判断
        if (c.apiKey != null && !c.apiKey.isBlank()) {
            // 调用 apiKeyCrypto.decryptFromStorage
            c.apiKey = apiKeyCrypto.decryptFromStorage(c.apiKey);
        }
        // 条件判断
        if (c.apiKey == null || c.apiKey.isBlank()) {
            String env = System.getenv("DASHSCOPE_CHAT_API_KEY");
            // 条件判断
            if (env == null || env.isBlank()) env = System.getenv("DASHSCOPE_API_KEY");
            c.apiKey = env;
        }
        // 条件判断
        if (c.apiKey == null || c.apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对话模型未配置 API Key");
        }
        // 条件判断
        if (c.baseUrl == null || c.baseUrl.isBlank()) {
            c.baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        return c;
    }

    /**
     * insertStep：业务处理。
     * @param runId 参数 runId
     * @param index 参数 index
     * @param node 参数 node
     * @param title 参数 title
     * @param inputJson 参数 inputJson
     * @param outputJson 参数 outputJson
     * @param status 参数 status
     * @param latencyMs 参数 latencyMs
     */
    private void insertStep(
            String runId,
            int index,
            String node,
            String title,
            String inputJson,
            String outputJson,
            String status,
            int latencyMs) {
        String id = "as_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        // title 存在 output 里不便；用 score_scale 暂存标题不合适。把 title 合并进 node 展示由前端映射。
        jdbcTemplate.update(
                """
                INSERT INTO t_agent_step (
                  id, create_time, agent_run_id, step_index, node_name,
                  input_json, output_json, status, latency_ms, score_scale
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                Timestamp.valueOf(LocalDateTime.now()),
                runId,
                index,
                node,
                inputJson,
                outputJson,
                status,
                latencyMs,
                title);
    }

    /**
     * listSteps：业务处理。
     * @param runId 参数 runId
     * @return 返回结果
     */
    private List<AgentStepVo> listSteps(String runId) {
        // 调用 jdbcTemplate.query
        return jdbcTemplate.query(
                """
                SELECT * FROM t_agent_step
                WHERE agent_run_id = ?
                ORDER BY step_index ASC
                """,
                (rs, n) -> {
                    AgentStepVo vo = new AgentStepVo();
                    vo.setId(rs.getString("id"));
                    vo.setAgentRunId(rs.getString("agent_run_id"));
                    vo.setStepIndex(rs.getInt("step_index"));
                    vo.setNodeName(rs.getString("node_name"));
                    String title = rs.getString("score_scale");
                    vo.setTitle(title != null && !title.isBlank() ? title : defaultTitle(vo.getNodeName()));
                    Object in = rs.getObject("input_json");
                    Object out = rs.getObject("output_json");
                    vo.setInputJson(in == null ? null : String.valueOf(in));
                    vo.setOutputJson(out == null ? null : String.valueOf(out));
                    vo.setStatus(rs.getString("status"));
                    int lat = rs.getInt("latency_ms");
                    vo.setLatencyMs(rs.wasNull() ? null : lat);
                    Timestamp ct = rs.getTimestamp("create_time");
                    vo.setCreateTime(DateTimes.format(ct));
                    return vo;
                },
                runId);
    }

    /**
     * defaultTitle：业务处理。
     * @param node 参数 node
     * @return 返回结果
     */
    private static String defaultTitle(String node) {
        // 条件判断
        if (node == null) return "步骤";
        return switch (node) {
            // 匹配分支
            case "rewrite" -> "准备检索问题";
            // 匹配分支
            case "retrieve" -> "检索知识库";
            // 匹配分支
            case "grade" -> "判断召回是否相关";
            // 匹配分支
            case "generate" -> "生成答案";
            // 匹配分支
            case "check" -> "校验答案是否有资料支撑";
            default -> node;
        };
    }

    /**
     * runMapper：业务处理。
     * @param withSources 参数 withSources
     * @return 返回结果
     */
    private RowMapper<AgentRunVo> runMapper(boolean withSources) {
        return (rs, rowNum) -> {
            AgentRunVo vo = new AgentRunVo();
            vo.setId(rs.getString("id"));
            // 尝试执行
            try {
                vo.setRunType(rs.getString("run_type"));
            } catch (Exception e) {
                vo.setRunType("AGENTIC_QA");
            }
            // 尝试执行
            try {
                vo.setKnowledgeBaseId(rs.getString("knowledge_base_id"));
            } catch (Exception ignored) {
            }
            vo.setQuestion(rs.getString("question"));
            vo.setFinalAnswer(rs.getString("final_answer"));
            // 尝试执行
            try {
                vo.setConclusionLabel(rs.getString("conclusion_label"));
            } catch (Exception ignored) {
            }
            vo.setStatus(rs.getString("status"));
            // 尝试执行
            try {
                vo.setErrorMessage(rs.getString("error_message"));
            } catch (Exception ignored) {
            }
            int lat = rs.getInt("total_latency_ms");
            vo.setTotalLatencyMs(rs.wasNull() ? null : lat);
            vo.setStepCount(rs.getInt("step_count"));
            vo.setRetrievalRounds(rs.getInt("retrieval_rounds"));
            // 尝试执行
            try {
                vo.setRewriteRounds(rs.getInt("rewrite_rounds"));
            } catch (Exception e) {
                vo.setRewriteRounds(0);
            }
            String sj = null;
            // 尝试执行
            try {
                Object o = rs.getObject("sources_json");
                sj = o == null ? null : String.valueOf(o);
            } catch (Exception ignored) {
            }
            vo.setSourcesJson(sj);
            // 条件判断
            if (withSources && sj != null && !sj.isBlank()) {
                // 尝试执行
                try {
                    // 调用 objectMapper.readValue
                    vo.setSources(objectMapper.readValue(
                            // 调用 objectMapper.getTypeFactory
                            sj, objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)));
                } catch (Exception ignored) {
                }
            }
            Timestamp ct = rs.getTimestamp("create_time");
            vo.setCreateTime(DateTimes.format(ct));
            return vo;
        };
    }

    /**
     * abbreviate：业务处理。
     * @param s 参数 s
     * @param max 参数 max
     * @return 返回结果
     */
    private static String abbreviate(String s, int max) {
        // 条件判断
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static class ChatModelCfg {
        String modelName;
        String baseUrl;
        String apiKey;
        Double temperature;
    }
}
