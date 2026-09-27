package com.notemind.interfaces.agent.controller;

import com.notemind.application.service.agent.AgentRunAsvc;
import com.notemind.common.dto.IdsBatchDeleteRequest;
import com.notemind.common.result.PageResult;
import com.notemind.common.result.Result;
import com.notemind.interfaces.agent.vo.AgentRunExecuteRequest;
import com.notemind.interfaces.agent.vo.AgentRunVo;
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
 * Agent 运行记录接口：分页、详情、执行、单删与批量删除。
 */
@RestController
@RequestMapping("/api/v1/agent-runs")
public class AgentRunController {

    /** Agent 运行应用服务，编排 LangGraph 执行并落库步骤。 */
    private final AgentRunAsvc agentRunAsvc;

    /**
     * 构造注入 Agent 运行服务。
     *
     * @param agentRunAsvc Agent 运行应用服务
     */
    public AgentRunController(AgentRunAsvc agentRunAsvc) {
        this.agentRunAsvc = agentRunAsvc;
    }

    /**
     * 分页查询 Agent 运行记录。
     *
     * @param status   运行状态筛选，可选
     * @param page     页码，从 1 起
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<AgentRunVo>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 按状态筛选并分页返回运行记录
        return Result.ok(agentRunAsvc.page(status, page, pageSize));
    }

    /**
     * 查询单次运行详情（含时间线步骤）。
     *
     * @param id 运行记录 ID
     * @return 运行详情 VO
     */
    @GetMapping("/{id}")
    public Result<AgentRunVo> detail(@PathVariable String id) {
        // 加载运行主记录及关联步骤
        return Result.ok(agentRunAsvc.getById(id));
    }

    /**
     * 触发一次 Agentic 执行并返回结果。
     *
     * @param body 执行请求（知识库、问题等）
     * @return 执行结果 VO
     */
    @PostMapping("/execute")
    public Result<AgentRunVo> execute(@RequestBody AgentRunExecuteRequest body) {
        // 调用 AI 引擎 LangGraph 并落库运行/步骤
        return Result.ok(agentRunAsvc.execute(body));
    }

    /**
     * 删除单条运行记录。
     *
     * @param id 运行记录 ID
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        // 删除运行主记录及关联步骤
        agentRunAsvc.delete(id);
        return Result.ok(null);
    }

    /**
     * 批量删除运行记录。
     *
     * @param request 含 ids 的批量删除请求
     * @return 实际删除条数
     */
    @PostMapping("/batch-delete")
    public Result<Map<String, Integer>> batchDelete(@RequestBody IdsBatchDeleteRequest request) {
        // 空请求时传 null，由服务层做空列表保护
        int n = agentRunAsvc.batchDelete(request == null ? null : request.getIds());
        return Result.ok(Map.of("deleted", n));
    }
}
