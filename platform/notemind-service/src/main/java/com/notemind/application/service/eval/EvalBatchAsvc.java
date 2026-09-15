package com.notemind.application.service.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.notemind.application.service.agent.AgentRunAsvc;
import com.notemind.application.service.knowledge.KnowledgeRetrievalAsvc;
import com.notemind.application.service.knowledge.RetrievalStrategyAsvc;
import com.notemind.client.ai.RetrievalTestResult;
import com.notemind.client.ai.AiEngineClient;
import com.notemind.client.ai.ChatCompletionRequest;
import com.notemind.client.ai.ChatCompletionResult;
import com.notemind.common.result.PageResult;
import com.notemind.common.util.DateTimes;
import com.notemind.interfaces.eval.vo.EvalBatchStartRequest;
import com.notemind.interfaces.eval.vo.EvalCaseVo;
import com.notemind.interfaces.eval.vo.EvalDatasetVo;
import com.notemind.interfaces.eval.vo.EvalReportVo;
import com.notemind.interfaces.knowledge.vo.KnowledgeRetrievalTestRequest;
import com.notemind.interfaces.knowledge.vo.RetrievalStrategyVo;
import com.notemind.infrastructure.security.ApiKeyCrypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 批量评测应用服务：开跑任务、进度轮询与四维报告。
 */
@Service
public class EvalBatchAsvc {

    private static final Logger log = LoggerFactory.getLogger(EvalBatchAsvc.class);

    /** 达标阈值（与报告卡展示一致） */
    public static final double TH_RECALL = 0.7;
    public static final double TH_PRECISION = 0.6;
    public static final double TH_FAITHFULNESS = 0.8;
    public static final double TH_RELEVANCY = 0.7;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final EvalAsvc evalAsvc;
    private final KnowledgeRetrievalAsvc knowledgeRetrievalAsvc;
    private final RetrievalStrategyAsvc retrievalStrategyAsvc;
    private final AgentRunAsvc agentRunAsvc;
    private final ApiKeyCrypto apiKeyCrypto;
    private final AiEngineClient aiEngineClient;
    private final ExecutorService executor = Executors.newFixedThreadPool(1, r -> {
        Thread t = new Thread(r, "eval-batch-worker");
        t.setDaemon(true);
        return t;
    });
    private final AtomicLong taskNoSeq = new AtomicLong(System.currentTimeMillis() % 100000);
    /** reportId → 取消标志（软删时置位，worker 停写） */
    private final ConcurrentHashMap<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();
    /** 开跑互斥：避免 check→INSERT 竞态出现多个 RUNNING */
    private final Object startLock = new Object();

    /**
     * 构造 EvalBatchAsvc 并注入依赖。
     * @param jdbcTemplate 参数 jdbcTemplate
     * @param objectMapper 参数 objectMapper
     * @param evalAsvc 参数 evalAsvc
     * @param knowledgeRetrievalAsvc 参数 knowledgeRetrievalAsvc
     * @param retrievalStrategyAsvc 参数 retrievalStrategyAsvc
     * @param agentRunAsvc 参数 agentRunAsvc
     * @param apiKeyCrypto 参数 apiKeyCrypto
     * @param aiEngineClient 参数 aiEngineClient
     */
    public EvalBatchAsvc(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            EvalAsvc evalAsvc,
            KnowledgeRetrievalAsvc knowledgeRetrievalAsvc,
            RetrievalStrategyAsvc retrievalStrategyAsvc,
            AgentRunAsvc agentRunAsvc,
            ApiKeyCrypto apiKeyCrypto,
            AiEngineClient aiEngineClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.evalAsvc = evalAsvc;
        this.knowledgeRetrievalAsvc = knowledgeRetrievalAsvc;
        this.retrievalStrategyAsvc = retrievalStrategyAsvc;
        this.agentRunAsvc = agentRunAsvc;
        this.apiKeyCrypto = apiKeyCrypto;
        this.aiEngineClient = aiEngineClient;
    }

