package com.notemind.interfaces.knowledge.controller;

import com.notemind.application.service.knowledge.KnowledgeRetrievalAsvc;
import com.notemind.client.ai.RetrievalTestResult;
import com.notemind.common.result.Result;
import com.notemind.interfaces.knowledge.vo.KnowledgeRetrievalTestRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 检索测试接口：按知识库/策略发起一次检索并返回阶段耗时与命中。
 */
@RestController
@RequestMapping("/api/v1/knowledge/retrieval")
public class KnowledgeRetrievalController {

    /** 检索测试应用服务。 */
    private final KnowledgeRetrievalAsvc knowledgeRetrievalAsvc;

    /**
     * 构造注入检索测试服务。
     *
     * @param knowledgeRetrievalAsvc 检索应用服务
     */
    public KnowledgeRetrievalController(KnowledgeRetrievalAsvc knowledgeRetrievalAsvc) {
        this.knowledgeRetrievalAsvc = knowledgeRetrievalAsvc;
    }

    /**
     * 执行检索测试。
     *
     * @param body 测试请求（知识库、可选文档/策略、query）
     * @return 含阶段耗时与命中明细的结果
     */
    @PostMapping("/test")
    public Result<RetrievalTestResult> test(@RequestBody KnowledgeRetrievalTestRequest body) {
        // 经 Java 编排调用 AI 引擎完成检索测试
        return Result.ok(knowledgeRetrievalAsvc.test(body));
    }
}
