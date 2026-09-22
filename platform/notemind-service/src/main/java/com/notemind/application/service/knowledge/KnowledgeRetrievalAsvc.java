package com.notemind.application.service.knowledge;

import com.notemind.client.ai.AiEngineClient;
import com.notemind.client.ai.RetrievalStrategyParams;
import com.notemind.client.ai.RetrievalTestResult;
import com.notemind.application.service.system.SystemConfigAsvc;
import com.notemind.interfaces.knowledge.vo.KnowledgeRetrievalTestRequest;
import com.notemind.interfaces.knowledge.vo.RetrievalStrategyVo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 知识库检索测试应用服务：解析策略参数并调用 AI 引擎 retrievalTest。
 */

@Service
public class KnowledgeRetrievalAsvc {

    private final KnowledgeBaseAsvc knowledgeBaseAsvc;
    private final RetrievalStrategyAsvc retrievalStrategyAsvc;
    private final AiEngineClient aiEngineClient;
    private final SystemConfigAsvc systemConfig;

    /**
     * 注入知识库、检索策略、AI 客户端与系统配置。
     *
     * @param knowledgeBaseAsvc     知识库应用服务
     * @param retrievalStrategyAsvc 检索策略应用服务
     * @param aiEngineClient        AI 引擎客户端
     * @param systemConfig          系统配置服务
     */
    public KnowledgeRetrievalAsvc(
            KnowledgeBaseAsvc knowledgeBaseAsvc,
            RetrievalStrategyAsvc retrievalStrategyAsvc,
            AiEngineClient aiEngineClient,
            SystemConfigAsvc systemConfig) {
        this.knowledgeBaseAsvc = knowledgeBaseAsvc;
        this.retrievalStrategyAsvc = retrievalStrategyAsvc;
        this.aiEngineClient = aiEngineClient;
        this.systemConfig = systemConfig;
    }

    /**
     * 执行一次检索测试。
     *
     * @param req 检索测试请求
     * @return AI 引擎返回的检索结果
     */
    public RetrievalTestResult test(KnowledgeRetrievalTestRequest req) {
        // 问题必填
        if (req == null || req.getQuestion() == null || req.getQuestion().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question required");
        }
        // 解析默认/指定知识库 ID
        String kbId = systemConfig.resolveKnowledgeBaseId(req.getKnowledgeBaseId());
        // 校验知识库存在
        knowledgeBaseAsvc.getById(kbId);
        RetrievalStrategyVo strategy;
        // 指定了策略 ID 则按 ID 取，否则按知识库解析默认策略
        if (req.getRetrievalStrategyId() != null && !req.getRetrievalStrategyId().isBlank()) {
            // 按策略 ID 查询
            strategy = retrievalStrategyAsvc.getById(req.getRetrievalStrategyId().trim());
        } else {
            // 按知识库绑定策略或全局默认
            strategy = retrievalStrategyAsvc.resolveForKnowledgeBase(kbId);
        }
        RetrievalStrategyParams params = toParams(strategy);
        // 尝试执行
        try {
            // 调用 AI 引擎执行检索测试
            return aiEngineClient.retrievalTest(
                    req.getQuestion().trim(),
                    kbId,
                    params,
                    req.getChatHistory(),
                    req.getDocumentIds());
        } catch (Exception ex) {
            // AI 调用失败映射为 502
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "retrieval failed: " + ex.getMessage());
        }
    }

    /**
     * 将检索策略 VO 转为 AI 引擎参数（含 TopK 钳制）。
     *
     * @param vo 策略 VO，可为 null（用系统默认 TopK）
     * @return 检索参数
     */
    public RetrievalStrategyParams toParams(RetrievalStrategyVo vo) {
        RetrievalStrategyParams p = new RetrievalStrategyParams();
        // 无策略时仅填系统默认 TopK
        if (vo == null) {
            // 读取系统默认 TopK
            int top = systemConfig.defaultTopK();
            p.setTopK(top);
            p.setRerankTopN(top);
            p.setVectorTopK(top);
            return p;
        }
        p.setStrategyId(vo.getId());
        p.setStrategyName(vo.getName());
        p.setEnableVector(on(vo.getEnableVector()));
        p.setEnableBm25(on(vo.getEnableBm25()));
        p.setEnableRrf(on(vo.getEnableRrf()));
        p.setEnableRerank(on(vo.getEnableRerank()));
        p.setEnableRewrite(on(vo.getEnableRewrite()));
        p.setEnableParentFill(on(vo.getEnableParentFill()));
        // 钳制 TopK 到系统上限
        int top = systemConfig.clampTopK(vo.getTopK());
        p.setTopK(top);
        // 钳制重排 TopN
        p.setRerankTopN(systemConfig.clampTopK(vo.getRerankTopN() == null ? top : vo.getRerankTopN()));
        // 钳制向量召回 TopK
        p.setVectorTopK(systemConfig.clampTopK(vo.getVectorTopK() == null ? top : vo.getVectorTopK()));
        p.setBm25TopK(vo.getBm25TopK() == null ? 0 : Math.max(0, vo.getBm25TopK()));
        p.setRrfK(vo.getRrfK() == null ? 60 : vo.getRrfK());
        p.setCosineThreshold(vo.getCosineThreshold());
        p.setRerankThreshold(vo.getRerankThreshold());
        p.setRewriteMode(vo.getRewriteMode() == null ? "multi_query" : vo.getRewriteMode());
        p.setRewriteCount(vo.getRewriteCount() == null ? 3 : vo.getRewriteCount());
        return p;
    }

    /**
     * 整型开关转 boolean（非 0 为开）。
     *
     * @param v 整型开关，可为 null
     * @return 是否开启
     */
    private static boolean on(Integer v) {
        return v != null && v != 0;
    }
}

