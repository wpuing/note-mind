package com.notemind.domain.knowledge.repositories;

import com.notemind.domain.knowledge.entity.KnowledgeBase;
import com.notemind.domain.shared.PageData;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 知识库仓储端口：CRUD、级联软删与文档侧统计/元数据查询。
 * <p>由 infrastructure 层 Jdbc 实现，领域服务仅依赖本接口。</p>
 */
public interface KnowledgeBaseRepository {

    /**
     * 列出全部启用中的知识库。
     *
     * @return 启用知识库列表
     */
    List<KnowledgeBase> listEnabled();

    /**
     * 按名称/状态/分类分页查询知识库（可含列表统计字段）。
     *
     * @param name     名称模糊条件
     * @param status   状态过滤，可为 null
     * @param category 分类过滤，可为 null
     * @param page     页码（≥1）
     * @param pageSize 每页条数
     * @return 分页数据
     */
    PageData<KnowledgeBase> page(String name, Integer status, String category, int page, int pageSize);

    /**
     * 按主键查询知识库。
     *
     * @param id 知识库主键
     * @return 存在则返回实体，否则 empty
     */
    Optional<KnowledgeBase> findById(String id);

    /**
     * 插入新知识库。
     *
     * @param kb 待插入实体（须已填 id 等字段）
     */
    void insert(KnowledgeBase kb);

    /**
     * 按主键更新知识库。
     *
     * @param kb 含 id 的更新字段
     * @return 是否更新到行（false 表示未找到）
     */
    boolean update(KnowledgeBase kb);

    /**
     * 软删知识库并级联软删其下文档/片段；成功返回 true。
     *
     * @param id 知识库主键
     * @return 是否成功
     */
    boolean softDeleteCascade(String id);

    /**
     * 列出知识库下仍存活（未软删）的文档 ID。
     *
     * @param knowledgeBaseId 知识库主键
     * @return 文档 ID 集合
     */
    Set<String> listLiveDocumentIds(String knowledgeBaseId);

    /**
     * 统计知识库下解析失败的文档数量。
     *
     * @param knowledgeBaseId 知识库主键
     * @return 解析失败文档数
     */
    long countParseFailedDocuments(String knowledgeBaseId);

    /**
     * 统计知识库下正在向量化（EMBEDDING）的文档数。
     */
    long countEmbeddingDocuments(String knowledgeBaseId);

    /**
     * 汇总知识库下就绪片段的元数据计数。
     *
     * @param knowledgeBaseId 知识库主键
     * @return 就绪片段合计
     */
    long sumMetaSegmentCountReady(String knowledgeBaseId);

    /**
     * 列出知识库下文档元信息行。
     *
     * @param knowledgeBaseId 知识库主键
     * @return 文档元数据行列表
     */
    List<DocumentMetaRow> listDocumentMetas(String knowledgeBaseId);

    /**
     * 文档元数据投影：用于列表/状态下钻，不含正文内容。
     *
     * @param documentId   文档主键
     * @param title        标题
     * @param fileName     原始文件名
     * @param parseStatus  解析状态
     * @param errorMessage 错误信息
     * @param metaJson     元数据 JSON
     * @param createTime   创建时间
     */
    record DocumentMetaRow(
            String documentId,
            String title,
            String fileName,
            String parseStatus,
            String errorMessage,
            String metaJson,
            String createTime) {}
}
