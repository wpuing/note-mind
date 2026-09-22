package com.notemind.interfaces.config.controller;

import com.notemind.application.service.knowledge.ChunkStrategyAsvc;
import com.notemind.application.service.knowledge.RetrievalStrategyAsvc;
import com.notemind.common.result.Result;
import com.notemind.interfaces.knowledge.vo.ChunkStrategyVo;
import com.notemind.interfaces.knowledge.vo.RetrievalStrategyVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 策略兼容入口：提供启用中的切分/检索策略列表（旧路径）。
 */
@RestController
@RequestMapping("/api/v1/strategies")
public class StrategyController {

    /** 切分策略应用服务。 */
    private final ChunkStrategyAsvc chunkStrategyAsvc;
    /** 检索策略应用服务。 */
    private final RetrievalStrategyAsvc retrievalStrategyAsvc;

    /**
     * 构造注入切分与检索策略服务。
     *
     * @param chunkStrategyAsvc     切分策略服务
     * @param retrievalStrategyAsvc 检索策略服务
     */
    public StrategyController(
            ChunkStrategyAsvc chunkStrategyAsvc, RetrievalStrategyAsvc retrievalStrategyAsvc) {
        this.chunkStrategyAsvc = chunkStrategyAsvc;
        this.retrievalStrategyAsvc = retrievalStrategyAsvc;
    }

    /**
     * 兼容旧入口：启用切分策略列表。
     *
     * @return 已启用切分策略列表
     */
    @GetMapping("/chunk")
    public Result<List<ChunkStrategyVo>> chunkStrategies() {
        // 供下拉选择启用中的切分策略
        return Result.ok(chunkStrategyAsvc.listEnabled());
    }

    /**
     * 兼容旧入口：启用检索策略列表。
     *
     * @return 已启用检索策略列表
     */
    @GetMapping("/retrieval")
    public Result<List<RetrievalStrategyVo>> retrievalStrategies() {
        // 供下拉选择启用中的检索策略
        return Result.ok(retrievalStrategyAsvc.listEnabled());
    }
}
