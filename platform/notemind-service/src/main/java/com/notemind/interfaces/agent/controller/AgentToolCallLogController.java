package com.notemind.interfaces.agent.controller;

import com.notemind.application.service.agent.AgentToolCallLogAsvc;
import com.notemind.common.dto.IdsBatchDeleteRequest;
import com.notemind.common.result.PageResult;
import com.notemind.common.result.Result;
import com.notemind.interfaces.agent.vo.AgentToolCallLogVo;
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
 * 工具调用日志接口：筛选分页、详情、单删与批量删除。
 */
@RestController
@RequestMapping("/api/v1/agent-tool-call-logs")
public class AgentToolCallLogController {

    /** 工具调用日志应用服务。 */
    private final AgentToolCallLogAsvc agentToolCallLogAsvc;

    /**
     * 构造注入工具调用日志服务。
     *
     * @param agentToolCallLogAsvc 工具调用日志应用服务
     */
    public AgentToolCallLogController(AgentToolCallLogAsvc agentToolCallLogAsvc) {
        this.agentToolCallLogAsvc = agentToolCallLogAsvc;
    }

    /**
     * 分页查询工具调用日志。
     *
     * @param toolCode   工具编码，可选
     * @param status     调用结果状态，可选
     * @param agentRunId 关联 Agent 运行 ID，可选
     * @param page       页码
     * @param pageSize   每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<AgentToolCallLogVo>> page(
            @RequestParam(required = false) String toolCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String agentRunId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 按工具/状态/运行 ID 组合筛选分页
        return Result.ok(agentToolCallLogAsvc.page(toolCode, status, agentRunId, page, pageSize));
    }

    /**
     * 查询单条调用日志详情。
     *
     * @param id 日志 ID
     * @return 日志详情 VO
     */
    @GetMapping("/{id}")
    public Result<AgentToolCallLogVo> detail(@PathVariable String id) {
        // 按主键加载完整入参/出参
        return Result.ok(agentToolCallLogAsvc.getById(id));
    }

    /**
     * 删除单条调用日志。
     *
     * @param id 日志 ID
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        // 物理删除该条调用记录
        agentToolCallLogAsvc.delete(id);
        return Result.ok(null);
    }

    /**
     * 批量删除工具调用日志。
     *
     * @param request 含 ids 的批量删除请求
     * @return 实际删除条数
     */
    @PostMapping("/batch-delete")
    public Result<Map<String, Integer>> batchDelete(@RequestBody IdsBatchDeleteRequest request) {
        // 空请求时传 null，由服务层做空列表保护
        int n = agentToolCallLogAsvc.batchDelete(request == null ? null : request.getIds());
        return Result.ok(Map.of("deleted", n));
    }
}