    /**
     * pageReports：业务处理。
     * @param datasetId 参数 datasetId
     * @param status 参数 status
     * @param page 参数 page
     * @param pageSize 参数 pageSize
     * @return 返回结果
     */
    public PageResult<EvalReportVo> pageReports(String datasetId, String status, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        StringBuilder where = new StringBuilder(" WHERE r.deleted = 0");
        ArrayList<Object> args = new ArrayList<>();
        // 条件判断
        if (datasetId != null && !datasetId.isBlank()) {
            where.append(" AND r.dataset_id = ?");
            args.add(datasetId.trim());
        }
        // 条件判断
        if (status != null && !status.isBlank()) {
            where.append(" AND r.status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        // 调用 jdbcTemplate.queryForObject
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_eval_report r" + where, Long.class, args.toArray());
        ArrayList<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeSize);
        listArgs.add((safePage - 1) * safeSize);
        // 调用 jdbcTemplate.query
        List<EvalReportVo> records = jdbcTemplate.query(
                """
                SELECT r.*,
                  rs.enable_vector, rs.enable_bm25, rs.enable_rerank,
                  rs.enable_rewrite, rs.enable_parent_fill, rs.rewrite_mode
                FROM t_eval_report r
                LEFT JOIN t_retrieval_strategy rs ON rs.id = r.retrieval_strategy_id AND rs.deleted = 0
                """
                        + where
                        + " ORDER BY r.create_time DESC LIMIT ? OFFSET ?",
                reportMapper(false),
                listArgs.toArray());
        return PageResult.of(total == null ? 0L : total, safePage, safeSize, records);
    }

    /**
     * getReport：业务处理。
     * @param id 参数 id
     * @return 返回结果
     */
    public EvalReportVo getReport(String id) {
        // 调用 jdbcTemplate.query
        List<EvalReportVo> rows = jdbcTemplate.query(
                """
                SELECT r.*,
                  rs.enable_vector, rs.enable_bm25, rs.enable_rerank,
                  rs.enable_rewrite, rs.enable_parent_fill, rs.rewrite_mode
                FROM t_eval_report r
                LEFT JOIN t_retrieval_strategy rs ON rs.id = r.retrieval_strategy_id AND rs.deleted = 0
                WHERE r.deleted = 0 AND r.id = ?
                """,
                reportMapper(true),
                id);
        // 条件判断
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评测报告不存在");
        }
        return rows.get(0);
    }

