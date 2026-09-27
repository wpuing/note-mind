package com.notemind.client.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 检索测试结果，对应 POST /api/v1/ai/retrieval/test 响应。
 * <p>
 * 含策略元信息、改写查询、命中 sources、阶段耗时与总耗时。
 */
public class RetrievalTestResult {
    /** 原始用户问题。 */
    private String question;
    /** 检索所用知识库 ID。 */
    private String knowledgeBaseId;
    /** 分数刻度标识（如 cosine / rerank）。 */
    private String scoreScale;
    /** 分数刻度中文说明。 */
    private String scoreScaleLabel;
    /** 策略 ID。 */
    private String strategyId;
    /** 策略名称。 */
    private String strategyName;
    /** 改写后的查询列表。 */
    private List<String> rewrittenQueries = new ArrayList<>();
    /** 实际用于检索的查询列表。 */
    private List<String> retrievalQueries = new ArrayList<>();
    /** 命中片段明细。 */
    private List<Map<String, Object>> sources = new ArrayList<>();
    /** 各阶段耗时与状态。 */
    private List<Map<String, Object>> stages = new ArrayList<>();
    /** 命中条数。 */
    private Integer hitCount;
    /** 端到端耗时（毫秒）。 */
    private Integer elapsedMs;

    /**
     * 获取用户问题。
     *
     * @return 问题
     */
    public String getQuestion() { return question; }

    /**
     * 设置用户问题。
     *
     * @param question 问题
     */
    public void setQuestion(String question) { this.question = question; }

    /**
     * 获取知识库 ID。
     *
     * @return 知识库 ID
     */
    public String getKnowledgeBaseId() { return knowledgeBaseId; }

    /**
     * 设置知识库 ID。
     *
     * @param knowledgeBaseId 知识库 ID
     */
    public void setKnowledgeBaseId(String knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }

    /**
     * 获取分数刻度标识。
     *
     * @return 刻度代码
     */
    public String getScoreScale() { return scoreScale; }

    /**
     * 设置分数刻度标识。
     *
     * @param scoreScale 刻度代码
     */
    public void setScoreScale(String scoreScale) { this.scoreScale = scoreScale; }

    /**
     * 获取分数刻度说明。
     *
     * @return 中文标签
     */
    public String getScoreScaleLabel() { return scoreScaleLabel; }

    /**
     * 设置分数刻度说明。
     *
     * @param scoreScaleLabel 中文标签
     */
    public void setScoreScaleLabel(String scoreScaleLabel) { this.scoreScaleLabel = scoreScaleLabel; }

    /**
     * 获取策略 ID。
     *
     * @return 策略 ID
     */
    public String getStrategyId() { return strategyId; }

    /**
     * 设置策略 ID。
     *
     * @param strategyId 策略 ID
     */
    public void setStrategyId(String strategyId) { this.strategyId = strategyId; }

    /**
     * 获取策略名称。
     *
     * @return 策略名称
     */
    public String getStrategyName() { return strategyName; }

    /**
     * 设置策略名称。
     *
     * @param strategyName 策略名称
     */
    public void setStrategyName(String strategyName) { this.strategyName = strategyName; }

    /**
     * 获取改写查询列表。
     *
     * @return 改写后的查询
     */
    public List<String> getRewrittenQueries() { return rewrittenQueries; }

    /**
     * 设置改写查询列表。
     *
     * @param rewrittenQueries 改写后的查询
     */
    public void setRewrittenQueries(List<String> rewrittenQueries) { this.rewrittenQueries = rewrittenQueries; }

    /**
     * 获取实际检索查询列表。
     *
     * @return 检索查询
     */
    public List<String> getRetrievalQueries() { return retrievalQueries; }

    /**
     * 设置实际检索查询列表。
     *
     * @param retrievalQueries 检索查询
     */
    public void setRetrievalQueries(List<String> retrievalQueries) { this.retrievalQueries = retrievalQueries; }

    /**
     * 获取命中片段。
     *
     * @return sources
     */
    public List<Map<String, Object>> getSources() { return sources; }

    /**
     * 设置命中片段。
     *
     * @param sources sources
     */
    public void setSources(List<Map<String, Object>> sources) { this.sources = sources; }

    /**
     * 获取阶段耗时明细。
     *
     * @return stages
     */
    public List<Map<String, Object>> getStages() { return stages; }

    /**
     * 设置阶段耗时明细。
     *
     * @param stages stages
     */
    public void setStages(List<Map<String, Object>> stages) { this.stages = stages; }

    /**
     * 获取命中条数。
     *
     * @return 命中数
     */
    public Integer getHitCount() { return hitCount; }

    /**
     * 设置命中条数。
     *
     * @param hitCount 命中数
     */
    public void setHitCount(Integer hitCount) { this.hitCount = hitCount; }

    /**
     * 获取总耗时毫秒。
     *
     * @return 耗时
     */
    public Integer getElapsedMs() { return elapsedMs; }

    /**
     * 设置总耗时毫秒。
     *
     * @param elapsedMs 耗时
     */
    public void setElapsedMs(Integer elapsedMs) { this.elapsedMs = elapsedMs; }
}
