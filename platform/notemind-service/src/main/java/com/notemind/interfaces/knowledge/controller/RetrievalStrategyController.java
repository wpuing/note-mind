package com.notemind.interfaces.knowledge.controller;

import com.notemind.application.service.knowledge.RetrievalStrategyAsvc;
import com.notemind.common.result.PageResult;
import com.notemind.common.result.Result;
import com.notemind.interfaces.knowledge.vo.RetrievalStrategyBatchDeleteRequest;
import com.notemind.interfaces.knowledge.vo.RetrievalStrategySaveRequest;
import com.notemind.interfaces.knowledge.vo.RetrievalStrategyVo;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 检索策略接口：启用列表、分页 CRUD 与批量删除。
 */
@RestController
@RequestMapping("/api/v1/knowledge/retrieval-strategies")
public class RetrievalStrategyController {

    /** 检索策略应用服务。 */
    private final RetrievalStrategyAsvc retrievalStrategyAsvc;

    /**
     * 构造注入检索策略服务。
     *
     * @param retrievalStrategyAsvc 检索策略应用服务
     */
    public RetrievalStrategyController(RetrievalStrategyAsvc retrievalStrategyAsvc) {
        this.retrievalStrategyAsvc = retrievalStrategyAsvc;
    }

    /**
     * 列出已启用的检索策略（下拉用）。
     *
     * @return 已启用策略列表
     */
    @GetMapping
    public Result<List<RetrievalStrategyVo>> list() {
        // 仅返回启用中的检索策略
        return Result.ok(retrievalStrategyAsvc.listEnabled());
    }

    /**
     * 分页查询检索策略。
     *
     * @param name     名称筛选，可选
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<RetrievalStrategyVo>> page(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int pageSize) {
        // 管理端按名称分页
        return Result.ok(retrievalStrategyAsvc.page(name, page, pageSize));
    }

    /**
     * 查询检索策略详情。
     *
     * @param id 策略 ID
     * @return 策略 VO
     */
    @GetMapping("/{id}")
    public Result<RetrievalStrategyVo> detail(@PathVariable String id) {
        // 按主键加载七开关等参数
        return Result.ok(retrievalStrategyAsvc.getById(id));
    }

    /**
     * 新建检索策略。
     *
     * @param request 保存请求
     * @return 新建后的策略 VO
     */
    @PostMapping
    public Result<RetrievalStrategyVo> create(@RequestBody RetrievalStrategySaveRequest request) {
        // 持久化检索策略定义
        return Result.ok(retrievalStrategyAsvc.create(request));
    }

    /**
     * 更新检索策略。
     *
     * @param id      策略 ID
     * @param request 保存请求
     * @return 更新后的策略 VO
     */
    @PutMapping("/{id}")
    public Result<RetrievalStrategyVo> update(
            @PathVariable String id, @RequestBody RetrievalStrategySaveRequest request) {
        // 按 ID 覆盖更新策略
        return Result.ok(retrievalStrategyAsvc.update(id, request));
    }

    /**
     * 删除单个检索策略。
     *
     * @param id 策略 ID
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        // 删除策略（默认策略需服务层校验）
        retrievalStrategyAsvc.delete(id);
        return Result.ok(null);
    }

    /**
     * 批量删除检索策略。
     *
     * @param request 含 ids 的批量删除请求
     * @return 实际删除条数
     */
    @PostMapping("/batch-delete")
    public Result<Map<String, Integer>> batchDelete(
            @RequestBody RetrievalStrategyBatchDeleteRequest request) {
        // 空请求时传 null，由服务层做空列表保护
        int n = retrievalStrategyAsvc.batchDelete(request == null ? null : request.getIds());
        return Result.ok(Map.of("deleted", n));
    }
}
