package com.notemind.interfaces.eval.controller;

import com.notemind.application.service.eval.EvalAsvc;
import com.notemind.application.service.eval.EvalBatchAsvc;
import com.notemind.common.dto.IdsBatchDeleteRequest;
import com.notemind.common.result.PageResult;
import com.notemind.common.result.Result;
import com.notemind.interfaces.eval.vo.EvalBatchStartRequest;
import com.notemind.interfaces.eval.vo.EvalCaseSaveRequest;
import com.notemind.interfaces.eval.vo.EvalCaseVo;
import com.notemind.interfaces.eval.vo.EvalDatasetSaveRequest;
import com.notemind.interfaces.eval.vo.EvalDatasetVo;
import com.notemind.interfaces.eval.vo.EvalGenerateFromDocRequest;
import com.notemind.interfaces.eval.vo.EvalReportBatchDeleteRequest;
import com.notemind.interfaces.eval.vo.EvalReportVo;
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
 * 评测接口：评测集/用例 CRUD、文档生成、点踩导入，以及批量评测报告。
 */
@RestController
@RequestMapping("/api/v1/eval")
public class EvalController {

    /** 评测集与用例应用服务。 */
    private final EvalAsvc evalAsvc;
    /** 批量评测与报告应用服务。 */
    private final EvalBatchAsvc evalBatchAsvc;

    /**
     * 构造注入评测相关服务。
     *
     * @param evalAsvc      评测集/用例服务
     * @param evalBatchAsvc 批量评测服务
     */
    public EvalController(EvalAsvc evalAsvc, EvalBatchAsvc evalBatchAsvc) {
        this.evalAsvc = evalAsvc;
        this.evalBatchAsvc = evalBatchAsvc;
    }

    /**
     * 列出全部评测集。
     *
     * @return 评测集列表
     */
    @GetMapping("/datasets")
    public Result<List<EvalDatasetVo>> listDatasets() {
        // 返回评测集下拉/列表数据
        return Result.ok(evalAsvc.listDatasets());
    }

    /**
     * 查询评测集详情。
     *
     * @param id 评测集 ID
     * @return 评测集 VO
     */
    @GetMapping("/datasets/{id}")
    public Result<EvalDatasetVo> getDataset(@PathVariable String id) {
        // 按主键加载评测集元数据
        return Result.ok(evalAsvc.getDataset(id));
    }

    /**
     * 新建评测集。
     *
     * @param body 保存请求
     * @return 新建后的评测集 VO
     */
    @PostMapping("/datasets")
    public Result<EvalDatasetVo> createDataset(@RequestBody EvalDatasetSaveRequest body) {
        // 持久化评测集定义
        return Result.ok(evalAsvc.createDataset(body));
    }

    /**
     * 删除评测集。
     *
     * @param id 评测集 ID
     * @return 空成功响应
     */
    @DeleteMapping("/datasets/{id}")
    public Result<Void> deleteDataset(@PathVariable String id) {
        // 删除评测集及其关联用例
        evalAsvc.deleteDataset(id);
        return Result.ok(null);
    }

    /**
     * 批量删除评测集。
     *
     * @param body 含 ids 的批量删除请求
     * @return 实际删除条数
     */
    @PostMapping("/datasets/batch-delete")
    public Result<Map<String, Integer>> batchDeleteDatasets(@RequestBody IdsBatchDeleteRequest body) {
        // 空请求时传 null，由服务层做空列表保护
        int n = evalAsvc.batchDeleteDatasets(body == null ? null : body.getIds());
        return Result.ok(Map.of("deleted", n));
    }

    /**
     * 分页查询评测集下的用例。
     *
     * @param id         评测集 ID
     * @param sourceType 来源类型，可选
     * @param question   问题关键词，可选
     * @param page       页码
     * @param pageSize   每页条数
     * @return 分页结果
     */
    @GetMapping("/datasets/{id}/cases/page")
    public Result<PageResult<EvalCaseVo>> pageCases(
            @PathVariable String id,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String question,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 在指定评测集内按来源与问题筛选用例
        return Result.ok(evalAsvc.pageCases(id, sourceType, question, page, pageSize));
    }

    /**
     * 手工新增评测用例。
     *
     * @param id   评测集 ID
     * @param body 用例保存请求
     * @return 新建用例 VO
     */
    @PostMapping("/datasets/{id}/cases")
    public Result<EvalCaseVo> createCase(
            @PathVariable String id, @RequestBody EvalCaseSaveRequest body) {
        // 向指定评测集追加手工用例
        return Result.ok(evalAsvc.createCase(id, body));
    }

