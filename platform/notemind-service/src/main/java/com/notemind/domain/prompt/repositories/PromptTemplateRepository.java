package com.notemind.domain.prompt.repositories;

import com.notemind.domain.prompt.entity.PromptTemplate;
import com.notemind.domain.shared.PageData;

import java.util.List;
import java.util.Optional;

/**
 * Prompt 模板仓储端口：分页查询、按 ID 加载、增改与软删。
 * <p>由 infrastructure 层 Jdbc 实现，领域层仅依赖本接口。</p>
 */
public interface PromptTemplateRepository {

    /**
     * 按名称/场景分页查询模板。
     *
     * @param name     名称模糊条件，可为 null
     * @param scenario 场景过滤，可为 null
     * @param page     页码（≥1）
     * @param pageSize 每页条数
     * @return 分页数据
     */
    PageData<PromptTemplate> page(String name, String scenario, int page, int pageSize);

    /**
     * 按主键查询单条模板。
     *
     * @param id 模板主键
     * @return 存在则返回实体，否则 empty
     */
    Optional<PromptTemplate> findById(String id);

    /**
     * 插入新模板；编码冲突时可能抛出唯一键异常。
     *
     * @param entity 待插入实体（须已填 id 等字段）
     */
    void insert(PromptTemplate entity);

    /**
     * 按主键更新模板。
     *
     * @param entity 含 id 的完整更新字段
     * @return 是否更新到行（false 表示未找到）
     */
    boolean update(PromptTemplate entity);

    /**
     * 软删除单条模板。
     *
     * @param id 模板主键
     * @return 是否删除成功
     */
    boolean softDelete(String id);

    /**
     * 批量软删除模板。
     *
     * @param ids 主键列表
     * @return 实际软删条数
     */
    int softDeleteBatch(List<String> ids);
}
