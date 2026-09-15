package com.notemind.interfaces.knowledge.vo;

import java.util.List;

/**
 * 切分策略保存请求体。
 */
public class ChunkStrategySaveRequest {
    private String name;
    private String strategyType;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Integer parentChunkSize;
    private Integer childChunkSize;
    private Integer childOverlap;
    private List<String> separators;
    private Integer enabled;
    private Integer isDefault;
    private String remark;

    /** 获取名称 */
    public String getName() { return name; }
    /** 设置名称 */
    public void setName(String name) { this.name = name; }
    /** 获取策略类型 */
    public String getStrategyType() { return strategyType; }
    /** 设置策略类型 */
    public void setStrategyType(String strategyType) { this.strategyType = strategyType; }
    /** 获取chunk Size */
    public Integer getChunkSize() { return chunkSize; }
    /** 设置chunk Size */
    public void setChunkSize(Integer chunkSize) { this.chunkSize = chunkSize; }
    /** 获取chunk Overlap */
    public Integer getChunkOverlap() { return chunkOverlap; }
    /** 设置chunk Overlap */
    public void setChunkOverlap(Integer chunkOverlap) { this.chunkOverlap = chunkOverlap; }
    /** 获取parent Chunk Size */
    public Integer getParentChunkSize() { return parentChunkSize; }
    /** 设置parent Chunk Size */
    public void setParentChunkSize(Integer parentChunkSize) { this.parentChunkSize = parentChunkSize; }
    /** 获取child Chunk Size */
    public Integer getChildChunkSize() { return childChunkSize; }
    /** 设置child Chunk Size */
    public void setChildChunkSize(Integer childChunkSize) { this.childChunkSize = childChunkSize; }
    /** 获取child Overlap */
    public Integer getChildOverlap() { return childOverlap; }
    /** 设置child Overlap */
    public void setChildOverlap(Integer childOverlap) { this.childOverlap = childOverlap; }
    /** 获取separators */
    public List<String> getSeparators() { return separators; }
    /** 设置separators */
    public void setSeparators(List<String> separators) { this.separators = separators; }
    /** 获取是否启用 */
    public Integer getEnabled() { return enabled; }
    /** 设置是否启用 */
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    /** 获取是否默认 */
    public Integer getIsDefault() { return isDefault; }
    /** 设置是否默认 */
    public void setIsDefault(Integer isDefault) { this.isDefault = isDefault; }
    /** 获取备注 */
    public String getRemark() { return remark; }
    /** 设置备注 */
    public void setRemark(String remark) { this.remark = remark; }
}
