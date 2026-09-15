package com.notemind.application.service.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notemind.client.ai.RetrievalTestResult;
import com.notemind.common.result.PageResult;
import com.notemind.common.util.DateTimes;
import com.notemind.interfaces.knowledge.vo.KnowledgeBaseVo;
import com.notemind.interfaces.knowledge.vo.KnowledgeRetrievalTestRequest;
import com.notemind.interfaces.knowledge.vo.RecallBenchCompareRequest;
import com.notemind.interfaces.knowledge.vo.RecallBenchRunVo;
import com.notemind.interfaces.knowledge.vo.RetrievalStrategyVo;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * 召回调试台应用服务：多策略并排检索对比与历史记录。
 */

@Service
public class RecallBenchAsvc {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final KnowledgeBaseAsvc knowledgeBaseAsvc;
    private final RetrievalStrategyAsvc retrievalStrategyAsvc;
    private final KnowledgeRetrievalAsvc knowledgeRetrievalAsvc;

    /**
     * 注入 JDBC、JSON 及知识库/检索协作服务。
     *
     * @param jdbcTemplate            Spring JdbcTemplate
     * @param objectMapper            Jackson ObjectMapper
     * @param knowledgeBaseAsvc       知识库应用服务
     * @param retrievalStrategyAsvc   检索策略应用服务
     * @param knowledgeRetrievalAsvc  检索测试应用服务
     */
    public RecallBenchAsvc(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            KnowledgeBaseAsvc knowledgeBaseAsvc,
            RetrievalStrategyAsvc retrievalStrategyAsvc,
            KnowledgeRetrievalAsvc knowledgeRetrievalAsvc) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.knowledgeBaseAsvc = knowledgeBaseAsvc;
        this.retrievalStrategyAsvc = retrievalStrategyAsvc;
        this.knowledgeRetrievalAsvc = knowledgeRetrievalAsvc;
    }

    /**
     * 对同一问题用最多 4 套策略并排检索并落库调试记录。
     *
     * @param req 对比请求
     * @return 本次调试运行 VO（含各策略结果）
     */
    public RecallBenchRunVo compare(RecallBenchCompareRequest req) {
        // 问题必填
        if (req == null || req.getQuestion() == null || req.getQuestion().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入检索问题");
        }
        // 知识库必选
        if (req.getKnowledgeBaseId() == null || req.getKnowledgeBaseId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择知识库");
        }
        List<String> strategyIds = distinctIds(req.getRetrievalStrategyIds());
        // 至少一套策略
        if (strategyIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少选择 1 套对比策略");
        }
        // 最多四套
        if (strategyIds.size() > 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "最多选择 4 套策略并排对比");
        }

        // 校验并加载知识库
        KnowledgeBaseVo kb = knowledgeBaseAsvc.getById(req.getKnowledgeBaseId().trim());
        List<String> docIds = distinctIds(req.getDocumentIds());
        List<RetrievalTestResult> results = new ArrayList<>();
        List<String> strategyNames = new ArrayList<>();
        long t0 = System.currentTimeMillis();
        // 逐策略执行检索测试
        for (String sid : strategyIds) {
            // 加载策略名称
            RetrievalStrategyVo strategy = retrievalStrategyAsvc.getById(sid);
            strategyNames.add(strategy.getName());
            KnowledgeRetrievalTestRequest one = new KnowledgeRetrievalTestRequest();
            one.setQuestion(req.getQuestion().trim());
            one.setKnowledgeBaseId(kb.getId());
            one.setRetrievalStrategyId(sid);
            one.setDocumentIds(docIds.isEmpty() ? null : docIds);
            // 调用检索测试
            results.add(knowledgeRetrievalAsvc.test(one));
        }
        long elapsed = System.currentTimeMillis() - t0;

        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        LocalDateTime now = LocalDateTime.now();
        // 尝试执行
        try {
            // 落库调试运行记录
            jdbcTemplate.update(
                    """
                    INSERT INTO t_recall_debug_run
                      (id, create_time, update_time, deleted, question, knowledge_base_id, knowledge_base_name,
                       strategy_count, strategy_names_json, document_ids_json, elapsed_ms, result_json)
                    VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    id,
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now),
                    req.getQuestion().trim(),
                    kb.getId(),
                    kb.getName(),
                    strategyIds.size(),
                    // 序列化策略名列表
                    objectMapper.writeValueAsString(strategyNames),
                    // 序列化文档 ID 列表
                    objectMapper.writeValueAsString(docIds),
                    elapsed,
                    // 序列化各策略检索结果
                    objectMapper.writeValueAsString(results));
        } catch (Exception ex) {
            // 落库失败
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "保存调试记录失败: " + ex.getMessage());
        }

        RecallBenchRunVo vo = new RecallBenchRunVo();
        vo.setId(id);
        vo.setQuestion(req.getQuestion().trim());
        vo.setKnowledgeBaseId(kb.getId());
        vo.setKnowledgeBaseName(kb.getName());
        vo.setStrategyCount(strategyIds.size());
        vo.setStrategyNames(strategyNames);
        vo.setDocumentIds(docIds);
        vo.setElapsedMs(elapsed);
        vo.setCreateTime(DateTimes.format(now));
        vo.setResults(results);
        return vo;
    }

    /**
     * 分页查询召回调试历史。
     *
     * @param knowledgeBaseId 知识库过滤，可空
     * @param page            页码
     * @param pageSize        每页条数
     * @return 分页结果（列表不含完整 results）
     */
    public PageResult<RecallBenchRunVo> page(String knowledgeBaseId, int page, int pageSize) {
        int p = Math.max(page, 1);
        int size = Math.min(Math.max(pageSize, 1), 50);
        int offset = (p - 1) * size;
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE deleted = 0 ");
        // 按知识库过滤
        if (knowledgeBaseId != null && !knowledgeBaseId.isBlank()) {
            where.append(" AND knowledge_base_id = ? ");
            args.add(knowledgeBaseId.trim());
        }
        // 统计总数
        Integer total =
                // 调用 jdbcTemplate.queryForObject
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM t_recall_debug_run" + where, Integer.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(offset);
        pageArgs.add(size);
        // 分页列表（不含 result 反序列化）
        List<RecallBenchRunVo> records =
                // 调用 jdbcTemplate.query
                jdbcTemplate.query(
                        """
                        SELECT id, question, knowledge_base_id, knowledge_base_name, strategy_count,
                               strategy_names_json, document_ids_json, elapsed_ms, create_time, result_json
                        FROM t_recall_debug_run
                        """
                                + where
                                + " ORDER BY create_time DESC LIMIT ?, ?",
                        listMapper(false),
                        pageArgs.toArray());
        return PageResult.of(total == null ? 0 : total, p, size, records);
    }

    /**
     * 按 ID 获取调试详情（含完整结果）。
     *
     * @param id 运行 ID
     * @return 调试 VO
     */
    public RecallBenchRunVo get(String id) {
        // 按主键查询并反序列化结果
        List<RecallBenchRunVo> rows =
                // 调用 jdbcTemplate.query
                jdbcTemplate.query(
                        """
                        SELECT id, question, knowledge_base_id, knowledge_base_name, strategy_count,
                               strategy_names_json, document_ids_json, elapsed_ms, create_time, result_json
                        FROM t_recall_debug_run
                        WHERE deleted = 0 AND id = ?
                        """,
                        listMapper(true),
                        id);
        // 不存在则 404
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "调试记录不存在");
        }
        return rows.get(0);
    }

    /**
     * 逻辑删除单条调试记录。
     *
     * @param id 运行 ID
     */

    @Transactional
    public void delete(String id) {
        get(id);
        LocalDateTime now = LocalDateTime.now();
        // 逻辑删除调试记录
        jdbcTemplate.update(
                "UPDATE t_recall_debug_run SET deleted = 1, delete_time = ?, update_time = ? WHERE id = ? AND deleted = 0",
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                id);
    }

    /**
     * 批量逻辑删除调试记录。
     *
     * @param ids 运行 ID 列表
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
                    "UPDATE t_recall_debug_run SET deleted = 1, delete_time = ?, update_time = ? WHERE id = ? AND deleted = 0",
                    ts,
                    ts,
                    raw.trim());
        }
        return deleted;
    }

    /**
     * 构建调试记录行映射器。
     *
     * @param withResults 是否反序列化 result_json
     * @return RowMapper
     */
    private RowMapper<RecallBenchRunVo> listMapper(boolean withResults) {
        return (rs, rowNum) -> {
            RecallBenchRunVo vo = new RecallBenchRunVo();
            vo.setId(rs.getString("id"));
            vo.setQuestion(rs.getString("question"));
            vo.setKnowledgeBaseId(rs.getString("knowledge_base_id"));
            vo.setKnowledgeBaseName(rs.getString("knowledge_base_name"));
            vo.setStrategyCount(rs.getInt("strategy_count"));
            vo.setElapsedMs(rs.getObject("elapsed_ms") == null ? null : rs.getLong("elapsed_ms"));
            Timestamp ct = rs.getTimestamp("create_time");
            vo.setCreateTime(ct == null ? null : DateTimes.format(ct.toLocalDateTime()));
            vo.setStrategyNames(readStringList(rs.getString("strategy_names_json")));
            vo.setDocumentIds(readStringList(rs.getString("document_ids_json")));
            // 详情场景才解析完整结果
            if (withResults) {
                vo.setResults(readResults(rs.getString("result_json")));
            }
            return vo;
        };
    }

    /**
     * 解析字符串列表 JSON。
     *
     * @param json JSON 数组
     * @return 字符串列表
     */
    private List<String> readStringList(String json) {
        // 空则空列表
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        // 尝试执行
        try {
            // 调用 ObjectMapper 反序列化
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            // 解析失败降级
            return new ArrayList<>();
        }
    }

    /**
     * 解析检索结果列表 JSON。
     *
     * @param json JSON 数组
     * @return 检索结果列表
     */
    private List<RetrievalTestResult> readResults(String json) {
        // 空则空列表
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        // 尝试执行
        try {
            // 调用 ObjectMapper 反序列化检索结果
            return objectMapper.readValue(json, new TypeReference<List<RetrievalTestResult>>() {});
        } catch (Exception ex) {
            // 解析失败降级
            return new ArrayList<>();
        }
    }

    /**
     * 去重并去掉空白的 ID 列表（保序）。
     *
     * @param ids 原始 ID 列表
     * @return 去重后的列表
     */
    private static List<String> distinctIds(List<String> ids) {
        // 空输入
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        // 遍历处理
        for (String id : ids) {
            // 跳过空 ID
            if (id != null && !id.isBlank()) {
                set.add(id.trim());
            }
        }
        return new ArrayList<>(set);
    }
}

