package com.notemind.interfaces.knowledge.controller;

import com.notemind.application.service.knowledge.KnowledgeGraphAsvc;
import com.notemind.common.result.Result;
import com.notemind.interfaces.knowledge.vo.KgMultiHopRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识图谱测试接口（Phase C 本地 JSON 引擎）。
 */
@RestController
@RequestMapping("/api/v1/knowledge/kg")
public class KnowledgeGraphController {

    private final KnowledgeGraphAsvc knowledgeGraphAsvc;

    public KnowledgeGraphController(KnowledgeGraphAsvc knowledgeGraphAsvc) {
        this.knowledgeGraphAsvc = knowledgeGraphAsvc;
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(knowledgeGraphAsvc.overview());
    }

    @GetMapping("/search")
    public Result<List<Map<String, Object>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(knowledgeGraphAsvc.search(q, limit));
    }

    @PostMapping("/multi-hop")
    public Result<Map<String, Object>> multiHop(@RequestBody(required = false) KgMultiHopRequest body) {
        String entityId = body == null ? "" : body.getEntityId();
        int requested = body != null && body.getDepth() != null ? body.getDepth() : 2;
        int depth = Math.max(1, Math.min(requested, 3));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entityId", entityId);
        data.put("depth", depth);
        data.put("paths", knowledgeGraphAsvc.multiHop(entityId, depth));
        return Result.ok(data);
    }
}
