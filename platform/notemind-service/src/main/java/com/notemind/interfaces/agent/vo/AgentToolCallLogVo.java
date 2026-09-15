package com.notemind.interfaces.agent.vo;

/**
 * 工具调用日志视图对象。
 */
public class AgentToolCallLogVo {
    private String id;
    private String toolId;
    private String toolCode;
    private String toolName;
    private String agentRunId;
    private String inputJson;
    private String outputJson;
    private String status;
    private Integer latencyMs;
    private String errorMessage;
    private String createTime;

    /** 获取主键 ID */
    public String getId() { return id; }
    /** 设置主键 ID */
    public void setId(String id) { this.id = id; }
    /** 获取tool Id */
    public String getToolId() { return toolId; }
    /** 设置tool Id */
    public void setToolId(String toolId) { this.toolId = toolId; }
    /** 获取工具编码 */
    public String getToolCode() { return toolCode; }
    /** 设置工具编码 */
    public void setToolCode(String toolCode) { this.toolCode = toolCode; }
    /** 获取工具名称 */
    public String getToolName() { return toolName; }
    /** 设置工具名称 */
    public void setToolName(String toolName) { this.toolName = toolName; }
    /** 获取Agent 运行 ID */
    public String getAgentRunId() { return agentRunId; }
    /** 设置Agent 运行 ID */
    public void setAgentRunId(String agentRunId) { this.agentRunId = agentRunId; }
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
    /** 获取错误信息 */
    public String getErrorMessage() { return errorMessage; }
    /** 设置错误信息 */
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    /** 获取创建时间 */
    public String getCreateTime() { return createTime; }
    /** 设置创建时间 */
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
