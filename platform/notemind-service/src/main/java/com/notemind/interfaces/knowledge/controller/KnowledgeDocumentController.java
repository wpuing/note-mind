package com.notemind.interfaces.knowledge.controller;

import com.notemind.application.service.knowledge.KnowledgeDocumentAsvc;
import com.notemind.common.result.PageResult;
import com.notemind.common.result.Result;
import com.notemind.interfaces.knowledge.vo.DocumentParsedTextVo;
import com.notemind.interfaces.knowledge.vo.KnowledgeDocumentBatchDeleteRequest;
import com.notemind.interfaces.knowledge.vo.KnowledgeDocumentChunkRequest;
import com.notemind.interfaces.knowledge.vo.KnowledgeDocumentUpdateRequest;
import com.notemind.interfaces.knowledge.vo.KnowledgeDocumentVo;
import com.notemind.interfaces.knowledge.vo.KnowledgeSegmentVo;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 文件管理接口：上传、分页、解析预览、切分、向量化、下载、重传与删除。
 */
@RestController
@RequestMapping("/api/v1/knowledge/documents")
public class KnowledgeDocumentController {

    /** 知识文档应用服务。 */
    private final KnowledgeDocumentAsvc knowledgeDocumentAsvc;

    /**
     * 构造注入文档服务。
     *
     * @param knowledgeDocumentAsvc 文档应用服务
     */
    public KnowledgeDocumentController(KnowledgeDocumentAsvc knowledgeDocumentAsvc) {
        this.knowledgeDocumentAsvc = knowledgeDocumentAsvc;
    }

