package com.notemind.application.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.notemind.application.service.agent.AgentRunAsvc;
import com.notemind.application.service.agent.AgentToolCallLogAsvc;
import com.notemind.application.service.app.QaAppAsvc;
import com.notemind.application.service.knowledge.KnowledgeRetrievalAsvc;
import com.notemind.application.service.system.SystemConfigAsvc;
import com.notemind.client.ai.RetrievalTestResult;
import com.notemind.common.util.DateTimes;
import com.notemind.interfaces.agent.vo.AgentRunExecuteRequest;
import com.notemind.interfaces.agent.vo.AgentRunVo;
import com.notemind.interfaces.app.vo.QaAppVo;
import com.notemind.interfaces.chat.vo.ChatFeedbackRequest;
import com.notemind.interfaces.chat.vo.ChatMessageVo;
import com.notemind.interfaces.chat.vo.ChatSessionCreateRequest;
import com.notemind.interfaces.chat.vo.ChatSessionVo;
import com.notemind.interfaces.chat.vo.ChatStreamRequest;
import com.notemind.interfaces.knowledge.vo.KnowledgeRetrievalTestRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;

/**
 * 会话问答应用服务：会话管理、SSE 问答、引用来源与赞踩。
 */
@Service
public class ChatAsvc {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final QaAppAsvc qaAppAsvc;
    private final AgentRunAsvc agentRunAsvc;
    private final KnowledgeRetrievalAsvc knowledgeRetrievalAsvc;
    private final AgentToolCallLogAsvc agentToolCallLogAsvc;
    private final SystemConfigAsvc systemConfig;
    private final IntentRouteAsvc intentRouteAsvc;
    private final SemanticCacheAsvc semanticCacheAsvc;

