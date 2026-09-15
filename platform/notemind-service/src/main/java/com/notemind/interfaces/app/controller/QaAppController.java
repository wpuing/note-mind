package com.notemind.interfaces.app.controller;

import com.notemind.application.service.app.QaAppAsvc;
import com.notemind.common.dto.IdsBatchDeleteRequest;
import com.notemind.common.result.PageResult;
import com.notemind.common.result.Result;
import com.notemind.interfaces.app.vo.QaAppSaveRequest;
import com.notemind.interfaces.app.vo.QaAppVo;
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
 * 问答应用管理接口：分页 CRUD、启用列表与批量删除。
 */
@RestController
@RequestMapping("/api/v1/qa-apps")
public class QaAppController {

    /** 问答应用应用服务。 */
    private final QaAppAsvc qaAppAsvc;

    /**
     * 构造注入问答应用服务。
     *
     * @param qaAppAsvc 问答应用服务
     */
    public QaAppController(QaAppAsvc qaAppAsvc) {
        this.qaAppAsvc = qaAppAsvc;
    }

    /**
     * 分页查询问答应用。
     *
     * @param name     名称筛选，可选
     * @param enabled  启用状态，可选
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<QaAppVo>> page(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 管理端分页列表
        return Result.ok(qaAppAsvc.page(name, enabled, page, pageSize));
    }

    /**
     * 列出已启用应用，供用户端选择问答应用。
     *
     * @return 已启用应用列表
     */
    @GetMapping
    public Result<List<QaAppVo>> list() {
        // 仅返回启用中的应用供下拉选择
        return Result.ok(qaAppAsvc.listEnabled());
    }

    /**
     * 查询应用详情。
     *
     * @param id 应用 ID
     * @return 应用 VO
     */
    @GetMapping("/{id}")
    public Result<QaAppVo> detail(@PathVariable String id) {
        // 按主键加载应用配置
        return Result.ok(qaAppAsvc.getById(id));
    }

    /**
     * 新建问答应用。
     *
     * @param body 保存请求
     * @return 新建后的应用 VO
     */
    @PostMapping
    public Result<QaAppVo> create(@RequestBody QaAppSaveRequest body) {
        // 持久化 KB/策略/Agentic 等挂载配置
        return Result.ok(qaAppAsvc.create(body));
    }

    /**
     * 更新问答应用。
     *
     * @param id   应用 ID
     * @param body 保存请求
     * @return 更新后的应用 VO
     */
    @PutMapping("/{id}")
    public Result<QaAppVo> update(@PathVariable String id, @RequestBody QaAppSaveRequest body) {
        // 按 ID 覆盖更新应用配置
        return Result.ok(qaAppAsvc.update(id, body));
    }

    /**
     * 删除单个应用。
     *
     * @param id 应用 ID
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        // 删除应用定义
        qaAppAsvc.delete(id);
        return Result.ok(null);
    }

    /**
     * 批量删除应用。
     *
     * @param request 含 ids 的批量删除请求
     * @return 实际删除条数
     */
    @PostMapping("/batch-delete")
    public Result<Map<String, Integer>> batchDelete(@RequestBody IdsBatchDeleteRequest request) {
        // 空请求时传 null，由服务层做空列表保护
        int n = qaAppAsvc.batchDelete(request == null ? null : request.getIds());
        return Result.ok(Map.of("deleted", n));
    }
}
