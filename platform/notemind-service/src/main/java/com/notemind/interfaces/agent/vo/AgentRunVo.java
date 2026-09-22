package com.notemind.interfaces.agent.vo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 运行记录视图对象，含回答、步骤与引用来源。
 */
public class AgentRunVo {
    private String id;
    private String runType;
    private String knowledgeBaseId;
    private String question;
    private String finalAnswer;
    private String conclusionLabel;
    private String status;
    private String errorMessage;
    private Integer totalLatencyMs;
    private Integer stepCount;
    private Integer retrievalRounds;
    private Integer rewriteRounds;
    private String sourcesJson;
    private String createTime;
    private List<AgentStepVo> steps = new ArrayList<>();
    private List<Map<String, Object>> sources = new ArrayList<>();

    /** 获取主键 ID */
    public String getId() { return id; }
    /** 设置主键 ID */
    public void setId(String id) { this.id = id; }
    /** 获取运行类型 */
    public String getRunType() { return runType; }
    /** 设置运行类型 */
    public void setRunType(String runType) { this.runType = runType; }
    /** 获取知识库 ID */
    public String getKnowledgeBaseId() { return knowledgeBaseId; }
    /** 设置知识库 ID */
    public void setKnowledgeBaseId(String knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    /** 获取问题 */
    public String getQuestion() { return question; }
    /** 设置问题 */
    public void setQuestion(String question) { this.question = question; }
    /** 获取最终回答 */
    public String getFinalAnswer() { return finalAnswer; }
    /** 设置最终回答 */
    public void setFinalAnswer(String finalAnswer) { this.finalAnswer = finalAnswer; }
    /** 获取结论标签 */
    public String getConclusionLabel() { return conclusionLabel; }
    /** 设置结论标签 */
    public void setConclusionLabel(String conclusionLabel) { this.conclusionLabel = conclusionLabel; }
    /** 获取状态 */
    public String getStatus() { return status; }
    /** 设置状态 */
    public void setStatus(String status) { this.status = status; }
    /** 获取错误信息 */
    public String getErrorMessage() { return errorMessage; }
    /** 设置错误信息 */
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    /** 获取总耗时毫秒 */
    public Integer getTotalLatencyMs() { return totalLatencyMs; }
    /** 设置总耗时毫秒 */
    public void setTotalLatencyMs(Integer totalLatencyMs) { this.totalLatencyMs = totalLatencyMs; }
    /** 获取步骤数 */
    public Integer getStepCount() { return stepCount; }
    /** 设置步骤数 */
    public void setStepCount(Integer stepCount) { this.stepCount = stepCount; }
    /** 获取检索轮次 */
    public Integer getRetrievalRounds() { return retrievalRounds; }
    /** 设置检索轮次 */
    public void setRetrievalRounds(Integer retrievalRounds) { this.retrievalRounds = retrievalRounds; }
    /** 获取改写轮次 */
    public Integer getRewriteRounds() { return rewriteRounds; }
    /** 设置改写轮次 */
    public void setRewriteRounds(Integer rewriteRounds) { this.rewriteRounds = rewriteRounds; }
    /** 获取引用来源 JSON */
    public String getSourcesJson() { return sourcesJson; }
    /** 设置引用来源 JSON */
    public void setSourcesJson(String sourcesJson) { this.sourcesJson = sourcesJson; }
    /** 获取创建时间 */
    public String getCreateTime() { return createTime; }
    /** 设置创建时间 */
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    /** 获取步骤列表 */
    public List<AgentStepVo> getSteps() { return steps; }
    /** 设置步骤列表 */
    public void setSteps(List<AgentStepVo> steps) { this.steps = steps == null ? new ArrayList<>() : steps; }
    /** getSources 方法 */
    public List<Map<String, Object>> getSources() { return sources; }
    /** 设置引用来源 */
    public void setSources(List<Map<String, Object>> sources) {
        this.sources = sources == null ? new ArrayList<>() : sources;
    }
}
