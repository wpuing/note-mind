package com.notemind.interfaces.agent.vo;

/**
 * 工具调用统计视图对象。
 */
public class AgentToolStatVo {
    private String toolCode;
    private String toolName;
    private long callCount;
    private long successCount;
    private long failCount;
    private Double avgLatencyMs;

    /** 获取工具编码 */
    public String getToolCode() { return toolCode; }
    /** 设置工具编码 */
    public void setToolCode(String toolCode) { this.toolCode = toolCode; }
    /** 获取工具名称 */
    public String getToolName() { return toolName; }
    /** 设置工具名称 */
    public void setToolName(String toolName) { this.toolName = toolName; }
    /** 获取调用次数 */
    public long getCallCount() { return callCount; }
    /** 设置调用次数 */
    public void setCallCount(long callCount) { this.callCount = callCount; }
    /** 获取成功次数 */
    public long getSuccessCount() { return successCount; }
    /** 设置成功次数 */
    public void setSuccessCount(long successCount) { this.successCount = successCount; }
    /** 获取失败次数 */
    public long getFailCount() { return failCount; }
    /** 设置失败次数 */
    public void setFailCount(long failCount) { this.failCount = failCount; }
    /** 获取avg Latency Ms */
    public Double getAvgLatencyMs() { return avgLatencyMs; }
    /** 设置avg Latency Ms */
    public void setAvgLatencyMs(Double avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }
}
