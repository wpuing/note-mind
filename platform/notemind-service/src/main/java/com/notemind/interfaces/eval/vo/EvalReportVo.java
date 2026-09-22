package com.notemind.interfaces.eval.vo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 批量评测报告视图对象。
 */
public class EvalReportVo {
    private String id;
    private Long taskNo;
    private String datasetId;
    private String datasetName;
    private String retrievalStrategyId;
    private String strategyName;
    private String status;
    private Integer caseCount;
    private Integer doneCount;
    private Double contextRecall;
    private Double contextPrecision;
    private Double faithfulness;
    private Double answerRelevancy;
    private Double overallScore;
    private String failReason;
    private Long durationMs;
    private String createTime;
    private String detailJson;
    private List<Map<String, Object>> cases = new ArrayList<>();
    /** 启用的能力标签：向量 / BM25 / 重排 / 多查询扩展 / 父块回填 */
    private List<String> capabilityTags = new ArrayList<>();
    /** 单条平均耗时 ms = durationMs / caseCount */
    private Long avgLatencyMs;

    /** 获取主键 ID */
    public String getId() { return id; }
    /** 设置主键 ID */
    public void setId(String id) { this.id = id; }
    /** 获取task No */
    public Long getTaskNo() { return taskNo; }
    /** 设置task No */
    public void setTaskNo(Long taskNo) { this.taskNo = taskNo; }
    /** 获取评测集 ID */
    public String getDatasetId() { return datasetId; }
    /** 设置评测集 ID */
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    /** 获取dataset Name */
    public String getDatasetName() { return datasetName; }
    /** 设置dataset Name */
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }
    /** 获取检索策略 ID */
    public String getRetrievalStrategyId() { return retrievalStrategyId; }
    /** 设置检索策略 ID */
    public void setRetrievalStrategyId(String retrievalStrategyId) { this.retrievalStrategyId = retrievalStrategyId; }
    /** 获取strategy Name */
    public String getStrategyName() { return strategyName; }
    /** 设置strategy Name */
    public void setStrategyName(String strategyName) { this.strategyName = strategyName; }
    /** 获取状态 */
    public String getStatus() { return status; }
    /** 设置状态 */
    public void setStatus(String status) { this.status = status; }
    /** 获取case Count */
    public Integer getCaseCount() { return caseCount; }
    /** 设置case Count */
    public void setCaseCount(Integer caseCount) { this.caseCount = caseCount; }
    /** 获取done Count */
    public Integer getDoneCount() { return doneCount; }
    /** 设置done Count */
    public void setDoneCount(Integer doneCount) { this.doneCount = doneCount; }
    /** 获取context Recall */
    public Double getContextRecall() { return contextRecall; }
    /** 设置context Recall */
    public void setContextRecall(Double contextRecall) { this.contextRecall = contextRecall; }
    /** 获取context Precision */
    public Double getContextPrecision() { return contextPrecision; }
    /** 设置context Precision */
    public void setContextPrecision(Double contextPrecision) { this.contextPrecision = contextPrecision; }
    /** 获取faithfulness */
    public Double getFaithfulness() { return faithfulness; }
    /** 设置faithfulness */
    public void setFaithfulness(Double faithfulness) { this.faithfulness = faithfulness; }
    /** 获取answer Relevancy */
    public Double getAnswerRelevancy() { return answerRelevancy; }
    /** 设置answer Relevancy */
    public void setAnswerRelevancy(Double answerRelevancy) { this.answerRelevancy = answerRelevancy; }
    /** 获取overall Score */
    public Double getOverallScore() { return overallScore; }
    /** 设置overall Score */
    public void setOverallScore(Double overallScore) { this.overallScore = overallScore; }
    /** 获取fail Reason */
    public String getFailReason() { return failReason; }
    /** 设置fail Reason */
    public void setFailReason(String failReason) { this.failReason = failReason; }
    /** 获取duration Ms */
    public Long getDurationMs() { return durationMs; }
    /** 设置duration Ms */
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    /** 获取创建时间 */
    public String getCreateTime() { return createTime; }
    /** 设置创建时间 */
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    /** 获取detail Json */
    public String getDetailJson() { return detailJson; }
    /** 设置detail Json */
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
    /** getCases 方法 */
    public List<Map<String, Object>> getCases() { return cases; }
    /** 设置cases */
    public void setCases(List<Map<String, Object>> cases) { this.cases = cases; }
    /** 获取capability Tags */
    public List<String> getCapabilityTags() { return capabilityTags; }
    /** 设置capability Tags */
    public void setCapabilityTags(List<String> capabilityTags) { this.capabilityTags = capabilityTags; }
    /** 获取avg Latency Ms */
    public Long getAvgLatencyMs() { return avgLatencyMs; }
    /** 设置avg Latency Ms */
    public void setAvgLatencyMs(Long avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }
}
