package com.notemind.interfaces.chat.controller;

import com.notemind.application.service.chat.ChatAsvc;
import com.notemind.common.result.Result;
import com.notemind.interfaces.chat.vo.ChatFeedbackRequest;
import com.notemind.interfaces.chat.vo.ChatMessageVo;
import com.notemind.interfaces.chat.vo.ChatSessionCreateRequest;
import com.notemind.interfaces.chat.vo.ChatSessionVo;
import com.notemind.interfaces.chat.vo.ChatStreamRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 用户问答接口：会话 CRUD、消息列表、赞踩反馈与 SSE 流式回答。
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    /** 问答应用服务，负责会话归属校验与 SSE 编排。 */
    private final ChatAsvc chatAsvc;

    /**
     * 构造注入问答服务。
     *
     * @param chatAsvc 问答应用服务
     */
    public ChatController(ChatAsvc chatAsvc) {
        this.chatAsvc = chatAsvc;
    }

    /**
     * 新建聊天会话。
     *
     * @param body 创建请求（应用 ID 等）
     * @return 新建会话 VO
     */
    @PostMapping("/sessions")
    public Result<ChatSessionVo> createSession(@RequestBody ChatSessionCreateRequest body) {
        // 为当前用户在指定应用下创建会话
        return Result.ok(chatAsvc.createSession(body));
    }

    /**
     * 列出当前用户在某应用下的会话。
     *
     * @param appId 问答应用 ID
     * @return 会话列表
     */
    @GetMapping("/sessions")
    public Result<List<ChatSessionVo>> listSessions(@RequestParam String appId) {
        // 按应用过滤且校验归属后的会话列表
        return Result.ok(chatAsvc.listSessions(appId));
    }

    /**
     * 列出会话内消息。
     *
     * @param id 会话 ID
     * @return 消息列表
     */
    @GetMapping("/sessions/{id}/messages")
    public Result<List<ChatMessageVo>> messages(@PathVariable String id) {
        // 校验会话归属后返回历史消息
        return Result.ok(chatAsvc.listMessages(id));
    }

    /**
     * 对助手消息提交赞/踩反馈。
     *
     * @param id   消息 ID
     * @param body 反馈请求
     * @return 空成功响应
     */
    @PutMapping("/messages/{id}/feedback")
    public Result<Void> feedback(@PathVariable String id, @RequestBody ChatFeedbackRequest body) {
        // 写入赞踩并可供后续评测沉淀
        chatAsvc.feedback(id, body);
        return Result.ok(null);
    }

    /**
     * SSE 流式问答，返回回答片段与 sources。
     *
     * @param body 流式问答请求
     * @return SseEmitter
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatStreamRequest body) {
        // 走直线或 Agentic 链路并向客户端推送 SSE
        return chatAsvc.stream(body);
    }
}
