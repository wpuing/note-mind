package com.notemind.client.ai;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 下发给 AI 引擎的检索策略快照（七开关 + 阈值 + TopK）。
 * <p>
 * 由 Java 从 t_retrieval_strategy 组装后透传给 Python 检索管线。
 */
public class RetrievalStrategyParams {
    /** 是否启用向量检索。 */
    private boolean enableVector = true;
    /** 是否启用 BM25 关键词检索。 */
    private boolean enableBm25 = false;
    /** 是否启用 RRF 融合（只看名次）。 */
    private boolean enableRrf = false;
    /** 是否启用重排。 */
    private boolean enableRerank = false;
    /** 是否启用查询改写。 */
    private boolean enableRewrite = false;
    /** 是否启用父子回填。 */
    private boolean enableParentFill = false;
    /** 最终返回 TopK。 */
    private int topK = 5;
    /** 重排后保留条数。 */
    private int rerankTopN = 4;
    /** 向量通道召回条数。 */
    private int vectorTopK = 5;
    /** BM25 通道召回条数。 */
    private int bm25TopK = 0;
    /** RRF 常数 k。 */
    private int rrfK = 60;
    /** 余弦相似度阈值。 */
    private BigDecimal cosineThreshold;
    /** 重排分数阈值。 */
    private BigDecimal rerankThreshold;
    /** 改写模式，如 multi_query。 */
    private String rewriteMode = "multi_query";
    /** 改写生成条数。 */
    private int rewriteCount = 3;
    /** 策略业务 ID（展示用）。 */
    private String strategyId;
    /** 策略名称（展示用）。 */
    private String strategyName;

    /**
     * 是否启用向量检索。
     *
     * @return true 启用
     */
    public boolean isEnableVector() { return enableVector; }

    /**
     * 设置是否启用向量检索。
     *
     * @param enableVector true 启用
     */
    public void setEnableVector(boolean enableVector) { this.enableVector = enableVector; }

    /**
     * 是否启用 BM25。
     *
     * @return true 启用
     */
    public boolean isEnableBm25() { return enableBm25; }

    /**
     * 设置是否启用 BM25。
     *
     * @param enableBm25 true 启用
     */
    public void setEnableBm25(boolean enableBm25) { this.enableBm25 = enableBm25; }

    /**
     * 是否启用 RRF。
     *
     * @return true 启用
     */
    public boolean isEnableRrf() { return enableRrf; }

    /**
     * 设置是否启用 RRF。
     *
     * @param enableRrf true 启用
     */
    public void setEnableRrf(boolean enableRrf) { this.enableRrf = enableRrf; }

    /**
     * 是否启用重排。
     *
     * @return true 启用
     */
    public boolean isEnableRerank() { return enableRerank; }

    /**
     * 设置是否启用重排。
     *
     * @param enableRerank true 启用
     */
    public void setEnableRerank(boolean enableRerank) { this.enableRerank = enableRerank; }

    /**
     * 是否启用查询改写。
     *
     * @return true 启用
     */
    public boolean isEnableRewrite() { return enableRewrite; }

    /**
     * 设置是否启用查询改写。
     *
     * @param enableRewrite true 启用
     */
    public void setEnableRewrite(boolean enableRewrite) { this.enableRewrite = enableRewrite; }

    /**
     * 是否启用父子回填。
     *
     * @return true 启用
     */
    public boolean isEnableParentFill() { return enableParentFill; }

    /**
     * 设置是否启用父子回填。
     *
     * @param enableParentFill true 启用
     */
    public void setEnableParentFill(boolean enableParentFill) { this.enableParentFill = enableParentFill; }

    /**
     * 获取最终 TopK。
     *
     * @return TopK
     */
    public int getTopK() { return topK; }

    /**
     * 设置最终 TopK。
     *
     * @param topK TopK
     */
    public void setTopK(int topK) { this.topK = topK; }

    /**
     * 获取重排保留条数。
     *
     * @return rerankTopN
     */
    public int getRerankTopN() { return rerankTopN; }

    /**
     * 设置重排保留条数。
     *
     * @param rerankTopN 条数
     */
    public void setRerankTopN(int rerankTopN) { this.rerankTopN = rerankTopN; }

    /**
     * 获取向量召回条数。
     *
     * @return vectorTopK
     */
    public int getVectorTopK() { return vectorTopK; }

    /**
     * 设置向量召回条数。
     *
     * @param vectorTopK 条数
     */
    public void setVectorTopK(int vectorTopK) { this.vectorTopK = vectorTopK; }

    /**
     * 获取 BM25 召回条数。
     *
     * @return bm25TopK
     */
    public int getBm25TopK() { return bm25TopK; }

    /**
     * 设置 BM25 召回条数。
     *
     * @param bm25TopK 条数
     */
    public void setBm25TopK(int bm25TopK) { this.bm25TopK = bm25TopK; }

    /**
     * 获取 RRF 常数 k。
     *
     * @return rrfK
     */
    public int getRrfK() { return rrfK; }

    /**
     * 设置 RRF 常数 k。
     *
     * @param rrfK 常数
     */
    public void setRrfK(int rrfK) { this.rrfK = rrfK; }

    /**
     * 获取余弦阈值。
     *
     * @return 阈值
     */
    public BigDecimal getCosineThreshold() { return cosineThreshold; }

    /**
     * 设置余弦阈值。
     *
     * @param cosineThreshold 阈值
     */
    public void setCosineThreshold(BigDecimal cosineThreshold) { this.cosineThreshold = cosineThreshold; }

    /**
     * 获取重排阈值。
     *
     * @return 阈值
     */
    public BigDecimal getRerankThreshold() { return rerankThreshold; }

    /**
     * 设置重排阈值。
     *
     * @param rerankThreshold 阈值
     */
    public void setRerankThreshold(BigDecimal rerankThreshold) { this.rerankThreshold = rerankThreshold; }

    /**
     * 获取改写模式。
     *
     * @return 模式字符串
     */
    public String getRewriteMode() { return rewriteMode; }

    /**
     * 设置改写模式。
     *
     * @param rewriteMode 模式字符串
     */
    public void setRewriteMode(String rewriteMode) { this.rewriteMode = rewriteMode; }

    /**
     * 获取改写条数。
     *
     * @return 条数
     */
    public int getRewriteCount() { return rewriteCount; }

    /**
     * 设置改写条数。
     *
     * @param rewriteCount 条数
     */
    public void setRewriteCount(int rewriteCount) { this.rewriteCount = rewriteCount; }

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
}
