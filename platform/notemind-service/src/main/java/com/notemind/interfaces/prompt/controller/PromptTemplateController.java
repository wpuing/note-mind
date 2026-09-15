package com.notemind.interfaces.prompt.controller;

import com.notemind.application.service.prompt.PromptTemplateAsvc;
import com.notemind.common.dto.IdsBatchDeleteRequest;
import com.notemind.common.result.PageResult;
import com.notemind.common.result.Result;
import com.notemind.interfaces.prompt.vo.PromptTemplateSaveRequest;
import com.notemind.interfaces.prompt.vo.PromptTemplateVo;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Prompt 模板接口：按名称/场景分页、CRUD 与批量删除。
 */
@RestController
@RequestMapping("/api/v1/prompt-templates")
public class PromptTemplateController {

    /** Prompt 模板应用服务。 */
    private final PromptTemplateAsvc promptTemplateAsvc;

    /**
     * 构造注入 Prompt 模板服务。
     *
     * @param promptTemplateAsvc Prompt 模板应用服务
     */
    public PromptTemplateController(PromptTemplateAsvc promptTemplateAsvc) {
        this.promptTemplateAsvc = promptTemplateAsvc;
    }

    /**
     * 分页查询 Prompt 模板。
     *
     * @param name     名称筛选，可选
     * @param scenario 场景筛选，可选
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<PromptTemplateVo>> page(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String scenario,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int pageSize) {
        // 按名称与场景筛选模板
        return Result.ok(promptTemplateAsvc.page(name, scenario, page, pageSize));
    }

    /**
     * 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板 VO
     */
    @GetMapping("/{id}")
    public Result<PromptTemplateVo> detail(@PathVariable String id) {
        // 按主键加载模板内容与占位变量
        return Result.ok(promptTemplateAsvc.getById(id));
    }

    /**
     * 新建 Prompt 模板。
     *
     * @param body 保存请求
     * @return 新建后的模板 VO
     */
    @PostMapping
    public Result<PromptTemplateVo> create(@RequestBody PromptTemplateSaveRequest body) {
        // 持久化编码/场景/内容（含 {var}）
        return Result.ok(promptTemplateAsvc.create(body));
    }

    /**
     * 更新 Prompt 模板。
     *
     * @param id   模板 ID
     * @param body 保存请求
     * @return 更新后的模板 VO
     */
    @PutMapping("/{id}")
    public Result<PromptTemplateVo> update(
            @PathVariable String id, @RequestBody PromptTemplateSaveRequest body) {
        // 按 ID 覆盖更新模板
        return Result.ok(promptTemplateAsvc.update(id, body));
    }

    /**
     * 删除单个模板。
     *
     * @param id 模板 ID
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        // 删除模板记录
        promptTemplateAsvc.delete(id);
        return Result.ok(null);
    }

    /**
     * 批量删除模板。
     *
     * @param request 含 ids 的批量删除请求
     * @return 实际删除条数
     */
    @PostMapping("/batch-delete")
    public Result<Map<String, Integer>> batchDelete(@RequestBody IdsBatchDeleteRequest request) {
        // 空请求时传 null，由服务层做空列表保护
        int n = promptTemplateAsvc.batchDelete(request == null ? null : request.getIds());
        return Result.ok(Map.of("deleted", n));
    }
}
