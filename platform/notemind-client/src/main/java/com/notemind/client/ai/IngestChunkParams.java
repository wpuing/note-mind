package com.notemind.client.ai;

/**
 * 入库切分参数（由知识库策略或上传指定解析而来）。
 * <p>
 * 透传给 Python 切分管线，支持递归分块与父子分块。
 */
public class IngestChunkParams {
    /** 递归分块大小。 */
    private Integer chunkSize;
    /** 递归分块重叠。 */
    private Integer chunkOverlap;
    /** 策略类型：recursive / parent_child 等。 */
    private String strategyType;
    /** 父块大小（父子策略）。 */
    private Integer parentChunkSize;
    /** 子块大小（父子策略）。 */
    private Integer childChunkSize;
    /** 子块重叠。 */
    private Integer childOverlap;
    /** 分隔符 JSON 数组字符串。 */
    private String separatorsJson;
    /** 切分策略业务 ID。 */
    private String chunkStrategyId;

    /**
     * 获取递归分块大小。
     *
     * @return chunkSize
     */
    public Integer getChunkSize() { return chunkSize; }

    /**
     * 设置递归分块大小。
     *
     * @param chunkSize 分块大小
     */
    public void setChunkSize(Integer chunkSize) { this.chunkSize = chunkSize; }

    /**
     * 获取递归分块重叠。
     *
     * @return overlap
     */
    public Integer getChunkOverlap() { return chunkOverlap; }

    /**
     * 设置递归分块重叠。
     *
     * @param chunkOverlap 重叠长度
     */
    public void setChunkOverlap(Integer chunkOverlap) { this.chunkOverlap = chunkOverlap; }

    /**
     * 获取策略类型。
     *
     * @return 策略类型
     */
    public String getStrategyType() { return strategyType; }

    /**
     * 设置策略类型。
     *
     * @param strategyType 策略类型
     */
    public void setStrategyType(String strategyType) { this.strategyType = strategyType; }

    /**
     * 获取父块大小。
     *
     * @return 父块大小
     */
    public Integer getParentChunkSize() { return parentChunkSize; }

    /**
     * 设置父块大小。
     *
     * @param parentChunkSize 父块大小
     */
    public void setParentChunkSize(Integer parentChunkSize) { this.parentChunkSize = parentChunkSize; }

    /**
     * 获取子块大小。
     *
     * @return 子块大小
     */
    public Integer getChildChunkSize() { return childChunkSize; }

    /**
     * 设置子块大小。
     *
     * @param childChunkSize 子块大小
     */
    public void setChildChunkSize(Integer childChunkSize) { this.childChunkSize = childChunkSize; }

    /**
     * 获取子块重叠。
     *
     * @return 子块重叠
     */
    public Integer getChildOverlap() { return childOverlap; }

    /**
     * 设置子块重叠。
     *
     * @param childOverlap 子块重叠
     */
    public void setChildOverlap(Integer childOverlap) { this.childOverlap = childOverlap; }

    /**
     * 获取分隔符 JSON。
     *
     * @return separatorsJson
     */
    public String getSeparatorsJson() { return separatorsJson; }

    /**
     * 设置分隔符 JSON。
     *
     * @param separatorsJson JSON 字符串
     */
    public void setSeparatorsJson(String separatorsJson) { this.separatorsJson = separatorsJson; }

    /**
     * 获取切分策略 ID。
     *
     * @return 策略 ID
     */
    public String getChunkStrategyId() { return chunkStrategyId; }

    /**
     * 设置切分策略 ID。
     *
     * @param chunkStrategyId 策略 ID
     */
    public void setChunkStrategyId(String chunkStrategyId) { this.chunkStrategyId = chunkStrategyId; }
}
