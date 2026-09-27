package com.notemind.client.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agentic RAG 运行结果，对应 POST /api/v1/ai/agent/run 响应。
 * <p>
 * 含最终回答、Judge 结论、引用来源、相关性/接地性标志、轮次统计与节点时间线。
 */
public class AgentGraphRunResult {
    /** 最终生成的回答正文。 */
    private String answer;
    /** Judge 或流程结论摘要。 */
    private String conclusion;
    /** 引用来源列表（含片段 ID、分数等）。 */
    private List<Map<String, Object>> sources = new ArrayList<>();
    /** 检索结果是否判定为相关。 */
    private boolean relevant;
    /** 回答是否接地（忠实于上下文）。 */
    private boolean grounded;
    /** 实际检索轮次数。 */
    private int retrievalRounds;
    /** 实际改写轮次数。 */
    private int rewriteRounds;
    /** LangGraph 各节点执行步骤时间线。 */
    private List<Step> steps = new ArrayList<>();

    /**
     * 获取回答正文。
     *
     * @return 回答文本
     */
    public String getAnswer() { return answer; }

    /**
     * 设置回答正文。
     *
     * @param answer 回答文本
     */
    public void setAnswer(String answer) { this.answer = answer; }

    /**
     * 获取流程结论。
     *
     * @return 结论文本
     */
    public String getConclusion() { return conclusion; }

    /**
     * 设置流程结论。
     *
     * @param conclusion 结论文本
     */
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }

    /**
     * 获取引用来源。
     *
     * @return sources 列表
     */
    public List<Map<String, Object>> getSources() { return sources; }

    /**
     * 设置引用来源。
     *
     * @param sources sources 列表
     */
    public void setSources(List<Map<String, Object>> sources) { this.sources = sources; }

    /**
     * 是否相关。
     *
     * @return true 表示 Judge 认为相关
     */
    public boolean isRelevant() { return relevant; }

    /**
     * 设置相关性标志。
     *
     * @param relevant 是否相关
     */
    public void setRelevant(boolean relevant) { this.relevant = relevant; }

    /**
     * 是否接地/忠实。
     *
     * @return true 表示 grounded
     */
    public boolean isGrounded() { return grounded; }

    /**
     * 设置接地标志。
     *
     * @param grounded 是否接地
     */
    public void setGrounded(boolean grounded) { this.grounded = grounded; }

    /**
     * 获取检索轮次。
     *
     * @return 检索次数
     */
    public int getRetrievalRounds() { return retrievalRounds; }

    /**
     * 设置检索轮次。
     *
     * @param retrievalRounds 检索次数
     */
    public void setRetrievalRounds(int retrievalRounds) { this.retrievalRounds = retrievalRounds; }

    /**
     * 获取改写轮次。
     *
     * @return 改写次数
     */
    public int getRewriteRounds() { return rewriteRounds; }

    /**
     * 设置改写轮次。
     *
     * @param rewriteRounds 改写次数
     */
    public void setRewriteRounds(int rewriteRounds) { this.rewriteRounds = rewriteRounds; }

    /**
     * 获取执行步骤时间线。
     *
     * @return 步骤列表
     */
    public List<Step> getSteps() { return steps; }

    /**
     * 设置执行步骤时间线。
     *
     * @param steps 步骤列表
     */
    public void setSteps(List<Step> steps) { this.steps = steps; }

    /**
     * 单个图节点执行步骤，供前端时间线与 t_agent_step 落库。
     */
    public static class Step {
        /** 节点技术名，如 retrieve / rewrite / generate。 */
        private String nodeName;
        /** 展示用标题。 */
        private String title;
        /** 节点输入快照。 */
        private Map<String, Object> input;
        /** 节点输出快照。 */
        private Map<String, Object> output;
        /** 步骤状态，如 ok / skip / fail。 */
        private String status;
        /** 节点耗时（毫秒）。 */
        private Integer latencyMs;

        /**
         * 获取节点名。
         *
         * @return 节点名
         */
        public String getNodeName() { return nodeName; }

        /**
         * 设置节点名。
         *
         * @param nodeName 节点名
         */
        public void setNodeName(String nodeName) { this.nodeName = nodeName; }

        /**
         * 获取展示标题。
         *
         * @return 标题
         */
        public String getTitle() { return title; }

        /**
         * 设置展示标题。
         *
         * @param title 标题
         */
        public void setTitle(String title) { this.title = title; }

        /**
         * 获取节点输入。
         *
         * @return 输入 Map
         */
        public Map<String, Object> getInput() { return input; }

        /**
         * 设置节点输入。
         *
         * @param input 输入 Map
         */
        public void setInput(Map<String, Object> input) { this.input = input; }

        /**
         * 获取节点输出。
         *
         * @return 输出 Map
         */
        public Map<String, Object> getOutput() { return output; }

        /**
         * 设置节点输出。
         *
         * @param output 输出 Map
         */
        public void setOutput(Map<String, Object> output) { this.output = output; }

        /**
         * 获取步骤状态。
         *
         * @return 状态字符串
         */
        public String getStatus() { return status; }

        /**
         * 设置步骤状态。
         *
         * @param status 状态字符串
         */
        public void setStatus(String status) { this.status = status; }

        /**
         * 获取耗时毫秒。
         *
         * @return 耗时，可为 null
         */
        public Integer getLatencyMs() { return latencyMs; }

        /**
         * 设置耗时毫秒。
         *
         * @param latencyMs 耗时
         */
        public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    }
}
