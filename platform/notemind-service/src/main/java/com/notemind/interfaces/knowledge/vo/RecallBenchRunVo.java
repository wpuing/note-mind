package com.notemind.interfaces.knowledge.vo;

import com.notemind.client.ai.RetrievalTestResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 召回调试运行结果视图对象。
 */
public class RecallBenchRunVo {
    private String id;
    private String question;
    private String knowledgeBaseId;
    private String knowledgeBaseName;
    private Integer strategyCount;
    private List<String> strategyNames = new ArrayList<>();
    private List<String> documentIds = new ArrayList<>();
    private Long elapsedMs;
    private String createTime;
    private List<RetrievalTestResult> results = new ArrayList<>();

    /** 获取主键 ID */
    public String getId() {
        return id;
    }

    /** 设置主键 ID */
    public void setId(String id) {
        this.id = id;
    }

    /** 获取问题 */
    public String getQuestion() {
        return question;
    }

    /** 设置问题 */
    public void setQuestion(String question) {
        this.question = question;
    }

    /** 获取知识库 ID */
    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    /** 设置知识库 ID */
    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    /** 获取knowledge Base Name */
    public String getKnowledgeBaseName() {
        return knowledgeBaseName;
    }

    /** 设置knowledge Base Name */
    public void setKnowledgeBaseName(String knowledgeBaseName) {
        this.knowledgeBaseName = knowledgeBaseName;
    }

    /** 获取strategy Count */
    public Integer getStrategyCount() {
        return strategyCount;
    }

    /** 设置strategy Count */
    public void setStrategyCount(Integer strategyCount) {
        this.strategyCount = strategyCount;
    }

    /** 获取strategy Names */
    public List<String> getStrategyNames() {
        return strategyNames;
    }

    /** 设置strategy Names */
    public void setStrategyNames(List<String> strategyNames) {
        this.strategyNames = strategyNames;
    }

    /** 获取document Ids */
    public List<String> getDocumentIds() {
        return documentIds;
    }

    /** 设置document Ids */
    public void setDocumentIds(List<String> documentIds) {
        this.documentIds = documentIds;
    }

    /** 获取elapsed Ms */
    public Long getElapsedMs() {
        return elapsedMs;
    }

    /** 设置elapsed Ms */
    public void setElapsedMs(Long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    /** 获取创建时间 */
    public String getCreateTime() {
        return createTime;
    }

    /** 设置创建时间 */
    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    /** 获取results */
    public List<RetrievalTestResult> getResults() {
        return results;
    }

    /** 设置results */
    public void setResults(List<RetrievalTestResult> results) {
        this.results = results;
    }
}
