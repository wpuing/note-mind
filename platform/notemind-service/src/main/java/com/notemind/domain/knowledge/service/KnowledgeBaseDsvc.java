package com.notemind.domain.knowledge.service;

import com.notemind.domain.knowledge.entity.KnowledgeBase;
import com.notemind.domain.knowledge.repositories.KnowledgeBaseRepository;
import com.notemind.domain.shared.PageData;
import com.notemind.application.service.system.SystemConfigAsvc;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 知识库领域服务：校验、默认策略、级联软删规则。
 * <p>不触达 AI / HTTP VO，策略默认值来自系统配置。</p>
 */
@Service
public class KnowledgeBaseDsvc {

    /** 知识库仓储 */
    private final KnowledgeBaseRepository repository;
    /** 系统配置（分页、默认模型/切分/检索策略） */
    private final SystemConfigAsvc systemConfig;

    /**
     * 构造并注入知识库仓储与系统配置。
     *
     * @param repository   知识库仓储
     * @param systemConfig 系统配置应用服务
     */
    public KnowledgeBaseDsvc(KnowledgeBaseRepository repository, SystemConfigAsvc systemConfig) {
        this.repository = repository;
        this.systemConfig = systemConfig;
    }

    /**
     * 列出全部启用中的知识库（下拉等场景）。
     *
     * @return 启用知识库列表
     */
    public List<KnowledgeBase> listEnabled() {
        // 委托仓储查询启用列表
        return repository.listEnabled();
    }

    /**
     * 按名称/状态/分类分页查询知识库。
     *
     * @param name     名称模糊条件
     * @param status   状态过滤，可为 null
     * @param category 分类过滤，可为 null
     * @param page     页码（规范为 ≥1）
     * @param pageSize 每页条数（按系统配置钳制）
     * @return 分页结果
     */
    public PageData<KnowledgeBase> page(String name, Integer status, String category, int page, int pageSize) {
        // 页码至少为 1
        int safePage = Math.max(page, 1);
        // 按系统配置钳制分页大小
        int safeSize = systemConfig.clampPageSize(pageSize);
        // 委托仓储分页查询
        return repository.page(name, status, category, safePage, safeSize);
    }

