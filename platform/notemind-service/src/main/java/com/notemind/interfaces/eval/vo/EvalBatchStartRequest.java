package com.notemind.interfaces.eval.vo;

/**
 * 启动批量评测请求体。
 */
public class EvalBatchStartRequest {
    private String datasetId;
    private String retrievalStrategyId;

    /** 获取评测集 ID */
    public String getDatasetId() { return datasetId; }
    /** 设置评测集 ID */
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    /** 获取检索策略 ID */
    public String getRetrievalStrategyId() { return retrievalStrategyId; }
    /** 设置检索策略 ID */
    public void setRetrievalStrategyId(String retrievalStrategyId) { this.retrievalStrategyId = retrievalStrategyId; }
}
