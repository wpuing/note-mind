package com.notemind.interfaces.prompt.vo;

/**
 * Prompt 模板视图对象。
 */
public class PromptTemplateVo {
    private String id;
    private String code;
    private String name;
    private String scenario;
    private String content;
    /** 从 content 解析或库中 JSON，逗号拼接展示 */
    private String variables;
    private Integer enabled;
    private String remark;
    private String createTime;

    /** 获取主键 ID */
    public String getId() { return id; }
    /** 设置主键 ID */
    public void setId(String id) { this.id = id; }
    /** 获取编码 */
    public String getCode() { return code; }
    /** 设置编码 */
    public void setCode(String code) { this.code = code; }
    /** 获取名称 */
    public String getName() { return name; }
    /** 设置名称 */
    public void setName(String name) { this.name = name; }
    /** 获取场景 */
    public String getScenario() { return scenario; }
    /** 设置场景 */
    public void setScenario(String scenario) { this.scenario = scenario; }
    /** 获取内容 */
    public String getContent() { return content; }
    /** 设置内容 */
    public void setContent(String content) { this.content = content; }
    /** 获取variables */
    public String getVariables() { return variables; }
    /** 设置variables */
    public void setVariables(String variables) { this.variables = variables; }
    /** 获取是否启用 */
    public Integer getEnabled() { return enabled; }
    /** 设置是否启用 */
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    /** 获取备注 */
    public String getRemark() { return remark; }
    /** 设置备注 */
    public void setRemark(String remark) { this.remark = remark; }
    /** 获取创建时间 */
    public String getCreateTime() { return createTime; }
    /** 设置创建时间 */
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