    /**
     * 按主键加载知识库；不存在则 404。
     *
     * @param id 知识库主键
     * @return 存在的知识库实体
     */
    public KnowledgeBase requireById(String id) {
        // 仓储按 id 查询，缺失则 404
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "knowledge base not found"));
    }

    /**
     * 新建知识库：校验名称/状态，补齐默认向量模型与切分/检索策略后落库。
     *
     * @param name                名称
     * @param category            分类，可空
     * @param description         描述，可空
     * @param status              状态，null 视为启用(1)
     * @param embeddingModelId    向量模型 ID，空则用系统默认
     * @param chunkStrategyId     切分策略 ID，空则用系统默认
     * @param retrievalStrategyId 检索策略 ID，空则用系统默认
     * @return 持久化后的实体
     */
    public KnowledgeBase create(String name, String category, String description, Integer status,
                                String embeddingModelId, String chunkStrategyId, String retrievalStrategyId) {
        // 校验名称非空且长度合法
        validateName(name);
        // 校验状态仅允许 0/1 或 null
        validateStatus(status);
        KnowledgeBase kb = new KnowledgeBase();
        // 生成短主键：kb_ + 16 位无连字符 UUID 片段
        kb.setId("kb_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        kb.setName(name.trim());
        kb.setCategory(blankToNull(category));
        kb.setDescription(blankToNull(description));
        kb.setStatus(status == null ? 1 : status);
        // 空则回落系统默认向量模型
        kb.setEmbeddingModelId(blankToDefault(embeddingModelId, systemConfig.embeddingModelId()));
        // 空则回落系统默认切分策略
        kb.setChunkStrategyId(blankToDefault(chunkStrategyId, systemConfig.chunkStrategyId()));
        // 空则回落系统默认检索策略
        kb.setRetrievalStrategyId(blankToDefault(retrievalStrategyId, systemConfig.retrievalStrategyId()));
        // 插入仓储
        repository.insert(kb);
        // 回读最新行
        return requireById(kb.getId());
    }

    /**
     * 更新已有知识库字段与绑定策略。
     *
     * @param id                  知识库主键
     * @param name                名称
     * @param category            分类
     * @param description         描述
     * @param status              状态
     * @param embeddingModelId    向量模型 ID
     * @param chunkStrategyId     切分策略 ID
     * @param retrievalStrategyId 检索策略 ID
     * @return 更新后的实体
     */
    public KnowledgeBase update(String id, String name, String category, String description, Integer status,
                                String embeddingModelId, String chunkStrategyId, String retrievalStrategyId) {
        // 确认记录存在
        requireById(id);
        // 校验名称
        validateName(name);
        // 校验状态
        validateStatus(status);
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(id);
        kb.setName(name.trim());
        kb.setCategory(blankToNull(category));
        kb.setDescription(blankToNull(description));
        kb.setStatus(status == null ? 1 : status);
        // 空则回落系统默认向量模型
        kb.setEmbeddingModelId(blankToDefault(embeddingModelId, systemConfig.embeddingModelId()));
        // 空则回落系统默认切分策略
        kb.setChunkStrategyId(blankToDefault(chunkStrategyId, systemConfig.chunkStrategyId()));
        // 空则回落系统默认检索策略
        kb.setRetrievalStrategyId(blankToDefault(retrievalStrategyId, systemConfig.retrievalStrategyId()));
        // 更新影响行数为 0 视为未找到
        if (!repository.update(kb)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "knowledge base not found");
        }
        // 回读最新数据
        return requireById(id);
    }

    /**
     * 软删知识库并级联软删其下文档/片段。
     *
     * @param id 知识库主键
     * @return 是否成功
     */
    public boolean softDeleteCascade(String id) {
        // 委托仓储级联软删
        return repository.softDeleteCascade(id);
    }

    /**
     * 列出知识库下仍存活（未软删）的文档 ID 集合。
     *
     * @param knowledgeBaseId 知识库主键
     * @return 文档 ID 集合
     */
    public Set<String> listLiveDocumentIds(String knowledgeBaseId) {
        // 委托仓储查询存活文档 ID
        return repository.listLiveDocumentIds(knowledgeBaseId);
    }

    /**
     * 统计知识库下解析失败的文档数量。
     *
     * @param knowledgeBaseId 知识库主键
     * @return 解析失败文档数
     */
    public long countParseFailedDocuments(String knowledgeBaseId) {
        // 委托仓储统计解析失败数
        return repository.countParseFailedDocuments(knowledgeBaseId);
    }

    /**
     * 汇总知识库下「已就绪」片段元数据计数（用于列表展示）。
     *
     * @param knowledgeBaseId 知识库主键
     * @return 就绪片段合计
     */
    public long sumMetaSegmentCountReady(String knowledgeBaseId) {
        // 委托仓储汇总就绪片段数
        return repository.sumMetaSegmentCountReady(knowledgeBaseId);
    }

    /**
     * 列出知识库下文档元信息行（解析状态、错误、meta JSON 等）。
     *
     * @param knowledgeBaseId 知识库主键
     * @return 文档元数据行列表
     */
    public List<KnowledgeBaseRepository.DocumentMetaRow> listDocumentMetas(String knowledgeBaseId) {
        // 先确认知识库存在
        requireById(knowledgeBaseId);
        // 委托仓储拉取文档元数据
        return repository.listDocumentMetas(knowledgeBaseId);
    }

    /**
     * 根据片段与向量化统计推导展示用向量状态。
     * <ul>
     *   <li>EMPTY：无片段或未完成任何成功向量化</li>
     *   <li>FAILED：有失败且无成功</li>
     *   <li>PARTIAL：仍有待处理或失败</li>
     *   <li>READY：全部成功</li>
     * </ul>
     *
     * @param segmentCount 片段总数，可为 null
     * @param done         已完成向量化数
     * @param pending      待向量化数
     * @param failed       失败数
     * @return 状态码字符串
     */
    public static String resolveVectorStatus(Long segmentCount, long done, long pending, long failed) {
        long segs = segmentCount == null ? 0L : segmentCount;
        // 无片段 → 空库
        if (segs <= 0) return "EMPTY";
        // 全失败、无成功 → 失败
        if (failed > 0 && done == 0) return "FAILED";
        // 仍有待处理或失败残留 → 部分完成
        if (pending > 0 || failed > 0) return "PARTIAL";
        // 有成功且无待处理/失败 → 就绪
        if (done > 0) return "READY";
        return "EMPTY";
    }

    /**
     * 校验知识库名称：非空且 trim 后长度 ≤128。
     *
     * @param name 名称
     */
    private static void validateName(String name) {
        // 名称必填
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name required");
        }
        // 长度上限 128
        if (name.trim().length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name too long");
        }
    }

    /**
     * 校验状态：null 或 0/1。
     *
     * @param status 状态值
     */
    private static void validateStatus(Integer status) {
        // 非 null 时必须是 0 或 1
        if (status != null && status != 0 && status != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be 0 or 1");
        }
    }

    /**
     * 空白字符串转为 null，否则 trim。
     *
     * @param v 原始值
     * @return null 或 trim 后的值
     */
    private static String blankToNull(String v) {
        // 空或纯空白视为未填写
        if (v == null || v.isBlank()) return null;
        return v.trim();
    }

    /**
     * 空白时使用默认值，否则 trim。
     *
     * @param v   原始值
     * @param def 默认值
     * @return 有效值或默认值
     */
    private static String blankToDefault(String v, String def) {
        // 空则回落默认
        if (v == null || v.isBlank()) return def;
        return v.trim();
    }
}