    /**
     * 上传文件并绑定知识库（仅存盘，不自动向量化）。
     *
     * @param file            上传文件
     * @param knowledgeBaseId 知识库 ID，缺省取系统默认
     * @return 文档 VO
     */
    @PostMapping("/upload")
    public Result<KnowledgeDocumentVo> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "knowledgeBaseId", required = false,
                    defaultValue = "${notemind.defaults.knowledge-base-id:kb_default}")
                    String knowledgeBaseId) {
        // 存盘并创建文档元数据，等待后续切分/向量化
        return Result.ok(knowledgeDocumentAsvc.upload(file, knowledgeBaseId));
    }

    /**
     * 分页查询文档列表。
     *
     * @param knowledgeBaseId 知识库 ID，可选
     * @param title           标题筛选，可选
     * @param parseStatus     解析状态，可选
     * @param vectorIssue     向量异常筛选，可选
     * @param page            页码
     * @param pageSize        每页条数
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<KnowledgeDocumentVo>> list(
            @RequestParam(value = "knowledgeBaseId", required = false) String knowledgeBaseId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "parseStatus", required = false) String parseStatus,
            @RequestParam(value = "vectorIssue", required = false) String vectorIssue,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "8") int pageSize) {
        // 文件管理页多条件分页
        return Result.ok(knowledgeDocumentAsvc.page(
                knowledgeBaseId, title, parseStatus, vectorIssue, page, pageSize));
    }

    /**
     * 分页查询某文档下的片段。
     *
     * @param id       文档 ID
     * @param keyword  内容关键词，可选
     * @param page     页码
     * @param pageSize 每页条数
     * @return 片段分页结果
     */
    @GetMapping("/{id}/segments")
    public Result<PageResult<KnowledgeSegmentVo>> segments(
            @PathVariable("id") String id,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "8") int pageSize) {
        // 文档详情内嵌片段列表
        return Result.ok(knowledgeDocumentAsvc.listSegments(id, keyword, page, pageSize));
    }

    /**
     * 解析预览文档文本（大文件可能限页 OCR）。
     *
     * @param id 文档 ID
     * @return 解析文本 VO
     */
    @GetMapping("/{id}/parsed-text")
    public Result<DocumentParsedTextVo> parsedText(@PathVariable("id") String id) {
        // 调用 AI 引擎解析并返回预览文本
        return Result.ok(knowledgeDocumentAsvc.previewParsedText(id));
    }

    /**
     * 对文档执行切分，生成待向量化片段。
     *
     * @param id   文档 ID
     * @param body 切分请求（可选策略 ID）
     * @return 更新后的文档 VO
     */
    @PostMapping("/{id}/chunk")
    public Result<KnowledgeDocumentVo> chunk(
            @PathVariable("id") String id, @RequestBody(required = false) KnowledgeDocumentChunkRequest body) {
        // 未传 body 时使用知识库默认切分策略
        String strategyId = body == null ? null : body.getChunkStrategyId();
        // 调用切分流水线写入片段表
        return Result.ok(knowledgeDocumentAsvc.chunk(id, strategyId));
    }

    /**
     * 将文档片段向量化写入 PGVector。
     *
     * @param id 文档 ID
     * @return 更新后的文档 VO
     */
    @PostMapping("/{id}/vectorize")
    public Result<KnowledgeDocumentVo> vectorize(@PathVariable("id") String id) {
        // 子块入向量库并回写片段状态
        return Result.ok(knowledgeDocumentAsvc.vectorize(id));
    }

    /**
     * 清除文档在向量库中的向量。
     *
     * @param id 文档 ID
     * @return 更新后的文档 VO
     */
    @DeleteMapping("/{id}/vectors")
    public Result<KnowledgeDocumentVo> clearVectors(@PathVariable("id") String id) {
        // 删除 PGVector 中该文档向量并重置状态
        return Result.ok(knowledgeDocumentAsvc.clearVectors(id));
    }

    /**
     * 下载原始文件。
     *
     * @param id 文档 ID
     * @return 文件流响应
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable("id") String id) {
        // 读取本地存盘文件并组装下载载荷
        KnowledgeDocumentAsvc.DownloadPayload payload = knowledgeDocumentAsvc.download(id);
        // 文件名 UTF-8 编码，兼容中文
        String encoded = URLEncoder.encode(payload.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(payload.resource());
    }

    /**
     * 修改文档元数据（如标题）。
     *
     * @param id   文档 ID
     * @param body 更新请求
     * @return 更新后的文档 VO
     */
    @PutMapping("/{id}")
    public Result<KnowledgeDocumentVo> update(
            @PathVariable("id") String id, @RequestBody KnowledgeDocumentUpdateRequest body) {
        // 更新文档展示字段
        return Result.ok(knowledgeDocumentAsvc.update(id, body));
    }

    /**
     * 用新文件覆盖重传。
     *
     * @param id   文档 ID
     * @param file 新文件
     * @return 更新后的文档 VO
     */
    @PostMapping("/{id}/reupload")
    public Result<KnowledgeDocumentVo> reupload(
            @PathVariable("id") String id, @RequestParam("file") MultipartFile file) {
        // 替换存盘文件并重置解析/向量状态
        return Result.ok(knowledgeDocumentAsvc.reupload(id, file));
    }

    /**
     * 删除文档。
     *
     * @param id 文档 ID
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") String id) {
        // 删除元数据、片段、向量与本地文件
        knowledgeDocumentAsvc.delete(id);
        return Result.ok(null);
    }

    /**
     * 批量删除文档。
     *
     * @param body 含 ids 的批量删除请求
     * @return 实际删除条数
     */
    @PostMapping("/batch-delete")
    public Result<Map<String, Integer>> batchDelete(
            @RequestBody KnowledgeDocumentBatchDeleteRequest body) {
        // 空请求时传 null，由服务层做空列表保护
        int n = knowledgeDocumentAsvc.batchDelete(body == null ? null : body.getIds());
        return Result.ok(Map.of("deleted", n));
    }

    /**
     * 查询文档详情。
     *
     * @param id 文档 ID
     * @return 文档 VO
     */
    @GetMapping("/{id}")
    public Result<KnowledgeDocumentVo> detail(@PathVariable("id") String id) {
        // 按主键加载文档元数据
        return Result.ok(knowledgeDocumentAsvc.getById(id));
    }
}
