package com.notemind.interfaces.knowledge.controller;

import com.notemind.application.service.knowledge.KnowledgeCategoryAsvc;
import com.notemind.common.result.PageResult;
import com.notemind.common.result.Result;
import com.notemind.interfaces.knowledge.vo.KnowledgeCategorySaveRequest;
import com.notemind.interfaces.knowledge.vo.KnowledgeCategoryVo;
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

/**
 * 知识库分类管理接口：启用列表与分页 CRUD。
 */
@RestController
@RequestMapping("/api/v1/knowledge/categories")
public class KnowledgeCategoryController {

    /** 知识库分类应用服务。 */
    private final KnowledgeCategoryAsvc knowledgeCategoryAsvc;

    /**
     * 构造注入分类服务。
     *
     * @param knowledgeCategoryAsvc 分类应用服务
     */
    public KnowledgeCategoryController(KnowledgeCategoryAsvc knowledgeCategoryAsvc) {
        this.knowledgeCategoryAsvc = knowledgeCategoryAsvc;
    }

    /**
     * 启用分类（知识库表单下拉）。
     *
     * @return 已启用分类列表
     */
    @GetMapping
    public Result<List<KnowledgeCategoryVo>> list() {
        // 仅返回启用分类供知识库表单选择
        return Result.ok(knowledgeCategoryAsvc.listEnabled());
    }

    /**
     * 分页查询分类。
     *
     * @param keyword  关键词，可选
     * @param status   状态，可选
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<KnowledgeCategoryVo>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int pageSize) {
        // 分类管理弹窗分页列表
        return Result.ok(knowledgeCategoryAsvc.page(keyword, status, page, pageSize));
    }

    /**
     * 查询分类详情。
     *
     * @param id 分类 ID
     * @return 分类 VO
     */
    @GetMapping("/{id}")
    public Result<KnowledgeCategoryVo> detail(@PathVariable String id) {
        // 按主键加载分类
        return Result.ok(knowledgeCategoryAsvc.getById(id));
    }

    /**
     * 新建分类。
     *
     * @param request 保存请求
     * @return 新建后的分类 VO
     */
    @PostMapping
    public Result<KnowledgeCategoryVo> create(@RequestBody KnowledgeCategorySaveRequest request) {
        // 持久化分类定义
        return Result.ok(knowledgeCategoryAsvc.create(request));
    }

    /**
     * 更新分类。
     *
     * @param id      分类 ID
     * @param request 保存请求
     * @return 更新后的分类 VO
     */
    @PutMapping("/{id}")
    public Result<KnowledgeCategoryVo> update(
            @PathVariable String id, @RequestBody KnowledgeCategorySaveRequest request) {
        // 按 ID 覆盖更新分类
        return Result.ok(knowledgeCategoryAsvc.update(id, request));
    }

    /**
     * 删除分类。
     *
     * @param id 分类 ID
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        // 删除分类（被引用时由服务层校验）
        knowledgeCategoryAsvc.delete(id);
        return Result.ok(null);
    }
}
