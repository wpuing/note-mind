package com.notemind.application.service.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notemind.client.ai.AiEngineClient;
import com.notemind.client.ai.ChatCompletionRequest;
import com.notemind.client.ai.ChatCompletionResult;
import com.notemind.common.result.PageResult;
import com.notemind.common.util.DateTimes;
import com.notemind.interfaces.eval.vo.EvalCaseSaveRequest;
import com.notemind.interfaces.eval.vo.EvalCaseVo;
import com.notemind.interfaces.eval.vo.EvalDatasetSaveRequest;
import com.notemind.interfaces.eval.vo.EvalDatasetVo;
import com.notemind.interfaces.eval.vo.EvalGenerateFromDocRequest;
import com.notemind.infrastructure.security.ApiKeyCrypto;
import com.notemind.application.service.system.SystemConfigAsvc;
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
import java.util.Map;
import java.util.UUID;

/**
 * 评测集应用服务：评测集/用例 CRUD、文档生成与点踩沉淀。
 */
@Service
public class EvalAsvc {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ApiKeyCrypto apiKeyCrypto;
    private final AiEngineClient aiEngineClient;
    private final SystemConfigAsvc systemConfig;

    /**
     * 构造 EvalAsvc 并注入依赖。
     * @param jdbcTemplate 参数 jdbcTemplate
     * @param objectMapper 参数 objectMapper
     * @param apiKeyCrypto 参数 apiKeyCrypto
     * @param aiEngineClient 参数 aiEngineClient
     * @param systemConfig 参数 systemConfig
     */
    public EvalAsvc(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ApiKeyCrypto apiKeyCrypto,
            AiEngineClient aiEngineClient,
            SystemConfigAsvc systemConfig) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.apiKeyCrypto = apiKeyCrypto;
        this.aiEngineClient = aiEngineClient;
        this.systemConfig = systemConfig;
    }

    /**
     * listDatasets：业务处理。
     * @return 返回结果
     */
    public List<EvalDatasetVo> listDatasets() {
        // 调用 jdbcTemplate.query
        return jdbcTemplate.query(
                """
                SELECT d.*, kb.name AS kb_name,
                  (SELECT COUNT(1) FROM t_eval_case c WHERE c.deleted = 0 AND c.dataset_id = d.id) AS case_count
                FROM t_eval_dataset d
                LEFT JOIN t_knowledge_base kb ON kb.id = d.knowledge_base_id AND kb.deleted = 0
                WHERE d.deleted = 0
                ORDER BY d.create_time DESC
                """,
                datasetMapper());
    }

    /**
     * getDataset：业务处理。
     * @param id 参数 id
     * @return 返回结果
     */
    public EvalDatasetVo getDataset(String id) {
        // 调用 jdbcTemplate.query
        List<EvalDatasetVo> rows = jdbcTemplate.query(
                """
                SELECT d.*, kb.name AS kb_name,
                  (SELECT COUNT(1) FROM t_eval_case c WHERE c.deleted = 0 AND c.dataset_id = d.id) AS case_count
                FROM t_eval_dataset d
                LEFT JOIN t_knowledge_base kb ON kb.id = d.knowledge_base_id AND kb.deleted = 0
                WHERE d.deleted = 0 AND d.id = ?
                """,
                datasetMapper(),
                id);
        // 条件判断
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评测集不存在");
        }
        return rows.get(0);
    }

    /**
     * createDataset：业务处理。
     * @param req 参数 req
     * @return 返回结果
     */
    @Transactional
    public EvalDatasetVo createDataset(EvalDatasetSaveRequest req) {
        // 条件判断
        if (req == null || req.getName() == null || req.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评测集名称必填");
        }
        String id = "ed_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        LocalDateTime now = LocalDateTime.now();
        String sourceType = blankOr(req.getSourceType(), "MANUAL");
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                """
                INSERT INTO t_eval_dataset (
                  id, create_time, update_time, deleted,
                  name, source_type, description, knowledge_base_id
                ) VALUES (?, ?, ?, 0, ?, ?, ?, ?)
                """,
                id,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                req.getName().trim(),
                sourceType,
                blankToNull(req.getDescription()),
                blankToNull(req.getKnowledgeBaseId()));
        return getDataset(id);
    }

    /**
     * deleteDataset：业务处理。
     * @param id 参数 id
     */
    @Transactional
    public void deleteDataset(String id) {
        getDataset(id);
        LocalDateTime now = LocalDateTime.now();
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                "UPDATE t_eval_case SET deleted = 1, delete_time = ?, update_time = ? WHERE dataset_id = ? AND deleted = 0",
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                id);
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                "UPDATE t_eval_dataset SET deleted = 1, delete_time = ?, update_time = ? WHERE id = ? AND deleted = 0",
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                id);
    }

    /**
     * pageCases：业务处理。
     * @param datasetId 参数 datasetId
     * @param sourceType 参数 sourceType
     * @param question 参数 question
     * @param page 参数 page
     * @param pageSize 参数 pageSize
     * @return 返回结果
     */
    public PageResult<EvalCaseVo> pageCases(
            String datasetId, String sourceType, String question, int page, int pageSize) {
        // 条件判断
        if (datasetId == null || datasetId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "datasetId required");
        }
        getDataset(datasetId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        StringBuilder where = new StringBuilder(" WHERE deleted = 0 AND dataset_id = ?");
        ArrayList<Object> args = new ArrayList<>();
        args.add(datasetId);
        // 条件判断
        if (sourceType != null && !sourceType.isBlank()) {
            where.append(" AND source_type = ?");
            args.add(sourceType.trim().toUpperCase(Locale.ROOT));
        }
        // 条件判断
        if (question != null && !question.isBlank()) {
            where.append(" AND question LIKE ?");
            args.add("%" + question.trim() + "%");
        }
        // 调用 jdbcTemplate.queryForObject
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_eval_case" + where, Long.class, args.toArray());
        ArrayList<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeSize);
        listArgs.add((safePage - 1) * safeSize);
        // 调用 jdbcTemplate.query
        List<EvalCaseVo> records = jdbcTemplate.query(
                "SELECT * FROM t_eval_case" + where + " ORDER BY create_time DESC LIMIT ? OFFSET ?",
                caseMapper(),
                listArgs.toArray());
        return PageResult.of(total == null ? 0L : total, safePage, safeSize, records);
    }

    /**
     * getCase：业务处理。
     * @param id 参数 id
     * @return 返回结果
     */
    public EvalCaseVo getCase(String id) {
        // 调用 jdbcTemplate.query
        List<EvalCaseVo> rows = jdbcTemplate.query(
                "SELECT * FROM t_eval_case WHERE deleted = 0 AND id = ?", caseMapper(), id);
        // 条件判断
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用例不存在");
        }
        return rows.get(0);
    }

    /**
     * createCase：业务处理。
     * @param datasetId 参数 datasetId
     * @param req 参数 req
     * @return 返回结果
     */
    @Transactional
    public EvalCaseVo createCase(String datasetId, EvalCaseSaveRequest req) {
        EvalDatasetVo ds = getDataset(datasetId);
        // 条件判断
        if (req == null || req.getQuestion() == null || req.getQuestion().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "问题必填");
        }
        String id = insertCase(
                datasetId,
                req.getQuestion().trim(),
                blankToNull(req.getExpectedAnswer()),
                blankOr(req.getSourceType(), "MANUAL"),
                req.getIncludeInEval() == null ? 1 : (req.getIncludeInEval() == 0 ? 0 : 1),
                blankToNull(req.getRemark()),
                blankToNull(req.getKnowledgeBaseId()) != null
                        ? req.getKnowledgeBaseId().trim()
                        : ds.getKnowledgeBaseId(),
                blankToNull(req.getDocumentId()),
                blankToNull(req.getSourceSegmentIds()),
                blankToNull(req.getSourceContent()),
                blankToNull(req.getSourceLabel()));
        return getCase(id);
    }

    /**
     * updateCase：业务处理。
     * @param id 参数 id
     * @param req 参数 req
     * @return 返回结果
     */
    @Transactional
    public EvalCaseVo updateCase(String id, EvalCaseSaveRequest req) {
        EvalCaseVo old = getCase(id);
        // 条件判断
        if (req == null || req.getQuestion() == null || req.getQuestion().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "问题必填");
        }
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                """
                UPDATE t_eval_case
                SET question = ?, expected_answer = ?, include_in_eval = ?, remark = ?, update_time = ?
                WHERE id = ? AND deleted = 0
                """,
                req.getQuestion().trim(),
                blankToNull(req.getExpectedAnswer()),
                req.getIncludeInEval() == null ? old.getIncludeInEval() : (req.getIncludeInEval() == 0 ? 0 : 1),
                blankToNull(req.getRemark()),
                Timestamp.valueOf(LocalDateTime.now()),
                id);
        return getCase(id);
    }

    /**
     * deleteCase：业务处理。
     * @param id 参数 id
     */
    @Transactional
    public void deleteCase(String id) {
        getCase(id);
        LocalDateTime now = LocalDateTime.now();
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                "UPDATE t_eval_case SET deleted = 1, delete_time = ?, update_time = ? WHERE id = ? AND deleted = 0",
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                id);
    }

    /**
     * batchDeleteDatasets：业务处理。
     * @param ids 参数 ids
     * @return 返回结果
     */
    @Transactional
    public int batchDeleteDatasets(List<String> ids) {
        // 条件判断
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        // 遍历处理
        for (String raw : ids) {
            // 条件判断
            if (raw == null || raw.isBlank()) continue;
            String id = raw.trim();
            // 调用 jdbcTemplate.queryForObject
            Long n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM t_eval_dataset WHERE id = ? AND deleted = 0", Long.class, id);
            // 条件判断
            if (n == null || n == 0) continue;
            deleteDataset(id);
            deleted++;
        }
        return deleted;
    }

    /**
     * batchDeleteCases：业务处理。
     * @param ids 参数 ids
     * @return 返回结果
     */
    @Transactional
    public int batchDeleteCases(List<String> ids) {
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
                    "UPDATE t_eval_case SET deleted = 1, delete_time = ?, update_time = ? WHERE id = ? AND deleted = 0",
                    ts,
                    ts,
                    raw.trim());
        }
        return deleted;
    }

    /**
     * generateFromDocument：业务处理。
     * @param datasetId 参数 datasetId
     * @param req 参数 req
     * @return 返回结果
     */
    @Transactional
    public Map<String, Object> generateFromDocument(String datasetId, EvalGenerateFromDocRequest req) {
        EvalDatasetVo ds = getDataset(datasetId);
        // 条件判断
        if (req == null || req.getDocumentId() == null || req.getDocumentId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择文档");
        }
        String kbId = blankToNull(req.getKnowledgeBaseId()) != null
                ? req.getKnowledgeBaseId().trim()
                : ds.getKnowledgeBaseId();
        // 条件判断
        if (kbId == null || kbId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择知识库");
        }
        int count = req.getCount() == null ? 5 : Math.min(Math.max(req.getCount(), 1), 20);
        // 调用 jdbcTemplate.queryForList
        List<Map<String, Object>> segs = jdbcTemplate.queryForList(
                """
                SELECT id, segment_index, content, meta_json
                FROM t_knowledge_segment
                WHERE deleted = 0 AND document_id = ?
                  AND segment_type IN ('CHUNK', 'CHILD', 'PARENT')
                ORDER BY segment_index ASC
                LIMIT 40
                """,
                req.getDocumentId().trim());
        // 条件判断
        if (segs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该文档暂无片段，请先切分");
        }
        StringBuilder ctx = new StringBuilder();
        List<String> usedIds = new ArrayList<>();
        // 遍历处理
        for (Map<String, Object> s : segs) {
            String content = String.valueOf(s.get("content"));
            // 条件判断
            if (content == null || content.isBlank()) continue;
            String sid = String.valueOf(s.get("id"));
            int idx = ((Number) s.get("segment_index")).intValue();
            ctx.append("【片段 #").append(sid.length() > 6 ? sid.substring(sid.length() - 4) : sid)
                    .append(" · 第").append(idx + 1).append("段】\n")
                    .append(content).append("\n\n");
            usedIds.add(sid);
            // 条件判断
            if (ctx.length() > 12000) break;
        }
        String promptTpl = loadPrompt("EVAL_CASE_GEN");
        String userPrompt = promptTpl
                .replace("{count}", String.valueOf(count))
                .replace("{context}", ctx.toString());
        userPrompt += "\n\n请严格输出 JSON 数组，每项形如 "
                + "{\"question\":\"...\",\"answer\":\"...\",\"segment_hint\":\"片段编号或段落摘要\"}，不要 markdown。";

        String raw;
        // 尝试执行
        try {
            raw = chatCompletion(userPrompt);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型生成失败: " + abbreviate(e.getMessage(), 200));
        }
        List<GeneratedQa> qas = parseGenerated(raw);
        // 条件判断
        if (qas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型未返回可用问答对，请重试");
        }
        String primarySeg = usedIds.isEmpty() ? null : usedIds.get(0);
        String sourceContent = ctx.length() > 4000 ? ctx.substring(0, 4000) + "…" : ctx.toString();
        int created = 0;
        // 遍历处理
        for (GeneratedQa qa : qas) {
            // 条件判断
            if (qa.question == null || qa.question.isBlank()) continue;
            String label = qa.hint != null && !qa.hint.isBlank()
                    ? qa.hint
                    : ("文档生成 · 共引用 " + usedIds.size() + " 个片段");
            String segJson;
            // 尝试执行
            try {
                // 调用 objectMapper.writeValueAsString
                segJson = objectMapper.writeValueAsString(usedIds.size() > 3 ? usedIds.subList(0, 3) : usedIds);
            } catch (Exception e) {
                segJson = primarySeg == null ? null : "[\"" + primarySeg + "\"]";
            }
            insertCase(
                    datasetId,
                    qa.question.trim(),
                    blankToNull(qa.answer),
                    "FROM_DOC",
                    1,
                    null,
                    kbId,
                    req.getDocumentId().trim(),
                    segJson,
                    sourceContent,
                    label);
            created++;
            // 条件判断
            if (created >= count) break;
        }
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                "UPDATE t_eval_dataset SET source_type = 'FROM_DOC', knowledge_base_id = ?, update_time = ? WHERE id = ?",
                kbId,
                Timestamp.valueOf(LocalDateTime.now()),
                datasetId);
        return Map.of("created", created);
    }

    /**
     * importFromDislikes：业务处理。
     * @param datasetId 参数 datasetId
     * @return 返回结果
     */
    @Transactional
    public Map<String, Object> importFromDislikes(String datasetId) {
        getDataset(datasetId);
        // 调用 jdbcTemplate.queryForList
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT m.id, m.content AS answer, m.session_id,
                  (SELECT u.content FROM t_chat_message u
                     WHERE u.deleted = 0 AND u.session_id = m.session_id AND u.role = 'USER'
                       AND u.create_time <= m.create_time
                     ORDER BY u.create_time DESC LIMIT 1) AS question
                FROM t_chat_message m
                WHERE m.deleted = 0 AND m.role = 'ASSISTANT' AND m.feedback = 'DISLIKE'
                  AND IFNULL(m.settled, 0) = 0
                ORDER BY m.create_time DESC
                LIMIT 100
                """);
        // 条件判断
        if (rows.isEmpty()) {
            return Map.of("created", 0, "message", "没有待沉淀的点踩问题");
        }
        int created = 0;
        LocalDateTime now = LocalDateTime.now();
        // 遍历处理
        for (Map<String, Object> row : rows) {
            String q = row.get("question") == null ? null : String.valueOf(row.get("question"));
            // 条件判断
            if (q == null || q.isBlank()) continue;
            insertCase(
                    datasetId,
                    q.trim(),
                    null,
                    "FROM_DISLIKE",
                    1,
                    "来自点踩沉淀，请手工补充标准答案",
                    null,
                    null,
                    null,
                    row.get("answer") == null ? null : String.valueOf(row.get("answer")),
                    "点踩沉淀");
            // 调用 jdbcTemplate.update
            jdbcTemplate.update(
                    "UPDATE t_chat_message SET settled = 1, update_time = ? WHERE id = ?",
                    Timestamp.valueOf(now),
                    row.get("id"));
            created++;
        }
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                "UPDATE t_eval_dataset SET source_type = 'FROM_DISLIKE', update_time = ? WHERE id = ?",
                Timestamp.valueOf(now),
                datasetId);
        return Map.of("created", created);
    }

    /**
     * countPendingDislikes：业务处理。
     * @return 返回结果
     */
    public int countPendingDislikes() {
        // 调用 jdbcTemplate.queryForObject
        Long n = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1) FROM t_chat_message
                WHERE deleted = 0 AND role = 'ASSISTANT' AND feedback = 'DISLIKE'
                  AND IFNULL(settled, 0) = 0
                """,
                Long.class);
        return n == null ? 0 : n.intValue();
    }

    /**
     * insertCase：业务处理。
     * @param datasetId 参数 datasetId
     * @param question 参数 question
     * @param expectedAnswer 参数 expectedAnswer
     * @param sourceType 参数 sourceType
     * @param includeInEval 参数 includeInEval
     * @param remark 参数 remark
     * @param kbId 参数 kbId
     * @param documentId 参数 documentId
     * @param sourceSegmentIds 参数 sourceSegmentIds
     * @param sourceContent 参数 sourceContent
     * @param sourceLabel 参数 sourceLabel
     * @return 返回结果
     */
    private String insertCase(
            String datasetId,
            String question,
            String expectedAnswer,
            String sourceType,
            int includeInEval,
            String remark,
            String kbId,
            String documentId,
            String sourceSegmentIds,
            String sourceContent,
            String sourceLabel) {
        String id = "ec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        LocalDateTime now = LocalDateTime.now();
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                """
                INSERT INTO t_eval_case (
                  id, create_time, update_time, deleted,
                  dataset_id, question, expected_answer, source_segment_ids,
                  knowledge_base_id, document_id, source_type, include_in_eval,
                  remark, source_content, source_label
                ) VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                datasetId,
                question,
                expectedAnswer,
                sourceSegmentIds,
                kbId,
                documentId,
                sourceType,
                includeInEval,
                remark,
                sourceContent,
                sourceLabel);
        return id;
    }

    /**
     * loadPrompt：业务处理。
     * @param code 参数 code
     * @return 返回结果
     */
    private String loadPrompt(String code) {
        // 调用 jdbcTemplate.query
        List<String> rows = jdbcTemplate.query(
                """
                SELECT content FROM t_prompt_template
                WHERE deleted = 0 AND enabled = 1 AND code = ?
                LIMIT 1
                """,
                (rs, i) -> rs.getString("content"),
                code);
        // 条件判断
        if (!rows.isEmpty() && rows.get(0) != null && !rows.get(0).isBlank()) {
            return rows.get(0);
        }
        return "请根据以下资料生成 {count} 条问答对。每条包含 question 与 answer。\n资料：\n{context}";
    }

    /**
     * chatCompletion：业务处理。
     * @param userContent 参数 userContent
     * @return 返回结果
     */
    private String chatCompletion(String userContent) throws Exception {
        // 调用 jdbcTemplate.queryForList
        List<Map<String, Object>> models = jdbcTemplate.queryForList(
                """
                SELECT model_name, base_url, api_key_enc, temperature
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
        // 调用 systemConfig.chatTemperature
        req.setTemperature(systemConfig.chatTemperature());
        req.setMessages(List.of(
                new ChatCompletionRequest.ChatMessage("system", "你是企业知识库评测用例生成助手，只输出合法 JSON。"),
                new ChatCompletionRequest.ChatMessage("user", userContent)));
        // 调用 aiEngineClient.chatCompletion
        ChatCompletionResult result = aiEngineClient.chatCompletion(req);
        // 条件判断
        if (result == null || result.getContent() == null || result.getContent().isBlank()) {
            throw new IllegalStateException("empty chat response");
        }
        return result.getContent().trim();
    }

    /**
     * parseGenerated：业务处理。
     * @param raw 参数 raw
     * @return 返回结果
     */
    private List<GeneratedQa> parseGenerated(String raw) {
        ArrayList<GeneratedQa> out = new ArrayList<>();
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
            JsonNode node = objectMapper.readTree(text);
            // 条件判断
            if (node.isObject() && node.has("items")) node = node.get("items");
            // 条件判断
            if (node.isArray()) {
                // 遍历处理
                for (JsonNode item : node) {
                    GeneratedQa qa = new GeneratedQa();
                    qa.question = textOf(item, "question", "q");
                    qa.answer = textOf(item, "answer", "expected_answer", "a");
                    qa.hint = textOf(item, "segment_hint", "hint", "source");
                    out.add(qa);
                }
                return out;
            }
        } catch (Exception ignored) {
        }
        // fallback: Q:/A: lines
        String[] lines = text.split("\n");
        GeneratedQa cur = null;
        // 遍历处理
        for (String line : lines) {
            String t = line.trim();
            // 条件判断
            if (t.matches("(?i)^(\\d+[\\.、)]\\s*)?(Q|问题)[:：].*")) {
                // 条件判断
                if (cur != null && cur.question != null) out.add(cur);
                cur = new GeneratedQa();
                cur.question = t.replaceFirst("(?i)^(\\d+[\\.、)]\\s*)?(Q|问题)[:：]\\s*", "");
            } else if (cur != null && t.matches("(?i)^(A|答案|标准答案)[:：].*")) {
                cur.answer = t.replaceFirst("(?i)^(A|答案|标准答案)[:：]\\s*", "");
            }
        }
        // 条件判断
        if (cur != null && cur.question != null) out.add(cur);
        return out;
    }

    /**
     * textOf：业务处理。
     * @param item 参数 item
     * @param keys 参数 keys
     * @return 返回结果
     */
    private static String textOf(JsonNode item, String... keys) {
        // 遍历处理
        for (String k : keys) {
            JsonNode n = item.get(k);
            // 条件判断
            if (n != null && !n.isNull() && !n.asText("").isBlank()) return n.asText();
        }
        return null;
    }

    /**
     * datasetMapper：业务处理。
     * @return 返回结果
     */
    private RowMapper<EvalDatasetVo> datasetMapper() {
        return (rs, n) -> {
            EvalDatasetVo vo = new EvalDatasetVo();
            vo.setId(rs.getString("id"));
            vo.setName(rs.getString("name"));
            vo.setDescription(rs.getString("description"));
            vo.setSourceType(rs.getString("source_type"));
            // 尝试执行
            try {
                vo.setKnowledgeBaseId(rs.getString("knowledge_base_id"));
            } catch (Exception ignored) {
            }
            // 尝试执行
            try {
                vo.setKnowledgeBaseName(rs.getString("kb_name"));
            } catch (Exception ignored) {
            }
            // 尝试执行
            try {
                vo.setCaseCount(rs.getInt("case_count"));
            } catch (Exception e) {
                vo.setCaseCount(0);
            }
            Timestamp ct = rs.getTimestamp("create_time");
            vo.setCreateTime(DateTimes.format(ct));
            return vo;
        };
    }

    /**
     * caseMapper：业务处理。
     * @return 返回结果
     */
    private RowMapper<EvalCaseVo> caseMapper() {
        return (rs, n) -> {
            EvalCaseVo vo = new EvalCaseVo();
            vo.setId(rs.getString("id"));
            vo.setDatasetId(rs.getString("dataset_id"));
            vo.setQuestion(rs.getString("question"));
            vo.setExpectedAnswer(rs.getString("expected_answer"));
            vo.setKnowledgeBaseId(rs.getString("knowledge_base_id"));
            vo.setDocumentId(rs.getString("document_id"));
            Object seg = rs.getObject("source_segment_ids");
            vo.setSourceSegmentIds(seg == null ? null : String.valueOf(seg));
            // 尝试执行
            try {
                vo.setSourceType(rs.getString("source_type"));
            } catch (Exception e) {
                vo.setSourceType("MANUAL");
            }
            // 尝试执行
            try {
                vo.setIncludeInEval(rs.getInt("include_in_eval"));
                // 条件判断
                if (rs.wasNull()) vo.setIncludeInEval(1);
            } catch (Exception e) {
                vo.setIncludeInEval(1);
            }
            // 尝试执行
            try {
                vo.setRemark(rs.getString("remark"));
            } catch (Exception ignored) {
            }
            // 尝试执行
            try {
                vo.setSourceContent(rs.getString("source_content"));
            } catch (Exception ignored) {
            }
            // 尝试执行
            try {
                vo.setSourceLabel(rs.getString("source_label"));
            } catch (Exception ignored) {
            }
            Timestamp ct = rs.getTimestamp("create_time");
            vo.setCreateTime(DateTimes.format(ct));
            return vo;
        };
    }

    /**
     * blankToNull：业务处理。
     * @param s 参数 s
     * @return 返回结果
     */
    private static String blankToNull(String s) {
        // 条件判断
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }

    /**
     * blankOr：业务处理。
     * @param s 参数 s
     * @param def 参数 def
     * @return 返回结果
     */
    private static String blankOr(String s, String def) {
        return s == null || s.isBlank() ? def : s.trim();
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

    private static class GeneratedQa {
        String question;
        String answer;
        String hint;
    }
}
