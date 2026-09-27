package com.notemind.interfaces.model.controller;

import com.notemind.application.service.model.AiModelConfigAsvc;
import com.notemind.common.dto.IdsBatchDeleteRequest;
import com.notemind.common.result.PageResult;
import com.notemind.common.result.Result;
import com.notemind.interfaces.model.vo.AiModelConfigSaveRequest;
import com.notemind.interfaces.model.vo.AiModelConfigTestResult;
import com.notemind.interfaces.model.vo.AiModelConfigVo;
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
 * AI 模型配置接口：列表/分页 CRUD、连通性测试与批量删除（密钥脱敏）。
 */
@RestController
@RequestMapping("/api/v1/ai-models")
public class AiModelConfigController {

    /** 模型配置应用服务。 */
    private final AiModelConfigAsvc aiModelConfigAsvc;

    /**
     * 构造注入模型配置服务。
     *
     * @param aiModelConfigAsvc 模型配置应用服务
     */
    public AiModelConfigController(AiModelConfigAsvc aiModelConfigAsvc) {
        this.aiModelConfigAsvc = aiModelConfigAsvc;
    }

    /**
     * 列出已启用的模型配置（下拉用）。
     *
     * @return 已启用模型列表（密钥脱敏）
     */
    @GetMapping
    public Result<List<AiModelConfigVo>> list() {
        // 仅返回启用中的模型供其它模块选择
        return Result.ok(aiModelConfigAsvc.listEnabled());
    }

    /**
     * 分页查询模型配置。
     *
     * @param name      名称筛选，可选
     * @param modelType 模型类型，可选
     * @param page      页码
     * @param pageSize  每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<AiModelConfigVo>> page(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String modelType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int pageSize) {
        // 管理端按名称与类型分页
        return Result.ok(aiModelConfigAsvc.page(name, modelType, page, pageSize));
    }

    /**
     * 查询模型配置详情。
     *
     * @param id 配置 ID
     * @return 模型配置 VO（密钥脱敏）
     */
    @GetMapping("/{id}")
    public Result<AiModelConfigVo> detail(@PathVariable String id) {
        // 按主键加载并脱敏展示
        return Result.ok(aiModelConfigAsvc.getById(id));
    }

    /**
     * 新建模型配置。
     *
     * @param body 保存请求
     * @return 新建后的 VO
     */
    @PostMapping
    public Result<AiModelConfigVo> create(@RequestBody AiModelConfigSaveRequest body) {
        // 密钥 AES-GCM 加密后落库
        return Result.ok(aiModelConfigAsvc.create(body));
    }

    /**
     * 更新模型配置。
     *
     * @param id   配置 ID
     * @param body 保存请求
     * @return 更新后的 VO
     */
    @PutMapping("/{id}")
    public Result<AiModelConfigVo> update(
            @PathVariable String id, @RequestBody AiModelConfigSaveRequest body) {
        // 按 ID 更新；空密钥表示保留原密钥
        return Result.ok(aiModelConfigAsvc.update(id, body));
    }

    /**
     * 删除单个模型配置。
     *
     * @param id 配置 ID
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        // 删除模型配置记录
        aiModelConfigAsvc.delete(id);
        return Result.ok(null);
    }

    /**
     * 批量删除模型配置。
     *
     * @param request 含 ids 的批量删除请求
     * @return 实际删除条数
     */
    @PostMapping("/batch-delete")
    public Result<Map<String, Integer>> batchDelete(@RequestBody IdsBatchDeleteRequest request) {
        // 空请求时传 null，由服务层做空列表保护
        int n = aiModelConfigAsvc.batchDelete(request == null ? null : request.getIds());
        return Result.ok(Map.of("deleted", n));
    }

    /**
     * 测试模型连通性。
     *
     * @param id 配置 ID
     * @return 连通性测试结果
     */
    @PostMapping("/{id}/test")
    public Result<AiModelConfigTestResult> test(@PathVariable String id) {
        // 使用解密后的密钥发起一次探测调用
        return Result.ok(aiModelConfigAsvc.test(id));
    }
}
