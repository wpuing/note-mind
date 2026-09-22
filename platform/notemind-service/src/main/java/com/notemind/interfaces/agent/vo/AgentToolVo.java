package com.notemind.interfaces.agent.vo;

/**
 * 工具定义视图对象。
 */
public class AgentToolVo {
    private String id;
    private String code;
    private String name;
    private String description;
    /** 入参说明 JSON 字符串，如 {"query":"检索问题"} */
    private String schemaJson;
    private Integer enabled;
    private Integer sortNo;
    private Integer implemented;
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
    /** 获取创建时间 */
    public String getCreateTime() { return createTime; }
    /** 设置创建时间 */
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
