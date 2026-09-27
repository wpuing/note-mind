package com.notemind.interfaces.system.vo;

/**
 * 系统配置项视图对象。
 */
public class SystemConfigVo {
    private String id;
    private String group;
    private String key;
    private String value;
    private String valueType;
    private String label;
    private String description;
    private Integer sortOrder;
    private Integer editable;
    private String updateTime;

    /** 获取主键 ID */
    public String getId() { return id; }
    /** 设置主键 ID */
    public void setId(String id) { this.id = id; }
    /** 获取group */
    public String getGroup() { return group; }
    /** 设置group */
    public void setGroup(String group) { this.group = group; }
    /** 获取key */
    public String getKey() { return key; }
    /** 设置key */
    public void setKey(String key) { this.key = key; }
    /** 获取value */
    public String getValue() { return value; }
    /** 设置value */
    public void setValue(String value) { this.value = value; }
    /** 获取值类型 */
    public String getValueType() { return valueType; }
    /** 设置值类型 */
    public void setValueType(String valueType) { this.valueType = valueType; }
    /** 获取显示标签 */
    public String getLabel() { return label; }
    /** 设置显示标签 */
    public void setLabel(String label) { this.label = label; }
    /** 获取描述 */
    public String getDescription() { return description; }
    /** 设置描述 */
    public void setDescription(String description) { this.description = description; }
    /** 获取排序 */
    public Integer getSortOrder() { return sortOrder; }
    /** 设置排序 */
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    /** 获取editable */
    public Integer getEditable() { return editable; }
    /** 设置editable */
    public void setEditable(Integer editable) { this.editable = editable; }
    /** 获取更新时间 */
    public String getUpdateTime() { return updateTime; }
    /** 设置更新时间 */
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
}
