package com.notemind.interfaces.dashboard.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作台概览视图对象（KPI 与图表数据）。
 */
public class DashboardOverviewVo {
    private int days;
    private Summary summary = new Summary();
    private List<NamedCount> hotKnowledgeBases = new ArrayList<>();
    private List<HotQuestion> hotQuestions = new ArrayList<>();
    private List<DailyUsage> usageTrend = new ArrayList<>();
    private List<NamedCount> modelCalls = new ArrayList<>();
    private List<NamedCount> modelTypeCalls = new ArrayList<>();

    /** 获取统计天数 */
    public int getDays() {
        return days;
    }

    /** 设置统计天数 */
    public void setDays(int days) {
        this.days = days;
    }

    /** 获取 KPI 汇总 */
    public Summary getSummary() {
        return summary;
    }

    /** 设置 KPI 汇总 */
    public void setSummary(Summary summary) {
        this.summary = summary;
    }

    /** 获取热门知识库 */
    public List<NamedCount> getHotKnowledgeBases() {
        return hotKnowledgeBases;
    }

    /** 设置热门知识库 */
    public void setHotKnowledgeBases(List<NamedCount> hotKnowledgeBases) {
        this.hotKnowledgeBases = hotKnowledgeBases;
    }

    /** 获取高频提问 */
    public List<HotQuestion> getHotQuestions() {
        return hotQuestions;
    }

    /** 设置高频提问 */
    public void setHotQuestions(List<HotQuestion> hotQuestions) {
        this.hotQuestions = hotQuestions;
    }

    /** 获取使用趋势 */
    public List<DailyUsage> getUsageTrend() {
        return usageTrend;
    }

    /** 设置使用趋势 */
    public void setUsageTrend(List<DailyUsage> usageTrend) {
        this.usageTrend = usageTrend;
    }

    /** 获取模型调用统计 */
    public List<NamedCount> getModelCalls() {
        return modelCalls;
    }

    /** 设置模型调用统计 */
    public void setModelCalls(List<NamedCount> modelCalls) {
        this.modelCalls = modelCalls;
    }

    /** 获取模型类型调用占比 */
    public List<NamedCount> getModelTypeCalls() {
        return modelTypeCalls;
    }

    /** 设置模型类型调用占比 */
    public void setModelTypeCalls(List<NamedCount> modelTypeCalls) {
        this.modelTypeCalls = modelTypeCalls;
    }

    /**
     * 工作台 KPI 汇总。
     */
    public static class Summary {
        private long knowledgeBaseCount;
        private long documentCount;
        private long questionCount;
        private long agentRunCount;
        private long toolCallCount;
        private long chatSessionCount;

        /** 获取知识库数量 */
        public long getKnowledgeBaseCount() {
            return knowledgeBaseCount;
        }

        /** 设置知识库数量 */
        public void setKnowledgeBaseCount(long knowledgeBaseCount) {
            this.knowledgeBaseCount = knowledgeBaseCount;
        }

        /** 获取文档数量 */
        public long getDocumentCount() {
            return documentCount;
        }

        /** 设置文档数量 */
        public void setDocumentCount(long documentCount) {
            this.documentCount = documentCount;
        }

        /** 获取提问次数 */
        public long getQuestionCount() {
            return questionCount;
        }

        /** 设置提问次数 */
        public void setQuestionCount(long questionCount) {
            this.questionCount = questionCount;
        }

        /** 获取 Agent 运行次数 */
        public long getAgentRunCount() {
            return agentRunCount;
        }

        /** 设置 Agent 运行次数 */
        public void setAgentRunCount(long agentRunCount) {
            this.agentRunCount = agentRunCount;
        }

        /** 获取工具调用次数 */
        public long getToolCallCount() {
            return toolCallCount;
        }

        /** 设置工具调用次数 */
        public void setToolCallCount(long toolCallCount) {
            this.toolCallCount = toolCallCount;
        }

        /** 获取会话数量 */
        public long getChatSessionCount() {
            return chatSessionCount;
        }

        /** 设置会话数量 */
        public void setChatSessionCount(long chatSessionCount) {
            this.chatSessionCount = chatSessionCount;
        }
    }

    /**
     * 带名称的计数项（热门库、模型调用等）。
     */
    public static class NamedCount {
        private String id;
        private String name;
        private String extra;
        private long count;

        /**
         * 无参构造。
         */
        public NamedCount() {}

        /**
         * 构造命名计数项。
         *
         * @param id    ID
         * @param name  名称
         * @param count 次数
         */
        public NamedCount(String id, String name, long count) {
            this.id = id;
            this.name = name;
            this.count = count;
        }

        /**
         * 构造命名计数项（含扩展字段）。
         *
         * @param id    ID
         * @param name  名称
         * @param extra 扩展信息
         * @param count 次数
         */
        public NamedCount(String id, String name, String extra, long count) {
            this.id = id;
            this.name = name;
            this.extra = extra;
            this.count = count;
        }

        /** 获取主键 ID */
        public String getId() {
            return id;
        }

        /** 设置主键 ID */
        public void setId(String id) {
            this.id = id;
        }

        /** 获取名称 */
        public String getName() {
            return name;
        }

        /** 设置名称 */
        public void setName(String name) {
            this.name = name;
        }

        /** 获取扩展信息 */
        public String getExtra() {
            return extra;
        }

        /** 设置扩展信息 */
        public void setExtra(String extra) {
            this.extra = extra;
        }

        /** 获取次数 */
        public long getCount() {
            return count;
        }

        /** 设置次数 */
        public void setCount(long count) {
            this.count = count;
        }
    }

    /**
     * 高频提问项。
     */
    public static class HotQuestion {
        private String question;
        private long count;
        private String lastAskTime;

        /** 获取问题 */
        public String getQuestion() {
            return question;
        }

        /** 设置问题 */
        public void setQuestion(String question) {
            this.question = question;
        }

        /** 获取提问次数 */
        public long getCount() {
            return count;
        }

        /** 设置提问次数 */
        public void setCount(long count) {
            this.count = count;
        }

        /** 获取最近提问时间 */
        public String getLastAskTime() {
            return lastAskTime;
        }

        /** 设置最近提问时间 */
        public void setLastAskTime(String lastAskTime) {
            this.lastAskTime = lastAskTime;
        }
    }

    /**
     * 按日使用趋势点。
     */
    public static class DailyUsage {
        private String date;
        private long questions;
        private long agentRuns;
        private long toolCalls;
        private long chatCalls;

        /** 获取日期 */
        public String getDate() {
            return date;
        }

        /** 设置日期 */
        public void setDate(String date) {
            this.date = date;
        }

        /** 获取提问次数 */
        public long getQuestions() {
            return questions;
        }

        /** 设置提问次数 */
        public void setQuestions(long questions) {
            this.questions = questions;
        }

        /** 获取 Agent 运行次数 */
        public long getAgentRuns() {
            return agentRuns;
        }

        /** 设置 Agent 运行次数 */
        public void setAgentRuns(long agentRuns) {
            this.agentRuns = agentRuns;
        }

        /** 获取工具调用次数 */
        public long getToolCalls() {
            return toolCalls;
        }

        /** 设置工具调用次数 */
        public void setToolCalls(long toolCalls) {
            this.toolCalls = toolCalls;
        }

        /** 获取聊天调用次数 */
        public long getChatCalls() {
            return chatCalls;
        }

        /** 设置聊天调用次数 */
        public void setChatCalls(long chatCalls) {
            this.chatCalls = chatCalls;
        }
    }
}
