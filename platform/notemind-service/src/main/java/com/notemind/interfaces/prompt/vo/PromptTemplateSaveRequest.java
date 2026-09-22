package com.notemind.interfaces.prompt.vo;

/**
 * Prompt 模板保存请求体。
 */
public class PromptTemplateSaveRequest {
    private String code;
    private String name;
    private String scenario;
    private String content;
    private Integer enabled;
    private String remark;

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
    /** 获取是否启用 */
    public Integer getEnabled() { return enabled; }
    /** 设置是否启用 */
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    /** 获取备注 */
    public String getRemark() { return remark; }
    /** 设置备注 */
    public void setRemark(String remark) { this.remark = remark; }
}
