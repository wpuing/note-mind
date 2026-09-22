package com.notemind.interfaces.knowledge.controller;

import com.notemind.application.service.knowledge.KnowledgeSegmentAsvc;
import com.notemind.common.result.PageResult;
import com.notemind.common.result.Result;
import com.notemind.interfaces.knowledge.vo.KnowledgeSegmentBatchDeleteRequest;
import com.notemind.interfaces.knowledge.vo.KnowledgeSegmentUpdateRequest;
import com.notemind.interfaces.knowledge.vo.KnowledgeSegmentVo;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 片段管理接口：多条件分页、编辑、单删与批量删除。
 */
@RestController
@RequestMapping("/api/v1/knowledge/segments")
public class KnowledgeSegmentController {

    /** 知识片段应用服务。 */
    private final KnowledgeSegmentAsvc knowledgeSegmentAsvc;

    /**
     * 构造注入片段服务。
     *
     * @param knowledgeSegmentAsvc 片段应用服务
     */
    public KnowledgeSegmentController(KnowledgeSegmentAsvc knowledgeSegmentAsvc) {
        this.knowledgeSegmentAsvc = knowledgeSegmentAsvc;
    }

    /**
     * 分页查询片段。
     *
     * @param knowledgeBaseId 知识库 ID，可选
     * @param documentId      文档 ID，可选
     * @param segmentType     片段类型，可选
     * @param vectorStatus    向量状态，可选
     * @param keyword         内容关键词，可选
     * @param page            页码
     * @param pageSize        每页条数
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<KnowledgeSegmentVo>> list(
            @RequestParam(value = "knowledgeBaseId", required = false) String knowledgeBaseId,
            @RequestParam(value = "documentId", required = false) String documentId,
            @RequestParam(value = "segmentType", required = false) String segmentType,
            @RequestParam(value = "vectorStatus", required = false) String vectorStatus,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "8") int pageSize) {
        // 片段管理页多条件分页查询
        return Result.ok(knowledgeSegmentAsvc.page(
                knowledgeBaseId, documentId, segmentType, vectorStatus, keyword, page, pageSize));
    }

    /**
     * 查询片段详情。
     *
     * @param id 片段 ID
     * @return 片段 VO
     */
    @GetMapping("/{id}")
    public Result<KnowledgeSegmentVo> detail(@PathVariable("id") String id) {
        // 按主键加载片段全文
        return Result.ok(knowledgeSegmentAsvc.getById(id));
    }

    /**
     * 编辑片段内容。
     *
     * @param id   片段 ID
     * @param body 更新请求
     * @return 更新后的片段 VO
     */
    @PutMapping("/{id}")
    public Result<KnowledgeSegmentVo> update(
            @PathVariable("id") String id, @RequestBody KnowledgeSegmentUpdateRequest body) {
        // 修改后通常需重新向量化
        return Result.ok(knowledgeSegmentAsvc.update(id, body));
    }

    /**
     * 删除单个片段。
     *
     * @param id 片段 ID
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") String id) {
        // 删除片段及关联向量
        knowledgeSegmentAsvc.delete(id);
        return Result.ok(null);
    }

    /**
     * 批量删除片段。
     *
     * @param body 含 ids 的批量删除请求
     * @return 实际删除条数
     */
    @PostMapping("/batch-delete")
    public Result<Integer> batchDelete(@RequestBody KnowledgeSegmentBatchDeleteRequest body) {
        // 空请求时传 null，由服务层做空列表保护
        return Result.ok(knowledgeSegmentAsvc.batchDelete(body == null ? null : body.getIds()));
    }
}
