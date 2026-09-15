package com.notemind.interfaces.knowledge.controller;

import com.notemind.application.service.knowledge.RecallBenchAsvc;
import com.notemind.common.dto.IdsBatchDeleteRequest;
import com.notemind.common.result.PageResult;
import com.notemind.common.result.Result;
import com.notemind.interfaces.knowledge.vo.RecallBenchCompareRequest;
import com.notemind.interfaces.knowledge.vo.RecallBenchRunVo;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 召回调试台接口：多策略并排对比、历史记录分页与删除。
 */
@RestController
@RequestMapping("/api/v1/knowledge/recall-bench")
public class RecallBenchController {

    /** 召回调试应用服务。 */
    private final RecallBenchAsvc recallBenchAsvc;

    /**
     * 构造注入召回调试服务。
     *
     * @param recallBenchAsvc 召回调试应用服务
     */
    public RecallBenchController(RecallBenchAsvc recallBenchAsvc) {
        this.recallBenchAsvc = recallBenchAsvc;
    }

    /**
     * 对同一 query 用最多 4 套策略并排对比召回。
     *
     * @param body 对比请求
     * @return 对比运行结果 VO
     */
    @PostMapping("/compare")
    public Result<RecallBenchRunVo> compare(@RequestBody RecallBenchCompareRequest body) {
        // 并行/串行调用多策略检索并落库历史
        return Result.ok(recallBenchAsvc.compare(body));
    }

    /**
     * 分页查询历史调试记录。
     *
     * @param knowledgeBaseId 知识库 ID，可选
     * @param page            页码
     * @param pageSize        每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<RecallBenchRunVo>> page(
            @RequestParam(required = false) String knowledgeBaseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 按知识库筛选历史对比记录
        return Result.ok(recallBenchAsvc.page(knowledgeBaseId, page, pageSize));
    }

    /**
     * 查询单次对比详情。
     *
     * @param id 运行记录 ID
     * @return 对比结果 VO
     */
    @GetMapping("/{id}")
    public Result<RecallBenchRunVo> detail(@PathVariable String id) {
        // 回看历史对比的阶段耗时与命中
        return Result.ok(recallBenchAsvc.get(id));
    }

    /**
     * 删除单条历史记录。
     *
     * @param id 运行记录 ID
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        // 删除该次对比历史
        recallBenchAsvc.delete(id);
        return Result.ok(null);
    }

    /**
     * 批量删除历史记录。
     *
     * @param request 含 ids 的批量删除请求
     * @return 实际删除条数
     */
    @PostMapping("/batch-delete")
    public Result<Map<String, Integer>> batchDelete(@RequestBody IdsBatchDeleteRequest request) {
        // 空请求时传 null，由服务层做空列表保护
        int n = recallBenchAsvc.batchDelete(request == null ? null : request.getIds());
        return Result.ok(Map.of("deleted", n));
    }
}