    /**
     * deleteReport：业务处理。
     * @param id 参数 id
     */
    @Transactional
    public void deleteReport(String id) {
        getReport(id);
        requestCancel(id);
        LocalDateTime now = LocalDateTime.now();
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                "UPDATE t_eval_report SET deleted = 1, delete_time = ?, update_time = ? WHERE id = ? AND deleted = 0",
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                id);
    }

    /**
     * batchDeleteReports：业务处理。
     * @param ids 参数 ids
     * @return 返回结果
     */
    @Transactional
    public int batchDeleteReports(List<String> ids) {
        // 条件判断
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<String> distinct = ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        // 条件判断
        if (distinct.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        Timestamp ts = Timestamp.valueOf(now);
        int deleted = 0;
        // 遍历处理
        for (String id : distinct) {
            requestCancel(id);
            // 调用 jdbcTemplate.update
            deleted += jdbcTemplate.update(
                    "UPDATE t_eval_report SET deleted = 1, delete_time = ?, update_time = ? WHERE id = ? AND deleted = 0",
                    ts,
                    ts,
                    id);
        }
        return deleted;
    }

    /**
     * requestCancel：业务处理。
     * @param reportId 参数 reportId
     */
    private void requestCancel(String reportId) {
        // 条件判断
        if (reportId == null || reportId.isBlank()) return;
        cancelFlags.computeIfAbsent(reportId, k -> new AtomicBoolean(false)).set(true);
    }

    /**
     * isCancelled：业务处理。
     * @param reportId 参数 reportId
     * @return 返回结果
     */
    private boolean isCancelled(String reportId) {
        AtomicBoolean flag = cancelFlags.get(reportId);
        // 条件判断
        if (flag != null && flag.get()) {
            return true;
        }
        // 尝试执行
        try {
            // 调用 jdbcTemplate.queryForObject
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM t_eval_report WHERE id = ? AND deleted = 0 AND status = 'RUNNING'",
                    Integer.class,
                    reportId);
            return n == null || n == 0;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * start：业务处理。
     * @param req 参数 req
     * @return 返回结果
     */
    public EvalReportVo start(EvalBatchStartRequest req) {
        // 条件判断
        if (req == null || req.getDatasetId() == null || req.getDatasetId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择评测集");
        }
        // 调用 evalAsvc.getDataset
        EvalDatasetVo ds = evalAsvc.getDataset(req.getDatasetId().trim());
        // 条件判断
        if (ds.getKnowledgeBaseId() == null || ds.getKnowledgeBaseId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评测集未关联知识库");
        }
        List<EvalCaseVo> cases = loadEvalCases(ds.getId());
        // 条件判断
        if (cases.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评测集没有可参与评测的用例");
        }

        RetrievalStrategyVo strategy;
        // 条件判断
        if (req.getRetrievalStrategyId() != null && !req.getRetrievalStrategyId().isBlank()) {
            // 调用 retrievalStrategyAsvc.getById
            strategy = retrievalStrategyAsvc.getById(req.getRetrievalStrategyId().trim());
        } else {
            // 调用 retrievalStrategyAsvc.resolveForKnowledgeBase
            strategy = retrievalStrategyAsvc.resolveForKnowledgeBase(ds.getKnowledgeBaseId());
        }

        Integer running;
        String id;
        long taskNo;
        LocalDateTime now = LocalDateTime.now();
        synchronized (startLock) {
            // 调用 jdbcTemplate.queryForObject
            running = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM t_eval_report WHERE deleted = 0 AND status = 'RUNNING'",
                    Integer.class);
            // 条件判断
            if (running != null && running > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "已有评测任务在运行，请等待完成或删除后再开");
            }
            id = "er_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            taskNo = nextTaskNo();
            // 调用 jdbcTemplate.update
            jdbcTemplate.update(
                    """
                    INSERT INTO t_eval_report (
                      id, task_no, create_time, update_time, deleted,
                      dataset_id, dataset_name, retrieval_strategy_id, strategy_name,
                      status, case_count, done_count
                    ) VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?, 'RUNNING', ?, 0)
                    """,
                    id,
                    taskNo,
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now),
                    ds.getId(),
                    ds.getName(),
                    strategy.getId(),
                    strategy.getName(),
                    cases.size());
        }

        String reportId = id;
        String kbId = ds.getKnowledgeBaseId();
        String strategyId = strategy.getId();
        cancelFlags.put(reportId, new AtomicBoolean(false));
        executor.submit(() -> {
            // 尝试执行
            try {
                runBatch(reportId, kbId, strategyId, cases);
            } finally {
                cancelFlags.remove(reportId);
            }
        });
        return getReport(id);
    }

    /**
     * runBatch：业务处理。
     * @param reportId 参数 reportId
     * @param kbId 参数 kbId
     * @param strategyId 参数 strategyId
     * @param cases 参数 cases
     */
    private void runBatch(String reportId, String kbId, String strategyId, List<EvalCaseVo> cases) {
        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> caseResults = new ArrayList<>();
        double sumRecall = 0, sumPrecision = 0, sumFaith = 0, sumRel = 0;
        int scored = 0;
        // 尝试执行
        try {
            // 遍历处理
            for (int i = 0; i < cases.size(); i++) {
                // 条件判断
                if (isCancelled(reportId)) {
                    log.info("eval batch cancelled reportId={}", reportId);
                    return;
                }
                EvalCaseVo c = cases.get(i);
                Map<String, Object> row = evaluateOne(c, kbId, strategyId);
                caseResults.add(row);
                Object fr = row.get("failReason");
                // 条件判断
                if (fr == null || String.valueOf(fr).isBlank()) {
                    sumRecall += num(row.get("contextRecall"));
                    sumPrecision += num(row.get("contextPrecision"));
                    sumFaith += num(row.get("faithfulness"));
                    sumRel += num(row.get("answerRelevancy"));
                    scored++;
                }
                // 条件判断
                if (isCancelled(reportId)) {
                    log.info("eval batch cancelled after progress reportId={}", reportId);
                    return;
                }
                updateProgress(reportId, i + 1, caseResults);
            }
            // 条件判断
            if (isCancelled(reportId)) {
                return;
            }
            double recall = scored == 0 ? 0 : sumRecall / scored;
            double precision = scored == 0 ? 0 : sumPrecision / scored;
            double faith = scored == 0 ? 0 : sumFaith / scored;
            double rel = scored == 0 ? 0 : sumRel / scored;
            double overall = (recall + precision + faith + rel) / 4.0;
            // 调用 objectMapper.createObjectNode
            ObjectNode detail = objectMapper.createObjectNode();
            detail.put("version", 1);
            detail.putObject("thresholds")
                    .put("contextRecall", TH_RECALL)
                    .put("contextPrecision", TH_PRECISION)
                    .put("faithfulness", TH_FAITHFULNESS)
                    .put("answerRelevancy", TH_RELEVANCY);
            // 调用 objectMapper.valueToTree
            detail.set("cases", objectMapper.valueToTree(caseResults));
            long duration = System.currentTimeMillis() - t0;
            // 调用 jdbcTemplate.update
            jdbcTemplate.update(
                    """
                    UPDATE t_eval_report SET
                      status = 'COMPLETED',
                      done_count = ?,
                      context_recall = ?, context_precision = ?, faithfulness = ?, answer_relevancy = ?,
                      overall_score = ?, duration_ms = ?, detail_json = ?, update_time = ?
                    WHERE id = ? AND deleted = 0 AND status = 'RUNNING'
                    """,
                    cases.size(),
                    recall,
                    precision,
                    faith,
                    rel,
                    overall,
                    duration,
                    detail.toString(),
                    Timestamp.valueOf(LocalDateTime.now()),
                    reportId);
        } catch (Exception ex) {
            log.error("eval batch failed reportId={}", reportId, ex);
            long duration = System.currentTimeMillis() - t0;
            // 尝试执行
            try {
                // 调用 objectMapper.createObjectNode
                ObjectNode detail = objectMapper.createObjectNode();
                // 调用 objectMapper.valueToTree
                detail.set("cases", objectMapper.valueToTree(caseResults));
                // 调用 jdbcTemplate.update
                jdbcTemplate.update(
                        """
                        UPDATE t_eval_report SET
                          status = 'FAILED', fail_reason = ?, duration_ms = ?, detail_json = ?, update_time = ?
                        WHERE id = ? AND deleted = 0 AND status = 'RUNNING'
                        """,
                        abbreviate(ex.getMessage(), 500),
                        duration,
                        detail.toString(),
                        Timestamp.valueOf(LocalDateTime.now()),
                        reportId);
            } catch (Exception ignore) {
                log.warn("failed to persist FAILED status for {}", reportId);
            }
        }
    }

    /**
     * evaluateOne：业务处理。
     * @param c 参数 c
     * @param kbId 参数 kbId
     * @param strategyId 参数 strategyId
     * @return 返回结果
     */
    private Map<String, Object> evaluateOne(EvalCaseVo c, String kbId, String strategyId) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("caseId", c.getId());
        row.put("question", c.getQuestion());
        row.put("expectedAnswer", c.getExpectedAnswer());
        row.put("generatedAnswer", "");
        row.put("contextRecall", 0);
        row.put("contextPrecision", 0);
        row.put("faithfulness", 0);
        row.put("answerRelevancy", 0);
        row.put("avgScore", 0);
        row.put("failReason", null);
        // 尝试执行
        try {
            // 条件判断
            if (c.getExpectedAnswer() == null || c.getExpectedAnswer().isBlank()) {
                row.put("failReason", "缺少标准答案");
                return row;
            }
            KnowledgeRetrievalTestRequest rreq = new KnowledgeRetrievalTestRequest();
            rreq.setKnowledgeBaseId(kbId);
            rreq.setQuestion(c.getQuestion());
            rreq.setRetrievalStrategyId(strategyId);
            // 调用 knowledgeRetrievalAsvc.test
            RetrievalTestResult retrieval = knowledgeRetrievalAsvc.test(rreq);
            List<Map<String, Object>> sources =
                    retrieval.getSources() == null ? List.of() : retrieval.getSources();
            List<String> contexts = new ArrayList<>();
            List<String> retrievedIds = new ArrayList<>();
            int idx = 1;
            StringBuilder ctxBuilder = new StringBuilder();
            // 遍历处理
            for (Map<String, Object> src : sources) {
                Object content = src.get("content");
                // 条件判断
                if (content == null || String.valueOf(content).isBlank()) continue;
                String text = String.valueOf(content);
                contexts.add(text);
                Object sid = src.get("segment_id");
                // 条件判断
                if (sid == null) sid = src.get("segmentId");
                // 条件判断
                if (sid == null) sid = src.get("id");
                // 条件判断
                if (sid != null) retrievedIds.add(String.valueOf(sid));
                ctxBuilder.append('[').append(idx++).append("]\n").append(text).append("\n\n");
                // 条件判断
                if (ctxBuilder.length() > 14000) break;
            }
            String contextText = ctxBuilder.toString();
            String answer;
            // 条件判断
            if (contexts.isEmpty()) {
                answer = "知识库中未找到相关内容";
            } else {
                // 调用 agentRunAsvc.generateRagAnswer
                answer = agentRunAsvc.generateRagAnswer(c.getQuestion(), contexts);
            }
            row.put("generatedAnswer", answer);

            double recall = scoreContextRecall(c, retrievedIds, contextText);
            double precision = scoreContextPrecision(c.getQuestion(), c.getExpectedAnswer(), contexts, contextText);
            double faith = scoreFaithfulness(c.getQuestion(), contextText, answer);
            double relevancy = scoreAnswerRelevancy(c.getQuestion(), answer);
            double avg = (recall + precision + faith + relevancy) / 4.0;
            row.put("contextRecall", round4(recall));
            row.put("contextPrecision", round4(precision));
            row.put("faithfulness", round4(faith));
            row.put("answerRelevancy", round4(relevancy));
            row.put("avgScore", round4(avg));
        } catch (Exception ex) {
            row.put("failReason", abbreviate(ex.getMessage(), 240));
            log.warn("eval case failed caseId={}: {}", c.getId(), ex.getMessage());
        }
        return row;
    }

    /** Context Recall：优先 source_segment_ids 集合运算，否则 Judge */
    private double scoreContextRecall(EvalCaseVo c, List<String> retrievedIds, String contextText)
            throws Exception {
        Set<String> expected = parseSegmentIds(c.getSourceSegmentIds());
        // 条件判断
        if (!expected.isEmpty()) {
            // 条件判断
            if (retrievedIds.isEmpty()) return 0;
            Set<String> hit = new HashSet<>(retrievedIds);
            hit.retainAll(expected);
            return expected.isEmpty() ? 0 : (double) hit.size() / expected.size();
        }
        // 条件判断
        if (contextText == null || contextText.isBlank()) return 0;
        String prompt = loadPrompt(
                "EVAL_CONTEXT_RECALL",
                "你是 RAG 评测裁判。判断标准答案要点是否被资料覆盖。只输出 JSON {\"score\":0或1}。\n问题：{question}\n标准答案：{expected}\n资料：\n{context}");
        prompt = prompt
                .replace("{question}", safe(c.getQuestion()))
                .replace("{expected}", safe(c.getExpectedAnswer()))
                .replace("{context}", contextText);
        String raw = chatJudge(prompt);
        return clamp01(extractScore(raw, "score"));
    }

    /**
     * scoreContextPrecision：业务处理。
     * @param question 参数 question
     * @param expected 参数 expected
     * @param contexts 参数 contexts
     * @param contextText 参数 contextText
     * @return 返回结果
     */
    private double scoreContextPrecision(
            String question, String expected, List<String> contexts, String contextText) throws Exception {
        // 条件判断
        if (contexts == null || contexts.isEmpty()) return 0;
        String prompt = loadPrompt(
                "EVAL_CONTEXT_PRECISION",
                "对每条资料判断是否有用。只输出 JSON {\"scores\":[0或1,...]}。\n问题：{question}\n标准答案：{expected}\n资料：\n{context}");
        prompt = prompt
                .replace("{question}", safe(question))
                .replace("{expected}", safe(expected))
                .replace("{context}", contextText);
        String raw = chatJudge(prompt);
        List<Double> scores = extractScoreList(raw);
        // 条件判断
        if (scores.isEmpty()) {
            // fallback: 单分
            return clamp01(extractScore(raw, "score"));
        }
        // Average Precision@K（有用片段按名次加权）
        double hit = 0;
        double ap = 0;
        int k = Math.min(scores.size(), contexts.size());
        // 遍历处理
        for (int i = 0; i < k; i++) {
            // 条件判断
            if (scores.get(i) >= 0.5) {
                hit += 1;
                ap += hit / (i + 1.0);
            }
        }
        return hit <= 0 ? 0 : ap / hit;
    }

    /**
     * scoreFaithfulness：业务处理。
     * @param question 参数 question
     * @param contextText 参数 contextText
     * @param answer 参数 answer
     * @return 返回结果
     */
    private double scoreFaithfulness(String question, String contextText, String answer) throws Exception {
        // 条件判断
        if (answer == null || answer.isBlank()) return 0;
        // 条件判断
        if (contextText == null || contextText.isBlank()) {
            return answer.contains("未找到") ? 1.0 : 0.0;
        }
        String prompt = loadPrompt(
                "EVAL_FAITHFULNESS",
                "判断答案是否忠实于资料。只输出 JSON {\"score\":0到1}。\n问题：{question}\n资料：\n{context}\n答案：\n{answer}");
        prompt = prompt
                .replace("{question}", safe(question))
                .replace("{context}", contextText)
                .replace("{answer}", safe(answer));
        return clamp01(extractScore(chatJudge(prompt), "score"));
    }

    /** 答案相关性：反推问题 + 相关性打分（约 2 次模型调用） */
    private double scoreAnswerRelevancy(String question, String answer) throws Exception {
        // 条件判断
        if (answer == null || answer.isBlank()) return 0;
        String qPrompt = loadPrompt(
                "EVAL_RELEVANCY_QUESTIONS",
                "根据答案反推 3 个可能的用户问题，每行一个。\n答案：\n{answer}");
        qPrompt = qPrompt.replace("{answer}", safe(answer));
        String generatedQs = chatJudge(qPrompt);
        String prompt = loadPrompt(
                "EVAL_ANSWER_RELEVANCY",
                "判断答案是否在回答问题。只输出 JSON {\"score\":0到1}。\n问题：{question}\n答案：\n{answer}");
        prompt = prompt
                .replace("{question}", safe(question))
                .replace("{answer}", safe(answer) + "\n\n（参考反推问题）\n" + safe(generatedQs));
        return clamp01(extractScore(chatJudge(prompt), "score"));
    }

    /**
     * updateProgress：业务处理。
     * @param reportId 参数 reportId
     * @param done 参数 done
     * @param caseResults 参数 caseResults
     */
    private void updateProgress(String reportId, int done, List<Map<String, Object>> caseResults) {
        // 尝试执行
        try {
            // 调用 objectMapper.createObjectNode
            ObjectNode detail = objectMapper.createObjectNode();
            // 调用 objectMapper.valueToTree
            detail.set("cases", objectMapper.valueToTree(caseResults));
            // 调用 jdbcTemplate.update
            jdbcTemplate.update(
                    """
                    UPDATE t_eval_report SET done_count = ?, detail_json = ?, update_time = ?
                    WHERE id = ? AND deleted = 0 AND status = 'RUNNING'
                    """,
                    done,
                    detail.toString(),
                    Timestamp.valueOf(LocalDateTime.now()),
                    reportId);
        } catch (Exception ex) {
            log.warn("update progress failed: {}", ex.getMessage());
        }
    }

    /**
     * loadEvalCases：业务处理。
     * @param datasetId 参数 datasetId
     * @return 返回结果
     */
    private List<EvalCaseVo> loadEvalCases(String datasetId) {
        // 调用 jdbcTemplate.query
        return jdbcTemplate.query(
                """
                SELECT * FROM t_eval_case
                WHERE deleted = 0 AND dataset_id = ? AND include_in_eval = 1
                ORDER BY create_time ASC
                """,
                (rs, n) -> {
                    EvalCaseVo vo = new EvalCaseVo();
                    vo.setId(rs.getString("id"));
                    vo.setDatasetId(rs.getString("dataset_id"));
                    vo.setQuestion(rs.getString("question"));
                    vo.setExpectedAnswer(rs.getString("expected_answer"));
                    // 尝试执行
                    try {
                        vo.setSourceSegmentIds(rs.getString("source_segment_ids"));
                    } catch (Exception ignored) {
                    }
                    return vo;
                },
                datasetId);
    }

    /**
     * nextTaskNo：业务处理。
     * @return 返回结果
     */
    private long nextTaskNo() {
        // 尝试执行
        try {
            // 调用 jdbcTemplate.queryForObject
            Long max = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(task_no), 100) FROM t_eval_report", Long.class);
            long next = (max == null ? 100L : max) + 1;
            taskNoSeq.updateAndGet(v -> Math.max(v, next));
            return next;
        } catch (Exception e) {
            return taskNoSeq.incrementAndGet();
        }
    }

    /**
     * loadPrompt：业务处理。
     * @param code 参数 code
     * @param fallback 参数 fallback
     * @return 返回结果
     */
    private String loadPrompt(String code, String fallback) {
        // 尝试执行
        try {
            // 调用 jdbcTemplate.query
            List<String> rows = jdbcTemplate.query(
                    """
                    SELECT content FROM t_prompt_template
                    WHERE deleted = 0 AND enabled = 1 AND code = ?
                    LIMIT 1
                    """,
                    (rs, n) -> rs.getString("content"),
                    code);
            // 条件判断
            if (!rows.isEmpty() && rows.get(0) != null && !rows.get(0).isBlank()) {
                return rows.get(0);
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    /**
     * chatJudge：业务处理。
     * @param userContent 参数 userContent
     * @return 返回结果
     */
    private String chatJudge(String userContent) throws Exception {
        // 调用 jdbcTemplate.queryForList
        List<Map<String, Object>> models = jdbcTemplate.queryForList(
                """
                SELECT model_name, base_url, api_key_enc
                FROM t_ai_model_config
                WHERE deleted = 0 AND enabled = 1 AND model_type = 'CHAT'
                ORDER BY create_time ASC LIMIT 1
                """);
        // 条件判断
        if (models.isEmpty()) {
            throw new IllegalStateException("未配置对话模型");
        }
        Map<String, Object> m = models.get(0);
        String apiKey = m.get("api_key_enc") == null ? null : String.valueOf(m.get("api_key_enc"));
        // 条件判断
        if (apiKey != null && !apiKey.isBlank()) {
            // 调用 apiKeyCrypto.decryptFromStorage
            apiKey = apiKeyCrypto.decryptFromStorage(apiKey);
        }
        // 条件判断
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("DASHSCOPE_CHAT_API_KEY");
            // 条件判断
            if (apiKey == null || apiKey.isBlank()) apiKey = System.getenv("DASHSCOPE_API_KEY");
        }
        // 条件判断
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("对话模型未配置 API Key");
        }
        String base = m.get("base_url") == null || String.valueOf(m.get("base_url")).isBlank()
                ? "https://dashscope.aliyuncs.com/compatible-mode/v1"
                : String.valueOf(m.get("base_url"));
        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setModel(String.valueOf(m.get("model_name")));
        req.setApiKey(apiKey);
        req.setBaseUrl(base);
        req.setTemperature(0.0);
        req.setMessages(List.of(
                new ChatCompletionRequest.ChatMessage(
                        "system", "你是严格的 RAG 评测裁判，只输出合法 JSON 或纯文本列表，不要 Markdown。"),
                new ChatCompletionRequest.ChatMessage("user", userContent)));
        // 调用 aiEngineClient.chatCompletion
        ChatCompletionResult result = aiEngineClient.chatCompletion(req);
        // 条件判断
        if (result == null || result.getContent() == null || result.getContent().isBlank()) {
            throw new IllegalStateException("empty judge response");
        }
        return result.getContent().trim();
    }

    /**
     * extractScore：业务处理。
     * @param raw 参数 raw
     * @param key 参数 key
     * @return 返回结果
     */
    private double extractScore(String raw, String key) {
        JsonNode node = tryParseJson(raw);
        // 条件判断
        if (node != null) {
            // 条件判断
            if (node.has(key)) return node.get(key).asDouble(0);
            // 条件判断
            if (node.has("scores") && node.get("scores").isArray() && node.get("scores").size() > 0) {
                double sum = 0;
                // 遍历处理
                for (JsonNode s : node.get("scores")) sum += s.asDouble(0);
                return sum / node.get("scores").size();
            }
        }
        // 文本兜底
        String lower = raw.toLowerCase(Locale.ROOT);
        // 条件判断
        if (lower.contains("\"score\"")) {
            // 尝试执行
            try {
                int i = lower.indexOf("\"score\"");
                String sub = raw.substring(i);
                String num = sub.replaceAll("(?s).*?([01](?:\\.\\d+)?).*", "$1");
                return Double.parseDouble(num);
            } catch (Exception ignored) {
            }
        }
        // 条件判断
        if (raw.contains("1") && !raw.contains("0")) return 1;
        return 0;
    }

    /**
     * extractScoreList：业务处理。
     * @param raw 参数 raw
     * @return 返回结果
     */
    private List<Double> extractScoreList(String raw) {
        ArrayList<Double> out = new ArrayList<>();
        JsonNode node = tryParseJson(raw);
        // 条件判断
        if (node != null && node.has("scores") && node.get("scores").isArray()) {
            // 遍历处理
            for (JsonNode s : node.get("scores")) out.add(s.asDouble(0));
        }
        return out;
    }

    /**
     * tryParseJson：业务处理。
     * @param raw 参数 raw
     * @return 返回结果
     */
    private JsonNode tryParseJson(String raw) {
        // 条件判断
        if (raw == null) return null;
        String text = raw.trim();
        // 条件判断
        if (text.startsWith("```")) {
            int start = text.indexOf('\n');
            int end = text.lastIndexOf("```");
            // 条件判断
            if (start > 0 && end > start) text = text.substring(start + 1, end).trim();
        }
        // 尝试执行
        try {
            // 调用 objectMapper.readTree
            return objectMapper.readTree(text);
        } catch (Exception e) {
            int l = text.indexOf('{');
            int r = text.lastIndexOf('}');
            // 条件判断
            if (l >= 0 && r > l) {
                // 尝试执行
                try {
                    // 调用 objectMapper.readTree
                    return objectMapper.readTree(text.substring(l, r + 1));
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    /**
     * parseSegmentIds：业务处理。
     * @param raw 参数 raw
     * @return 返回结果
     */
    private Set<String> parseSegmentIds(String raw) {
        HashSet<String> set = new HashSet<>();
        // 条件判断
        if (raw == null || raw.isBlank()) return set;
        String t = raw.trim();
        // 尝试执行
        try {
            // 调用 objectMapper.readTree
            JsonNode n = objectMapper.readTree(t);
            // 条件判断
            if (n.isArray()) {
                // 遍历处理
                for (JsonNode x : n) {
                    // 条件判断
                    if (!x.asText("").isBlank()) set.add(x.asText());
                }
                return set;
            }
        } catch (Exception ignored) {
        }
        // 遍历处理
        for (String p : t.split("[,;\\s\\[\\]\"]+")) {
            // 条件判断
            if (!p.isBlank()) set.add(p.trim());
        }
        return set;
    }

    /**
     * reportMapper：业务处理。
     * @param withCases 参数 withCases
     * @return 返回结果
     */
    private RowMapper<EvalReportVo> reportMapper(boolean withCases) {
        return (rs, n) -> {
            EvalReportVo vo = new EvalReportVo();
            vo.setId(rs.getString("id"));
            // 尝试执行
            try {
                long tn = rs.getLong("task_no");
                vo.setTaskNo(rs.wasNull() ? null : tn);
            } catch (Exception ignored) {
            }
            vo.setDatasetId(rs.getString("dataset_id"));
            // 尝试执行
            try {
                vo.setDatasetName(rs.getString("dataset_name"));
            } catch (Exception ignored) {
            }
            vo.setRetrievalStrategyId(rs.getString("retrieval_strategy_id"));
            // 尝试执行
            try {
                vo.setStrategyName(rs.getString("strategy_name"));
            } catch (Exception ignored) {
            }
            vo.setStatus(rs.getString("status"));
            vo.setCaseCount(rs.getInt("case_count"));
            // 尝试执行
            try {
                vo.setDoneCount(rs.getInt("done_count"));
            } catch (Exception e) {
                vo.setDoneCount(0);
            }
            vo.setContextRecall(getDouble(rs, "context_recall"));
            vo.setContextPrecision(getDouble(rs, "context_precision"));
            vo.setFaithfulness(getDouble(rs, "faithfulness"));
            vo.setAnswerRelevancy(getDouble(rs, "answer_relevancy"));
            vo.setOverallScore(getDouble(rs, "overall_score"));
            // 尝试执行
            try {
                vo.setFailReason(rs.getString("fail_reason"));
            } catch (Exception ignored) {
            }
            // 尝试执行
            try {
                long d = rs.getLong("duration_ms");
                vo.setDurationMs(rs.wasNull() ? null : d);
            } catch (Exception ignored) {
            }
            // 条件判断
            if (vo.getDurationMs() != null && vo.getCaseCount() != null && vo.getCaseCount() > 0) {
                vo.setAvgLatencyMs(vo.getDurationMs() / vo.getCaseCount());
            }
            vo.setCapabilityTags(buildCapabilityTags(rs));
            Timestamp ct = rs.getTimestamp("create_time");
            vo.setCreateTime(DateTimes.format(ct));
            // 尝试执行
            try {
                String detail = rs.getString("detail_json");
                vo.setDetailJson(detail);
                // 条件判断
                if (withCases && detail != null && !detail.isBlank()) {
                    // 调用 objectMapper.readTree
                    JsonNode root = objectMapper.readTree(detail);
                    JsonNode cases = root.get("cases");
                    // 条件判断
                    if (cases != null && cases.isArray()) {
                        @SuppressWarnings("unchecked")
                        // 调用 objectMapper.convertValue
                        List<Map<String, Object>> list = objectMapper.convertValue(cases, List.class);
                        vo.setCases(list);
                    }
                }
            } catch (Exception ignored) {
            }
            return vo;
        };
    }

    /**
     * buildCapabilityTags：业务处理。
     * @param rs 参数 rs
     * @return 返回结果
     */
    private static List<String> buildCapabilityTags(java.sql.ResultSet rs) {
        ArrayList<String> tags = new ArrayList<>();
        // 尝试执行
        try {
            // 条件判断
            if (onFlag(rs, "enable_vector")) tags.add("向量");
            // 条件判断
            if (onFlag(rs, "enable_bm25")) tags.add("BM25");
            // 条件判断
            if (onFlag(rs, "enable_rerank")) tags.add("重排");
            // 条件判断
            if (onFlag(rs, "enable_rewrite")) {
                String mode = null;
                // 尝试执行
                try {
                    mode = rs.getString("rewrite_mode");
                } catch (Exception ignored) {
                }
                // 条件判断
                if (mode != null && mode.toLowerCase(Locale.ROOT).contains("hyde")) {
                    tags.add("HyDE");
                } else {
                    tags.add("多查询扩展");
                }
            }
            // 条件判断
            if (onFlag(rs, "enable_parent_fill")) tags.add("父块回填");
        } catch (Exception ignored) {
        }
        return tags;
    }

    /**
     * onFlag：业务处理。
     * @param rs 参数 rs
     * @param col 参数 col
     * @return 返回结果
     */
    private static boolean onFlag(java.sql.ResultSet rs, String col) {
        // 尝试执行
        try {
            int v = rs.getInt(col);
            return !rs.wasNull() && v != 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * getDouble：业务处理。
     * @param rs 参数 rs
     * @param col 参数 col
     * @return 返回结果
     */
    private static Double getDouble(java.sql.ResultSet rs, String col) {
        // 尝试执行
        try {
            double v = rs.getDouble(col);
            return rs.wasNull() ? null : v;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * num：业务处理。
     * @param o 参数 o
     * @return 返回结果
     */
    private static double num(Object o) {
        // 条件判断
        if (o == null) return 0;
        // 条件判断
        if (o instanceof Number n) return n.doubleValue();
        // 尝试执行
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * clamp01：业务处理。
     * @param v 参数 v
     * @return 返回结果
     */
    private static double clamp01(double v) {
        // 条件判断
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0;
        return Math.max(0, Math.min(1, v));
    }

    /**
     * round4：业务处理。
     * @param v 参数 v
     * @return 返回结果
     */
    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    /**
     * safe：业务处理。
     * @param s 参数 s
     * @return 返回结果
     */
    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * abbreviate：业务处理。
     * @param s 参数 s
     * @param max 参数 max
     * @return 返回结果
     */
    private static String abbreviate(String s, int max) {
        // 条件判断
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
