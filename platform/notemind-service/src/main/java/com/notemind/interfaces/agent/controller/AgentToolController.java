package com.notemind.interfaces.agent.controller;

import com.notemind.application.service.agent.AgentToolAsvc;
import com.notemind.common.dto.IdsBatchDeleteRequest;
import com.notemind.common.result.PageResult;
import com.notemind.common.result.Result;
import com.notemind.interfaces.agent.vo.AgentToolSaveRequest;
import com.notemind.interfaces.agent.vo.AgentToolStatVo;
import com.notemind.interfaces.agent.vo.AgentToolVo;
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
 * 工具中心接口：CRUD、启用开关、OpenAI 工具定义预览与调用统计。
 */
@RestController
@RequestMapping("/api/v1/agent-tools")
public class AgentToolController {

    /** 工具中心应用服务。 */
    private final AgentToolAsvc agentToolAsvc;

    /**
     * 构造注入工具中心服务。
     *
     * @param agentToolAsvc 工具应用服务
     */
    public AgentToolController(AgentToolAsvc agentToolAsvc) {
        this.agentToolAsvc = agentToolAsvc;
    }

    /**
     * 分页查询工具定义。
     *
     * @param name     名称模糊筛选，可选
     * @param enabled  启用状态，可选
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<AgentToolVo>> page(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int pageSize) {
        // 按名称与启用状态分页查询工具
        return Result.ok(agentToolAsvc.page(name, enabled, page, pageSize));
    }

    /**
     * 获取已启用且已实现工具的 OpenAI 工具定义 JSON。
     *
     * @param codes 工具编码列表（逗号分隔），可选
     * @return 含 json 字段的 Map
     */
    @GetMapping("/openai-definitions")
    public Result<Map<String, String>> openaiDefinitions(
            @RequestParam(required = false) String codes) {
        // 组装模型侧可见的 tools 定义预览
        return Result.ok(Map.of("json", agentToolAsvc.openaiDefinitionsJson(codes)));
    }

    /**
     * 查询工具调用统计。
     *
     * @param codes 工具编码列表（逗号分隔），可选
     * @return 统计列表
     */
    @GetMapping("/stats")
    public Result<List<AgentToolStatVo>> stats(
            @RequestParam(required = false) String codes) {
        // 聚合各工具调用次数与成功率等
        return Result.ok(agentToolAsvc.stats(codes));
    }

    /**
     * 查询工具详情。
     *
     * @param id 工具 ID
     * @return 工具 VO
     */
    @GetMapping("/{id}")
    public Result<AgentToolVo> detail(@PathVariable String id) {
        // 按主键加载工具定义
        return Result.ok(agentToolAsvc.getById(id));
    }

    /**
     * 新建工具定义。
     *
     * @param body 保存请求
     * @return 新建后的工具 VO
     */
    @PostMapping
    public Result<AgentToolVo> create(@RequestBody AgentToolSaveRequest body) {
        // 持久化新工具登记信息
        return Result.ok(agentToolAsvc.create(body));
    }

    /**
     * 更新工具定义。
     *
     * @param id   工具 ID
     * @param body 保存请求
     * @return 更新后的工具 VO
     */
    @PutMapping("/{id}")
    public Result<AgentToolVo> update(
            @PathVariable String id, @RequestBody AgentToolSaveRequest body) {
        // 按 ID 覆盖更新工具字段
        return Result.ok(agentToolAsvc.update(id, body));
    }

    /**
     * 更新工具启用状态。
     *
     * @param id   工具 ID
     * @param body 含 enabled 字段的请求体
     * @return 更新后的工具 VO
     */
    @PutMapping("/{id}/enabled")
    public Result<AgentToolVo> updateEnabled(
            @PathVariable String id, @RequestBody Map<String, Integer> body) {
        Integer enabled = body == null ? null : body.get("enabled");
        // 缺省或未传 enabled 时视为关闭
        if (enabled == null) {
            enabled = 0;
        }
        // 仅切换启用开关，不改其它字段
        return Result.ok(agentToolAsvc.updateEnabled(id, enabled));
    }

    /**
     * 删除单个工具。
     *
     * @param id 工具 ID
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        // 删除工具定义记录
        agentToolAsvc.delete(id);
        return Result.ok(null);
    }

    /**
     * 批量删除工具。
     *
     * @param request 含 ids 的批量删除请求
     * @return 实际删除条数
     */
    @PostMapping("/batch-delete")
    public Result<Map<String, Integer>> batchDelete(@RequestBody IdsBatchDeleteRequest request) {
        // 空请求时传 null，由服务层做空列表保护
        int n = agentToolAsvc.batchDelete(request == null ? null : request.getIds());
        return Result.ok(Map.of("deleted", n));
    }
}
