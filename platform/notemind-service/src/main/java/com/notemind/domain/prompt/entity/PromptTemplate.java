package com.notemind.domain.prompt.entity;

/**
 * Prompt 模板聚合根。
 * <p>对应表字段：编码、场景、正文、变量 JSON、启停与备注等。</p>
 */
public class PromptTemplate {
    /** 主键，如 p_xxxxxxxxxxxx */
    private String id;
    /** 模板编码（唯一，通常大写） */
    private String code;
    /** 显示名称 */
    private String name;
    /** 使用场景（如问答/改写/评测） */
    private String scenario;
    /** 模板正文，可含 {var} / {{var}} 占位符 */
    private String content;
    /** JSON 数组字符串，如 ["query","context"]，由正文抽取 */
    private String variablesJson;
    /** 是否启用：1 启用 / 0 停用 */
    private Integer enabled;
    /** 备注说明 */
    private String remark;
    /** 创建时间（库侧字符串或格式化结果） */
    private String createTime;

    /** @return 主键 */ public String getId() { return id; }
    /** @param id 主键 */ public void setId(String id) { this.id = id; }
    /** @return 模板编码 */ public String getCode() { return code; }
    /** @param code 模板编码 */ public void setCode(String code) { this.code = code; }
    /** @return 显示名称 */ public String getName() { return name; }
    /** @param name 显示名称 */ public void setName(String name) { this.name = name; }
    /** @return 使用场景 */ public String getScenario() { return scenario; }
    /** @param scenario 使用场景 */ public void setScenario(String scenario) { this.scenario = scenario; }
    /** @return 模板正文 */ public String getContent() { return content; }
    /** @param content 模板正文 */ public void setContent(String content) { this.content = content; }
    /** @return 变量 JSON 数组字符串 */ public String getVariablesJson() { return variablesJson; }
    /** @param variablesJson 变量 JSON */ public void setVariablesJson(String variablesJson) { this.variablesJson = variablesJson; }
    /** @return 启用标记 */ public Integer getEnabled() { return enabled; }
    /** @param enabled 启用标记 */ public void setEnabled(Integer enabled) { this.enabled = enabled; }
    /** @return 备注 */ public String getRemark() { return remark; }
    /** @param remark 备注 */ public void setRemark(String remark) { this.remark = remark; }
    /** @return 创建时间 */ public String getCreateTime() { return createTime; }
    /** @param createTime 创建时间 */ public void setCreateTime(String createTime) { this.createTime = createTime; }
}
