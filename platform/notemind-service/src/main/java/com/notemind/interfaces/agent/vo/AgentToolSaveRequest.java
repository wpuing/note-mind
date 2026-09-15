package com.notemind.interfaces.agent.vo;

/**
 * 工具定义保存请求体。
 */
public class AgentToolSaveRequest {
    private String code;
    private String name;
    private String description;
    private String schemaJson;
    private Integer enabled;
    private Integer sortNo;
    private Integer implemented;

    /** 获取编码 */
    public String getCode() { return code; }
    /** 设置编码 */
    public void setCode(String code) { this.code = code; }
    /** 获取名称 */
    public String getName() { return name; }
    /** 设置名称 */
    public void setName(String name) { this.name = name; }
    /** 获取描述 */
    public String getDescription() { return description; }
    /** 设置描述 */
    public void setDescription(String description) { this.description = description; }
    /** 获取schema Json */
    public String getSchemaJson() { return schemaJson; }
    /** 设置schema Json */
    public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }
    /** 获取是否启用 */
    public Integer getEnabled() { return enabled; }
    /** 设置是否启用 */
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    /** 获取sort No */
    public Integer getSortNo() { return sortNo; }
    /** 设置sort No */
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    /** 获取是否已实现 */
    public Integer getImplemented() { return implemented; }
    /** 设置是否已实现 */
    public void setImplemented(Integer implemented) { this.implemented = implemented; }
}