    /**
     * 更新评测用例。
     *
     * @param id   用例 ID
     * @param body 用例保存请求
     * @return 更新后的用例 VO
     */
    @PutMapping("/cases/{id}")
    public Result<EvalCaseVo> updateCase(
            @PathVariable String id, @RequestBody EvalCaseSaveRequest body) {
        // 按用例 ID 覆盖更新
        return Result.ok(evalAsvc.updateCase(id, body));
    }

    /**
     * 删除单条用例。
     *
     * @param id 用例 ID
     * @return 空成功响应
     */
    @DeleteMapping("/cases/{id}")
    public Result<Void> deleteCase(@PathVariable String id) {
        // 删除单条评测用例
        evalAsvc.deleteCase(id);
        return Result.ok(null);
    }

    /**
     * 批量删除用例。
     *
     * @param body 含 ids 的批量删除请求
     * @return 实际删除条数
     */
    @PostMapping("/cases/batch-delete")
    public Result<Map<String, Integer>> batchDeleteCases(@RequestBody IdsBatchDeleteRequest body) {
        // 空请求时传 null，由服务层做空列表保护
        int n = evalAsvc.batchDeleteCases(body == null ? null : body.getIds());
        return Result.ok(Map.of("deleted", n));
    }

    /**
     * 查询用例详情（含原文核对信息）。
     *
     * @param id 用例 ID
     * @return 用例 VO
     */
    @GetMapping("/cases/{id}")
    public Result<EvalCaseVo> getCase(@PathVariable String id) {
        // 加载用例及关联原文片段
        return Result.ok(evalAsvc.getCase(id));
    }

    /**
     * 基于文档大模型生成评测用例。
     *
     * @param id   评测集 ID
     * @param body 生成请求
     * @return 生成结果摘要（数量等）
     */
    @PostMapping("/datasets/{id}/generate-from-doc")
    public Result<Map<String, Object>> generateFromDoc(
            @PathVariable String id, @RequestBody EvalGenerateFromDocRequest body) {
        // 调用对话模型从文档自动生成问答对
        return Result.ok(evalAsvc.generateFromDocument(id, body));
    }

    /**
     * 从点踩反馈沉淀导入用例。
     *
     * @param id 评测集 ID
     * @return 导入结果摘要
     */
    @PostMapping("/datasets/{id}/import-dislikes")
    public Result<Map<String, Object>> importDislikes(@PathVariable String id) {
        // 将未沉淀点踩转为评测用例
        return Result.ok(evalAsvc.importFromDislikes(id));
    }

    /**
     * 统计待沉淀点踩数量。
     *
     * @return 含 count 的 Map
     */
    @GetMapping("/pending-dislikes/count")
    public Result<Map<String, Integer>> pendingDislikes() {
        // 供管理端展示可导入数量
        return Result.ok(Map.of("count", evalAsvc.countPendingDislikes()));
    }

    /**
     * 分页查询批量评测报告。
     *
     * @param datasetId 评测集 ID，可选
     * @param status    任务状态，可选
     * @param page      页码
     * @param pageSize  每页条数
     * @return 分页结果
     */
    @GetMapping("/reports/page")
    public Result<PageResult<EvalReportVo>> pageReports(
            @RequestParam(required = false) String datasetId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 按评测集与状态筛选批量任务
        return Result.ok(evalBatchAsvc.pageReports(datasetId, status, page, pageSize));
    }

    /**
     * 启动一次批量评测。
     *
     * @param body 启动请求（评测集 + 检索策略等）
     * @return 新建报告 VO（含进度）
     */
    @PostMapping("/reports/start")
    public Result<EvalReportVo> startBatch(@RequestBody EvalBatchStartRequest body) {
        // 异步开跑四维评测任务
        return Result.ok(evalBatchAsvc.start(body));
    }

    /**
     * 批量删除评测报告。
     *
     * @param body 含 ids 的批量删除请求
     * @return 实际删除条数
     */
    @PostMapping("/reports/batch-delete")
    public Result<Map<String, Integer>> batchDeleteReports(@RequestBody EvalReportBatchDeleteRequest body) {
        // 空请求时传 null，由服务层做空列表保护
        int n = evalBatchAsvc.batchDeleteReports(body == null ? null : body.getIds());
        return Result.ok(Map.of("deleted", n));
    }

    /**
     * 查询评测报告详情（含四维指标）。
     *
     * @param id 报告 ID
     * @return 报告 VO
     */
    @GetMapping("/reports/{id}")
    public Result<EvalReportVo> getReport(@PathVariable String id) {
        // 加载报告进度与指标明细
        return Result.ok(evalBatchAsvc.getReport(id));
    }

    /**
     * 删除单份评测报告。
     *
     * @param id 报告 ID
     * @return 空成功响应
     */
    @DeleteMapping("/reports/{id}")
    public Result<Void> deleteReport(@PathVariable String id) {
        // 删除报告及关联明细
        evalBatchAsvc.deleteReport(id);
        return Result.ok(null);
    }
}