    /**
     * 构造 ChatAsvc 并注入依赖。
     */
    public ChatAsvc(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            QaAppAsvc qaAppAsvc,
            AgentRunAsvc agentRunAsvc,
            KnowledgeRetrievalAsvc knowledgeRetrievalAsvc,
            AgentToolCallLogAsvc agentToolCallLogAsvc,
            SystemConfigAsvc systemConfig,
            IntentRouteAsvc intentRouteAsvc,
            SemanticCacheAsvc semanticCacheAsvc) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.qaAppAsvc = qaAppAsvc;
        this.agentRunAsvc = agentRunAsvc;
        this.knowledgeRetrievalAsvc = knowledgeRetrievalAsvc;
        this.agentToolCallLogAsvc = agentToolCallLogAsvc;
        this.systemConfig = systemConfig;
        this.intentRouteAsvc = intentRouteAsvc;
        this.semanticCacheAsvc = semanticCacheAsvc;
    }

    /**
     * createSession：业务处理。
     * @param req 参数 req
     * @return 返回结果
     */
    @Transactional
    public ChatSessionVo createSession(ChatSessionCreateRequest req) {
        // 条件判断
        if (req == null || req.getAppId() == null || req.getAppId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "appId required");
        }
        // 调用 qaAppAsvc.getById
        QaAppVo app = qaAppAsvc.getById(req.getAppId().trim());
        // 条件判断
        if (app.getEnabled() != null && app.getEnabled() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "应用已停用");
        }
        String source = req.getClientSource() == null || req.getClientSource().isBlank()
                ? "ADMIN"
                : req.getClientSource().trim().toUpperCase(Locale.ROOT);
        // 条件判断
        if (!source.equals("ADMIN") && !source.equals("WEB")) {
            source = "ADMIN";
        }
        String userId = currentUserId();
        String id = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                """
                INSERT INTO t_chat_session (
                  id, create_time, update_time, deleted, user_id, app_id, title, client_source
                ) VALUES (?, ?, ?, 0, ?, ?, ?, ?)
                """,
                id,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                userId,
                app.getId(),
                "新对话",
                source);
        ChatSessionVo vo = new ChatSessionVo();
        vo.setId(id);
        vo.setAppId(app.getId());
        vo.setTitle("新对话");
        vo.setCreateTime(DateTimes.format(Timestamp.valueOf(now)));
        vo.setUpdateTime(vo.getCreateTime());
        return vo;
    }

    /**
     * listSessions：业务处理。
     * @param appId 参数 appId
     * @return 返回结果
     */
    public List<ChatSessionVo> listSessions(String appId) {
        // 条件判断
        if (appId == null || appId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "appId required");
        }
        String userId = currentUserId();
        // 调用 systemConfig.sessionListLimit
        int limit = systemConfig.sessionListLimit();
        // 调用 jdbcTemplate.query
        return jdbcTemplate.query(
                """
                SELECT id, app_id, title, create_time, update_time
                FROM t_chat_session
                WHERE deleted = 0 AND app_id = ? AND user_id = ?
                ORDER BY update_time DESC
                LIMIT ?
                """,
                (rs, n) -> {
                    ChatSessionVo vo = new ChatSessionVo();
                    vo.setId(rs.getString("id"));
                    vo.setAppId(rs.getString("app_id"));
                    vo.setTitle(rs.getString("title"));
                    vo.setCreateTime(DateTimes.format(rs.getTimestamp("create_time")));
                    vo.setUpdateTime(DateTimes.format(rs.getTimestamp("update_time")));
                    return vo;
                },
                appId.trim(),
                userId,
                limit);
    }

    /**
     * currentUserId：业务处理。
     * @return 返回结果
     */
    private String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
            String p = String.valueOf(auth.getPrincipal());
            if (!p.isBlank() && !"anonymousUser".equals(p)) {
                return p;
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
    }

    /**
     * listMessages：业务处理。
     * @param sessionId 参数 sessionId
     * @return 返回结果
     */
    public List<ChatMessageVo> listMessages(String sessionId) {
        ensureOwnedSession(sessionId);
        // 调用 systemConfig.messageListLimit
        int limit = systemConfig.messageListLimit();
        // 调用 jdbcTemplate.query
        return jdbcTemplate.query(
                """
                SELECT * FROM t_chat_message
                WHERE deleted = 0 AND session_id = ?
                ORDER BY create_time ASC
                LIMIT ?
                """,
                (rs, n) -> {
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
                    // 条件判断
                    if (sj != null) {
                        // 尝试执行
                        try {
                            // 调用 objectMapper.readValue
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
                sessionId,
                limit);
    }

    /**
     * feedback：业务处理。
     * @param messageId 参数 messageId
     * @param req 参数 req
     */
    @Transactional
    public void feedback(String messageId, ChatFeedbackRequest req) {
        String fb = req == null || req.getFeedback() == null ? "" : req.getFeedback().trim().toUpperCase(Locale.ROOT);
        // 条件判断
        if (!fb.equals("LIKE") && !fb.equals("DISLIKE") && !fb.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "feedback must be LIKE/DISLIKE");
        }
        String userId = currentUserId();
        // 调用 jdbcTemplate.update
        int n = jdbcTemplate.update(
                """
                UPDATE t_chat_message m
                INNER JOIN t_chat_session s ON s.id = m.session_id AND s.deleted = 0
                SET m.feedback = ?, m.feedback_reason = ?, m.update_time = ?
                WHERE m.id = ? AND m.deleted = 0 AND m.role = 'ASSISTANT' AND s.user_id = ?
                """,
                fb.isEmpty() ? null : fb,
                req == null || req.getReason() == null || req.getReason().isBlank()
                        ? null
                        : req.getReason().trim(),
                Timestamp.valueOf(LocalDateTime.now()),
                messageId,
                userId);
        // 条件判断
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "message not found");
        }
    }

    /**
     * stream：业务处理。
     * @param req 参数 req
     * @return 返回结果
     */
    public SseEmitter stream(ChatStreamRequest req) {
        // 条件判断
        if (req == null || req.getAppId() == null || req.getAppId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "appId required");
        }
        // 条件判断
        if (req.getQuestion() == null || req.getQuestion().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question required");
        }
        // 调用 qaAppAsvc.getById
        QaAppVo app = qaAppAsvc.getById(req.getAppId().trim());
        if (app.getEnabled() != null && app.getEnabled() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "应用已停用");
        }
        String sessionId = req.getSessionId();
        // 条件判断
        if (sessionId == null || sessionId.isBlank()) {
            ChatSessionCreateRequest create = new ChatSessionCreateRequest();
            create.setAppId(app.getId());
            create.setClientSource(req.getClientSource());
            sessionId = createSession(create).getId();
        } else {
            ensureOwnedSession(sessionId, app.getId());
        }
        final String sid = sessionId;
        final String question = req.getQuestion().trim();
        // 工作线程需自行持有认证上下文（SSE 在独立线程执行）
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        SseEmitter emitter = new SseEmitter(600_000L);
        final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        Thread t = new Thread(() -> {
            // 尝试执行
            try {
                SecurityContextHolder.getContext().setAuthentication(auth);
                send(emitter, "session", Map.of("sessionId", sid, "appId", app.getId()));
                // 条件判断
                if (cancelled.get()) {
                    return;
                }

                String userMsgId = insertMessage(sid, "USER", question, null, null);
                touchSessionTitle(sid, question);
                send(emitter, "user_message", Map.of("id", userMsgId, "content", question));

                long tAll = System.currentTimeMillis();
                // Phase B：语义缓存短路
                SemanticCacheAsvc.Hit cacheHit =
                        semanticCacheAsvc.lookup(
                                currentUserId(), app.getId(), app.getKnowledgeBaseId(), question);
                if (cacheHit.hit()) {
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("cacheHit", true);
                    meta.put("cacheSimilarity", cacheHit.similarity());
                    meta.put("cacheMode", cacheHit.mode());
                    meta.put("intentLevel", "CACHE");
                    meta.put("pipeline", "cache");
                    meta.put("generatorModel", "cache");
                    meta.put("routerModel", "n/a");
                    send(emitter, "meta", meta);
                    send(emitter, "status", Map.of("text", "语义缓存命中，跳过模型调用"));
                    if (cacheHit.sources() != null && !cacheHit.sources().isEmpty()) {
                        send(emitter, "sources", Map.of("sources", cacheHit.sources(), "count", cacheHit.sources().size()));
                    }
                    emitAnswerChunks(emitter, cacheHit.answer(), cancelled);
                    String sourcesJson = objectMapper.writeValueAsString(cacheHit.sources());
                    String msgId = insertMessage(sid, "ASSISTANT", cacheHit.answer(), sourcesJson, null);
                    Map<String, Object> fin = new LinkedHashMap<>();
                    fin.put("messageId", msgId);
                    fin.put("sessionId", sid);
                    fin.put("answer", cacheHit.answer());
                    fin.put("sources", cacheHit.sources());
                    fin.put("conclusionLabel", "缓存命中");
                    fin.put("enableAgentic", 0);
                    fin.put("elapsedMs", System.currentTimeMillis() - tAll);
                    send(emitter, "final", fin);
                    send(emitter, "done", Map.of("ok", true));
                    if (!cancelled.get()) {
                        emitter.complete();
                    }
                    return;
                }

                IntentRouteAsvc.Result intent = intentRouteAsvc.classify(question);
                Map<String, Object> meta = new LinkedHashMap<>(intentRouteAsvc.toMeta(intent));
                meta.put("cacheHit", false);
                meta.put("routerModel", "rule-v1");
                meta.put("generatorModel", "app-chat-model");
                boolean appAgentic = app.getEnableAgentic() != null && app.getEnableAgentic() == 1;
                boolean agentic;
                if (intent.level() == IntentRouteAsvc.Level.L3_REJECT) {
                    agentic = false;
                    meta.put("pipeline", "reject");
                } else if (intent.level() == IntentRouteAsvc.Level.L1_FACT) {
                    agentic = false;
                    meta.put("pipeline", "linear");
                } else {
                    agentic = appAgentic;
                    meta.put("pipeline", agentic ? "agentic" : "linear");
                }
                send(emitter, "meta", meta);

                if (cancelled.get()) {
                    return;
                }
                if (intent.level() == IntentRouteAsvc.Level.L3_REJECT) {
                    String answer = app.getFallbackReply() != null && !app.getFallbackReply().isBlank()
                            ? app.getFallbackReply()
                            : "该问题不在知识库问答范围内，请换一个业务相关问题。";
                    send(emitter, "status", Map.of("text", "意图判定为拒答/闲聊"));
                    emitAnswerChunks(emitter, answer, cancelled);
                    String msgId = insertMessage(sid, "ASSISTANT", answer, "[]", null);
                    Map<String, Object> fin = new LinkedHashMap<>();
                    fin.put("messageId", msgId);
                    fin.put("sessionId", sid);
                    fin.put("answer", answer);
                    fin.put("sources", List.of());
                    fin.put("conclusionLabel", "已拒答");
                    fin.put("enableAgentic", 0);
                    send(emitter, "final", fin);
                    send(emitter, "done", Map.of("ok", true));
                } else if (agentic) {
                    runAgentic(emitter, app, sid, question, cancelled);
                } else {
                    runLinear(emitter, app, sid, question, cancelled);
                }
                // 条件判断
                if (!cancelled.get()) {
                    emitter.complete();
                }
            } catch (CancellationException cancelledEx) {
                // 客户端断开：静默结束
            } catch (Exception ex) {
                // 条件判断
                if (cancelled.get()) {
                    return;
                }
                // 尝试执行
                try {
                    send(emitter, "error", Map.of("message", abbreviate(ex.getMessage(), 300)));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(ex);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }, "chat-sse-" + sid);
        t.setDaemon(true);
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> {
            cancelled.set(true);
            t.interrupt();
        });
        emitter.onError((ex) -> {
            cancelled.set(true);
            t.interrupt();
        });
        t.start();
        return emitter;
    }

    /**
     * runLinear：业务处理。
     * @param emitter 参数 emitter
     * @param app 参数 app
     * @param sessionId 参数 sessionId
     * @param question 参数 question
     * @param cancelled 参数 cancelled
     */
    private void runLinear(
            SseEmitter emitter,
            QaAppVo app,
            String sessionId,
            String question,
            java.util.concurrent.atomic.AtomicBoolean cancelled)
            throws Exception {
        ensureNotCancelled(cancelled);
        send(emitter, "status", Map.of("text", "正在检索知识库…"));
        KnowledgeRetrievalTestRequest rreq = new KnowledgeRetrievalTestRequest();
        rreq.setKnowledgeBaseId(app.getKnowledgeBaseId());
        rreq.setQuestion(question);
        // 条件判断
        if (app.getRetrievalStrategyId() != null && !app.getRetrievalStrategyId().isBlank()) {
            rreq.setRetrievalStrategyId(app.getRetrievalStrategyId());
        }
        // 调用 objectMapper.createObjectNode
        ObjectNode inTool = objectMapper.createObjectNode();
        inTool.put("query", question);
        inTool.put("kb_id", app.getKnowledgeBaseId());
        inTool.put("session_id", sessionId);
        // 条件判断
        if (app.getRetrievalStrategyId() != null && !app.getRetrievalStrategyId().isBlank()) {
            inTool.put("retrieval_strategy_id", app.getRetrievalStrategyId());
        }
        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> sources;
        // 尝试执行
        try {
            ensureNotCancelled(cancelled);
            // 调用 knowledgeRetrievalAsvc.test
            RetrievalTestResult retrieval = knowledgeRetrievalAsvc.test(rreq);
            ensureNotCancelled(cancelled);
            sources = retrieval.getSources() == null ? List.of() : retrieval.getSources();
            // 调用 objectMapper.createObjectNode
            ObjectNode outTool = objectMapper.createObjectNode();
            outTool.put("hits", sources.size());
            outTool.put("score_scale", retrieval.getScoreScale());
            // 调用 agentToolCallLogAsvc.record
            agentToolCallLogAsvc.record(
                    "search_knowledge",
                    null,
                    inTool.toString(),
                    outTool.toString(),
                    "OK",
                    (int) (System.currentTimeMillis() - t0),
                    null);
        } catch (CancellationException cex) {
            throw cex;
        } catch (Exception ex) {
            // 调用 objectMapper.createObjectNode
            ObjectNode outFail = objectMapper.createObjectNode();
            outFail.put("hits", 0);
            outFail.put("error", abbreviate(ex.getMessage(), 200));
            // 调用 agentToolCallLogAsvc.record
            agentToolCallLogAsvc.record(
                    "search_knowledge",
                    null,
                    inTool.toString(),
                    outFail.toString(),
                    "FAILED",
                    (int) (System.currentTimeMillis() - t0),
                    ex.getMessage());
            throw ex;
        }
        ensureNotCancelled(cancelled);
        send(emitter, "sources", Map.of("sources", sources, "count", sources.size()));

        String answer;
        String conclusion;
        // 条件判断
        if (sources.isEmpty()) {
            answer = app.getFallbackReply() != null && !app.getFallbackReply().isBlank()
                    ? app.getFallbackReply()
                    : "知识库中未找到相关内容";
            conclusion = "未找到相关内容";
            send(emitter, "status", Map.of("text", "未检索到相关资料"));
        } else {
            send(emitter, "status", Map.of("text", "已检索到资料，正在生成答案…"));
            List<String> contexts = new ArrayList<>();
            // 遍历处理
            for (Map<String, Object> src : sources) {
                Object c = src.get("content");
                // 条件判断
                if (c != null && !String.valueOf(c).isBlank()) {
                    contexts.add(String.valueOf(c));
                }
            }
            ensureNotCancelled(cancelled);
            // 调用 agentRunAsvc.generateRagAnswer
            answer = agentRunAsvc.generateRagAnswer(question, contexts);
            conclusion = "正常回答";
        }

        ensureNotCancelled(cancelled);
        emitAnswerChunks(emitter, answer, cancelled);
        ensureNotCancelled(cancelled);
        // 调用 objectMapper.writeValueAsString
        String sourcesJson = objectMapper.writeValueAsString(sources);
        String msgId = insertMessage(sessionId, "ASSISTANT", answer, sourcesJson, null);
        Map<String, Object> fin = new LinkedHashMap<>();
        fin.put("messageId", msgId);
        fin.put("sessionId", sessionId);
        fin.put("answer", answer);
        fin.put("sources", sources);
        fin.put("conclusionLabel", conclusion);
        fin.put("enableAgentic", 0);
        send(emitter, "final", fin);
        send(emitter, "done", Map.of("ok", true));
        // 仅成功有据回答写入语义缓存，避免兜底/空召回污染
        if (!sources.isEmpty() && !"未找到相关内容".equals(conclusion)) {
            semanticCacheAsvc.store(
                    currentUserId(), app.getId(), app.getKnowledgeBaseId(), question, answer, sources);
        }
    }

    /**
     * runAgentic：业务处理。
     * @param emitter 参数 emitter
     * @param app 参数 app
     * @param sessionId 参数 sessionId
     * @param question 参数 question
     * @param cancelled 参数 cancelled
     */
    private void runAgentic(
            SseEmitter emitter,
            QaAppVo app,
            String sessionId,
            String question,
            java.util.concurrent.atomic.AtomicBoolean cancelled)
            throws Exception {
        ensureNotCancelled(cancelled);
        AgentRunExecuteRequest areq = new AgentRunExecuteRequest();
        areq.setAppId(app.getId());
        areq.setKnowledgeBaseId(app.getKnowledgeBaseId());
        areq.setRetrievalStrategyId(app.getRetrievalStrategyId());
        areq.setQuestion(question);

        // 调用 agentRunAsvc.execute
        AgentRunVo run = agentRunAsvc.execute(areq, text -> {
            ensureNotCancelled(cancelled);
            // 尝试执行
            try {
                send(emitter, "status", Map.of("text", text));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        ensureNotCancelled(cancelled);

        List<Map<String, Object>> sources = run.getSources() == null ? List.of() : run.getSources();
        // 条件判断
        if (!sources.isEmpty()) {
            send(emitter, "sources", Map.of("sources", sources, "count", sources.size()));
        }

        String answer = run.getFinalAnswer();
        // 条件判断
        if ((answer == null || answer.isBlank())
                && "未找到相关内容".equals(run.getConclusionLabel())
                && app.getFallbackReply() != null
                && !app.getFallbackReply().isBlank()) {
            answer = app.getFallbackReply();
        }
        // 条件判断
        if (answer == null) answer = "";

        emitAnswerChunks(emitter, answer, cancelled);
        ensureNotCancelled(cancelled);
        // 调用 objectMapper.writeValueAsString
        String sourcesJson = objectMapper.writeValueAsString(sources);
        String msgId = insertMessage(sessionId, "ASSISTANT", answer, sourcesJson, run.getId());

        Map<String, Object> fin = new LinkedHashMap<>();
        fin.put("messageId", msgId);
        fin.put("sessionId", sessionId);
        fin.put("answer", answer);
        fin.put("sources", sources);
        fin.put("conclusionLabel", run.getConclusionLabel());
        fin.put("enableAgentic", 1);
        fin.put("agentRunId", run.getId());
        fin.put("stepCount", run.getStepCount());
        fin.put("retrievalRounds", run.getRetrievalRounds());
        fin.put("rewriteRounds", run.getRewriteRounds());
        fin.put("totalLatencyMs", run.getTotalLatencyMs());
        fin.put("status", run.getStatus());
        send(emitter, "final", fin);
        send(emitter, "done", Map.of("ok", true));
        if ("SUCCESS".equalsIgnoreCase(run.getStatus())
                && answer != null
                && !answer.isBlank()
                && !sources.isEmpty()
                && !"未找到相关内容".equals(run.getConclusionLabel())) {
            semanticCacheAsvc.store(
                    currentUserId(), app.getId(), app.getKnowledgeBaseId(), question, answer, sources);
        }
    }

    private void emitAnswerChunks(
            SseEmitter emitter, String answer, java.util.concurrent.atomic.AtomicBoolean cancelled)
            throws IOException {
        // 条件判断
        if (answer == null || answer.isEmpty()) {
            return;
        }
        int i = 0;
        // 循环处理
        while (i < answer.length()) {
            ensureNotCancelled(cancelled);
            int end = Math.min(i + 24, answer.length());
            send(emitter, "delta", Map.of("content", answer.substring(i, end)));
            i = end;
            // 尝试执行
            try {
                Thread.sleep(12);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancellationException("sse interrupted");
            }
        }
    }

    /**
     * ensureNotCancelled：业务处理。
     * @param cancelled 参数 cancelled
     */
    private static void ensureNotCancelled(java.util.concurrent.atomic.AtomicBoolean cancelled) {
        // 条件判断
        if (cancelled != null && cancelled.get()) {
            throw new CancellationException("sse cancelled");
        }
        // 条件判断
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("sse interrupted");
        }
    }

    /**
     * touchSessionTitle：业务处理。
     * @param sessionId 参数 sessionId
     * @param question 参数 question
     */
    private void touchSessionTitle(String sessionId, String question) {
        String title = question == null ? "" : question.trim();
        // 条件判断
        if (title.length() > 80) {
            title = title.substring(0, 80) + "…";
        }
        // 调用 jdbcTemplate.queryForObject
        Long cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_chat_message WHERE deleted = 0 AND session_id = ? AND role = 'USER'",
                Long.class,
                sessionId);
        // 条件判断
        if (cnt != null && cnt <= 1) {
            // 调用 jdbcTemplate.update
            jdbcTemplate.update(
                    "UPDATE t_chat_session SET title = ?, update_time = ? WHERE id = ? AND deleted = 0",
                    title,
                    Timestamp.valueOf(LocalDateTime.now()),
                    sessionId);
        }
    }

    /**
     * insertMessage：业务处理。
     * @param sessionId 参数 sessionId
     * @param role 参数 role
     * @param content 参数 content
     * @param sourcesJson 参数 sourcesJson
     * @param agentRunId 参数 agentRunId
     * @return 返回结果
     */
    private String insertMessage(
            String sessionId, String role, String content, String sourcesJson, String agentRunId) {
        String id = "cm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        LocalDateTime now = LocalDateTime.now();
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                """
                INSERT INTO t_chat_message (
                  id, create_time, update_time, deleted,
                  session_id, role, content, sources_json, agent_run_id
                ) VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?)
                """,
                id,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                sessionId,
                role,
                content,
                sourcesJson,
                agentRunId);
        return id;
    }

    /** 校验会话存在且归属当前登录用户，防止跨用户读写。 */
    private void ensureOwnedSession(String sessionId) {
        ensureOwnedSession(sessionId, null);
    }

    /** 校验会话归属；若传入 appId 则必须与会话绑定应用一致。 */
    private void ensureOwnedSession(String sessionId, String appId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId required");
        }
        String userId = currentUserId();
        Long n;
        if (appId != null && !appId.isBlank()) {
            n = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(1) FROM t_chat_session
                    WHERE deleted = 0 AND id = ? AND user_id = ? AND app_id = ?
                    """,
                    Long.class,
                    sessionId.trim(),
                    userId,
                    appId.trim());
        } else {
            n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM t_chat_session WHERE deleted = 0 AND id = ? AND user_id = ?",
                    Long.class,
                    sessionId.trim(),
                    userId);
        }
        if (n == null || n == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found");
        }
    }

    /**
     * send：业务处理。
     * @param emitter 参数 emitter
     * @param event 参数 event
     * @param data 参数 data
     */
    private void send(SseEmitter emitter, String event, Object data) throws IOException {
        // 调用 objectMapper.writeValueAsString
        emitter.send(SseEmitter.event().name(event).data(objectMapper.writeValueAsString(data)));
    }

    /**
     * abbreviate：业务处理。
     * @param s 参数 s
     * @param max 参数 max
     * @return 返回结果
     */
    private static String abbreviate(String s, int max) {
        // 条件判断
        if (s == null) return "error";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
