package com.notemind.interfaces.knowledge.controller;

import com.notemind.application.service.knowledge.KnowledgeBaseAsvc;
import com.notemind.common.result.PageResult;
import com.notemind.common.result.Result;
import com.notemind.interfaces.knowledge.vo.KnowledgeBaseBatchDeleteRequest;
import com.notemind.interfaces.knowledge.vo.KnowledgeBaseSaveRequest;
import com.notemind.interfaces.knowledge.vo.KnowledgeBaseVo;
import com.notemind.interfaces.knowledge.vo.KnowledgeDocumentVectorVo;
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
 * 知识库管理接口：启用列表、分页 CRUD、向量状态下钻与批量删除。
 */
@RestController
@RequestMapping("/api/v1/knowledge/bases")
public class KnowledgeBaseController {

    /** 知识库应用服务。 */
    private final KnowledgeBaseAsvc knowledgeBaseAsvc;

    /**
     * 构造注入知识库服务。
     *
     * @param knowledgeBaseAsvc 知识库应用服务
     */
    public KnowledgeBaseController(KnowledgeBaseAsvc knowledgeBaseAsvc) {
        this.knowledgeBaseAsvc = knowledgeBaseAsvc;
    }

    /**
     * 启用中的知识库（文档上传下拉）。
     *
     * @return 已启用知识库列表
     */
    @GetMapping
    public Result<List<KnowledgeBaseVo>> list() {
        // 仅返回启用中的知识库供绑定文档
        return Result.ok(knowledgeBaseAsvc.listEnabled());
    }

    /**
     * 管理端分页列表。
     *
     * @param name     名称筛选，可选
     * @param status   状态筛选，可选
     * @param category 分类筛选，可选
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<KnowledgeBaseVo>> page(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int pageSize) {
        // 按名称/状态/分类组合筛选分页
        return Result.ok(knowledgeBaseAsvc.page(name, status, category, page, pageSize));
    }

    /**
     * 查询知识库详情。
     *
     * @param id 知识库 ID
     * @return 知识库 VO
     */
    @GetMapping("/{id}")
    public Result<KnowledgeBaseVo> detail(@PathVariable String id) {
        // 按主键加载知识库及绑定策略
        return Result.ok(knowledgeBaseAsvc.getById(id));
    }

    /**
     * 向量状态下钻：该库下各文档的向量汇总。
     *
     * @param id 知识库 ID
     * @return 文档向量状态列表
     */
    @GetMapping("/{id}/vector-documents")
    public Result<List<KnowledgeDocumentVectorVo>> vectorDocuments(@PathVariable String id) {
        // 汇总库内文档向量化进度供下钻展示
        return Result.ok(knowledgeBaseAsvc.listDocumentVectorStatus(id));
    }

    /**
     * 新建知识库。
     *
     * @param request 保存请求
     * @return 新建后的知识库 VO
     */
    @PostMapping
    public Result<KnowledgeBaseVo> create(@RequestBody KnowledgeBaseSaveRequest request) {
        // 持久化知识库及切分/检索策略绑定
        return Result.ok(knowledgeBaseAsvc.create(request));
    }

    /**
     * 更新知识库。
     *
     * @param id      知识库 ID
     * @param request 保存请求
     * @return 更新后的知识库 VO
     */
    @PutMapping("/{id}")
    public Result<KnowledgeBaseVo> update(
            @PathVariable String id, @RequestBody KnowledgeBaseSaveRequest request) {
        // 按 ID 覆盖更新知识库配置
        return Result.ok(knowledgeBaseAsvc.update(id, request));
    }

    /**
     * 删除知识库（含清向量，依赖 AI 引擎）。
     *
     * @param id 知识库 ID
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        // 删库并清理关联文档/向量
        knowledgeBaseAsvc.delete(id);
        return Result.ok(null);
    }

    /**
     * 批量删除知识库。
     *
     * @param request 含 ids 的批量删除请求
     * @return 实际删除条数
     */
    @PostMapping("/batch-delete")
    public Result<Map<String, Integer>> batchDelete(@RequestBody KnowledgeBaseBatchDeleteRequest request) {
        // 空请求时传 null，由服务层做空列表保护
        int n = knowledgeBaseAsvc.batchDelete(request == null ? null : request.getIds());
        return Result.ok(Map.of("deleted", n));
    }
}
