package com.notemind.interfaces.agent.vo;

/**
 * Agent 单步执行视图对象（节点、入出参、耗时）。
 */
public class AgentStepVo {
    private String id;
    private String agentRunId;
    private Integer stepIndex;
    private String nodeName;
    private String title;
    private String inputJson;
    private String outputJson;
    private String status;
    private Integer latencyMs;
    private String createTime;

    /** 获取主键 ID */
    public String getId() { return id; }
    /** 设置主键 ID */
    public void setId(String id) { this.id = id; }
    /** 获取Agent 运行 ID */
    public String getAgentRunId() { return agentRunId; }
    /** 设置Agent 运行 ID */
    public void setAgentRunId(String agentRunId) { this.agentRunId = agentRunId; }
    /** 获取步骤序号 */
    public Integer getStepIndex() { return stepIndex; }
    /** 设置步骤序号 */
    public void setStepIndex(Integer stepIndex) { this.stepIndex = stepIndex; }
    /** 获取节点名称 */
    public String getNodeName() { return nodeName; }
    /** 设置节点名称 */
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    /** 获取标题 */
    public String getTitle() { return title; }
    /** 设置标题 */
    public void setTitle(String title) { this.title = title; }
    /** 获取输入 JSON */
    public String getInputJson() { return inputJson; }
    /** 设置输入 JSON */
    public void setInputJson(String inputJson) { this.inputJson = inputJson; }
    /** 获取输出 JSON */
    public String getOutputJson() { return outputJson; }
    /** 设置输出 JSON */
    public void setOutputJson(String outputJson) { this.outputJson = outputJson; }
    /** 获取状态 */
    public String getStatus() { return status; }
    /** 设置状态 */
    public void setStatus(String status) { this.status = status; }
    /** 获取耗时毫秒 */
    public Integer getLatencyMs() { return latencyMs; }
    /** 设置耗时毫秒 */
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    /** 获取创建时间 */
    public String getCreateTime() { return createTime; }
    /** 设置创建时间 */
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
