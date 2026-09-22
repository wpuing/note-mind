package com.notemind.application.assembler.prompt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notemind.domain.prompt.entity.PromptTemplate;
import com.notemind.domain.prompt.service.PromptTemplateDsvc;
import com.notemind.interfaces.prompt.vo.PromptTemplateVo;

import java.util.List;

/**
 * Prompt 模板领域实体与接口 VO 的装配器。
 */
public final class PromptTemplateAssembler {

    /** 工具类禁止实例化 */
    private PromptTemplateAssembler() {}

    /**
     * 将 Prompt 模板实体转为前端 VO。
     *
     * @param e            领域实体，可为 null
     * @param objectMapper JSON 解析器，用于解析 variablesJson
     * @return VO；实体为 null 时返回 null
     */
    public static PromptTemplateVo toVo(PromptTemplate e, ObjectMapper objectMapper) {
        // 空实体直接返回
        if (e == null) return null;
        PromptTemplateVo vo = new PromptTemplateVo();
        vo.setId(e.getId());
        vo.setCode(e.getCode());
        vo.setName(e.getName());
        vo.setScenario(e.getScenario());
        vo.setContent(e.getContent());
        // 优先用库中 variablesJson；为空则从正文抽取 {var}
        List<String> vars = parseVarsJson(e.getVariablesJson(), objectMapper);
        // 若 JSON 无变量列表，则从模板内容提取占位符
        if (vars.isEmpty()) {
            // 调用领域服务从 content 抽取变量名
            vars = PromptTemplateDsvc.extractVariables(e.getContent());
        }
        vo.setVariables(String.join(",", vars));
        vo.setEnabled(e.getEnabled());
        vo.setRemark(e.getRemark());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }

    /**
     * 解析 variablesJson 为变量名列表。
     *
     * @param json         JSON 数组字符串
     * @param objectMapper Jackson ObjectMapper
     * @return 变量名列表；空或解析失败时返回空列表
     */
    private static List<String> parseVarsJson(String json, ObjectMapper objectMapper) {
        // 无 JSON 内容则视为无变量
        if (json == null || json.isBlank()) return List.of();
        // 尝试执行
        try {
            // 调用 ObjectMapper 反序列化变量数组
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            // JSON 损坏时降级为空列表，避免阻断列表接口
            return List.of();
        }
    }
}

