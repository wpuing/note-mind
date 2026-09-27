package com.notemind.interfaces.chat.controller;

import com.notemind.application.service.chat.ChatLogAsvc;
import com.notemind.common.result.PageResult;
import com.notemind.common.result.Result;
import com.notemind.interfaces.chat.vo.ChatFeedbackItemVo;
import com.notemind.interfaces.chat.vo.ChatFeedbackStatVo;
import com.notemind.interfaces.chat.vo.ChatMessageVo;
import com.notemind.interfaces.chat.vo.ChatSessionLogVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 对话日志与反馈管理接口：满意度统计、会话/反馈分页、沉淀标记。
 */
@RestController
@RequestMapping("/api/v1/chat-logs")
public class ChatLogController {

    /** 对话日志应用服务。 */
    private final ChatLogAsvc chatLogAsvc;

    /**
     * 构造注入对话日志服务。
     *
     * @param chatLogAsvc 对话日志应用服务
     */
    public ChatLogController(ChatLogAsvc chatLogAsvc) {
        this.chatLogAsvc = chatLogAsvc;
    }

    /**
     * 获取赞踩满意度统计。
     *
     * @return 反馈统计 VO
     */
    @GetMapping("/stats")
    public Result<ChatFeedbackStatVo> stats() {
        // 聚合赞/踩数量与满意度
        return Result.ok(chatLogAsvc.stats());
    }

    /**
     * 分页查询会话日志。
     *
     * @param title    标题筛选，可选
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping("/sessions/page")
    public Result<PageResult<ChatSessionLogVo>> sessions(
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 管理端按标题筛选会话日志
        return Result.ok(chatLogAsvc.pageSessions(title, page, pageSize));
    }

    /**
     * 查询某会话的全部消息（管理端详情）。
     *
     * @param id 会话 ID
     * @return 消息列表
     */
    @GetMapping("/sessions/{id}/messages")
    public Result<List<ChatMessageVo>> sessionMessages(@PathVariable String id) {
        // 加载会话消息供日志详情页展示
        return Result.ok(chatLogAsvc.sessionMessages(id));
    }

    /**
     * 分页查询反馈列表。
     *
     * @param feedback 反馈类型，可选
     * @param settled  是否已沉淀，可选
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping("/feedback/page")
    public Result<PageResult<ChatFeedbackItemVo>> feedback(
            @RequestParam(required = false) String feedback,
            @RequestParam(required = false) Integer settled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 按赞踩类型与沉淀状态筛选反馈
        return Result.ok(chatLogAsvc.pageFeedback(feedback, settled, page, pageSize));
    }

    /**
     * 更新反馈是否已沉淀到评测集。
     *
     * @param id   反馈/消息 ID
     * @param body 含 settled 字段的请求体
     * @return 空成功响应
     */
    @PutMapping("/feedback/{id}/settled")
    public Result<Void> settled(@PathVariable String id, @RequestBody Map<String, Integer> body) {
        Integer settled = body == null ? null : body.get("settled");
        // 未传 settled 时默认标记为未沉淀(0)
        chatLogAsvc.updateSettled(id, settled == null ? 0 : settled);
        return Result.ok(null);
    }
}
