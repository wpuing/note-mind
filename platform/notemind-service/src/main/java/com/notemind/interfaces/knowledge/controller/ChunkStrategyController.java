package com.notemind.interfaces.knowledge.controller;

import com.notemind.application.service.knowledge.ChunkStrategyAsvc;
import com.notemind.common.result.PageResult;
import com.notemind.common.result.Result;
import com.notemind.interfaces.knowledge.vo.ChunkStrategyBatchDeleteRequest;
import com.notemind.interfaces.knowledge.vo.ChunkStrategySaveRequest;
import com.notemind.interfaces.knowledge.vo.ChunkStrategyVo;
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
 * 切分策略接口：启用列表、分页 CRUD 与批量删除。
 */
@RestController
@RequestMapping("/api/v1/knowledge/chunk-strategies")
public class ChunkStrategyController {

    /** 切分策略应用服务。 */
    private final ChunkStrategyAsvc chunkStrategyAsvc;

    /**
     * 构造注入切分策略服务。
     *
     * @param chunkStrategyAsvc 切分策略应用服务
     */
    public ChunkStrategyController(ChunkStrategyAsvc chunkStrategyAsvc) {
        this.chunkStrategyAsvc = chunkStrategyAsvc;
    }

    /**
     * 列出已启用的切分策略（下拉用）。
     *
     * @return 已启用策略列表
     */
    @GetMapping
    public Result<List<ChunkStrategyVo>> list() {
        // 仅返回启用中的切分策略
        return Result.ok(chunkStrategyAsvc.listEnabled());
    }

    /**
     * 分页查询切分策略。
     *
     * @param name         名称筛选，可选
     * @param strategyType 策略类型，可选
     * @param page         页码
     * @param pageSize     每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<ChunkStrategyVo>> page(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String strategyType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int pageSize) {
        // 管理端按名称与类型分页
        return Result.ok(chunkStrategyAsvc.page(name, strategyType, page, pageSize));
    }

    /**
     * 查询切分策略详情。
     *
     * @param id 策略 ID
     * @return 策略 VO
     */
    @GetMapping("/{id}")
    public Result<ChunkStrategyVo> detail(@PathVariable String id) {
        // 按主键加载策略参数
        return Result.ok(chunkStrategyAsvc.getById(id));
    }

    /**
     * 新建切分策略。
     *
     * @param request 保存请求
     * @return 新建后的策略 VO
     */
    @PostMapping
    public Result<ChunkStrategyVo> create(@RequestBody ChunkStrategySaveRequest request) {
        // 持久化切分策略定义
        return Result.ok(chunkStrategyAsvc.create(request));
    }

    /**
     * 更新切分策略。
     *
     * @param id      策略 ID
     * @param request 保存请求
     * @return 更新后的策略 VO
     */
    @PutMapping("/{id}")
    public Result<ChunkStrategyVo> update(
            @PathVariable String id, @RequestBody ChunkStrategySaveRequest request) {
        // 按 ID 覆盖更新策略
        return Result.ok(chunkStrategyAsvc.update(id, request));
    }

    /**
     * 删除单个切分策略。
     *
     * @param id 策略 ID
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        // 删除策略（默认策略需服务层校验）
        chunkStrategyAsvc.delete(id);
        return Result.ok(null);
    }

    /**
     * 批量删除切分策略。
     *
     * @param request 含 ids 的批量删除请求
     * @return 实际删除条数
     */
    @PostMapping("/batch-delete")
    public Result<Map<String, Integer>> batchDelete(@RequestBody ChunkStrategyBatchDeleteRequest request) {
        // 空请求时传 null，由服务层做空列表保护
        int n = chunkStrategyAsvc.batchDelete(request == null ? null : request.getIds());
        return Result.ok(Map.of("deleted", n));
    }
}
