package com.notemind.application.service.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notemind.application.assembler.prompt.PromptTemplateAssembler;
import com.notemind.common.result.PageResult;
import com.notemind.domain.prompt.entity.PromptTemplate;
import com.notemind.domain.prompt.service.PromptTemplateDsvc;
import com.notemind.domain.shared.PageData;
import com.notemind.interfaces.prompt.vo.PromptTemplateSaveRequest;
import com.notemind.interfaces.prompt.vo.PromptTemplateVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Prompt 模板应用服务：VO 编排，领域规则在 {@link PromptTemplateDsvc}。
 */

@Service
public class PromptTemplateAsvc {

    private final PromptTemplateDsvc promptTemplateDsvc;
    private final ObjectMapper objectMapper;

    /**
     * 注入领域服务与 JSON 工具。
     *
     * @param promptTemplateDsvc Prompt 模板领域服务
     * @param objectMapper       Jackson ObjectMapper
     */
    public PromptTemplateAsvc(PromptTemplateDsvc promptTemplateDsvc, ObjectMapper objectMapper) {
        this.promptTemplateDsvc = promptTemplateDsvc;
        this.objectMapper = objectMapper;
    }

    /**
     * 分页查询 Prompt 模板。
     *
     * @param name     名称模糊，可空
     * @param scenario 场景过滤，可空
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页 VO
     */
    public PageResult<PromptTemplateVo> page(String name, String scenario, int page, int pageSize) {
        // 调用领域服务分页查询
        PageData<PromptTemplate> data = promptTemplateDsvc.page(name, scenario, page, pageSize);
        List<PromptTemplateVo> records = data.getRecords().stream()
                // 装配为 VO（需 ObjectMapper 解析 variablesJson）
                .map(e -> PromptTemplateAssembler.toVo(e, objectMapper))
                .toList();
        return PageResult.of(data.getTotal(), data.getPage(), data.getPageSize(), records);
    }

    /**
     * 按 ID 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板 VO
     */
    public PromptTemplateVo getById(String id) {
        // 领域必存在校验后装配 VO
        return PromptTemplateAssembler.toVo(promptTemplateDsvc.requireById(id), objectMapper);
    }

    /**
     * 新建 Prompt 模板。
     *
     * @param req 保存请求
     * @return 新建后的 VO
     */

    @Transactional
    public PromptTemplateVo create(PromptTemplateSaveRequest req) {
        // 调用领域服务创建
        PromptTemplate e = promptTemplateDsvc.create(
                req.getCode(),
                req.getName(),
                req.getScenario(),
                req.getContent(),
                req.getEnabled(),
                req.getRemark());
        return PromptTemplateAssembler.toVo(e, objectMapper);
    }

    /**
     * 更新 Prompt 模板。
     *
     * @param id  模板 ID
     * @param req 保存请求
     * @return 更新后的 VO
     */

    @Transactional
    public PromptTemplateVo update(String id, PromptTemplateSaveRequest req) {
        // 调用领域服务更新
        PromptTemplate e = promptTemplateDsvc.update(
                id,
                req.getCode(),
                req.getName(),
                req.getScenario(),
                req.getContent(),
                req.getEnabled(),
                req.getRemark());
        return PromptTemplateAssembler.toVo(e, objectMapper);
    }

    /**
     * 逻辑删除单个模板。
     *
     * @param id 模板 ID
     */

    @Transactional
    public void delete(String id) {
        // 调用领域服务软删除
        promptTemplateDsvc.softDelete(id);
    }

    /**
     * 批量逻辑删除模板。
     *
     * @param ids 模板 ID 列表
     * @return 实际删除条数
     */

    @Transactional
    public int batchDelete(List<String> ids) {
        // 调用领域服务批量软删除
        return promptTemplateDsvc.softDeleteBatch(ids);
    }

    /**
     * 兼容其它应用服务调用的变量抽取。
     *
     * @param content 模板正文
     * @return 变量名列表
     */
    public static List<String> extractVariables(String content) {
        // 委托领域服务抽取 {var}
        return PromptTemplateDsvc.extractVariables(content);
    }
}

