package com.notemind.interfaces.model.vo;

/**
 * AI 模型配置保存请求体。
 */
public class AiModelConfigSaveRequest {
    private String name;
    private String modelType;
    private String provider;
    private String modelName;
    private String baseUrl;
    private String apiStyle;
    /** 留空表示不修改已有密钥 */
    private String apiKey;
    private Integer dimension;
    private Double temperature;
    private Integer enabled;
    private String remark;

    /** 获取名称 */
    public String getName() { return name; }
    /** 设置名称 */
    public void setName(String name) { this.name = name; }
    /** 获取模型类型 */
    public String getModelType() { return modelType; }
    /** 设置模型类型 */
    public void setModelType(String modelType) { this.modelType = modelType; }
    /** 获取provider */
    public String getProvider() { return provider; }
    /** 设置provider */
    public void setProvider(String provider) { this.provider = provider; }
    /** 获取模型名称 */
    public String getModelName() { return modelName; }
    /** 设置模型名称 */
    public void setModelName(String modelName) { this.modelName = modelName; }
    /** 获取接口基址 */
    public String getBaseUrl() { return baseUrl; }
    /** 设置接口基址 */
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    /** 获取api Style */
    public String getApiStyle() { return apiStyle; }
    /** 设置api Style */
    public void setApiStyle(String apiStyle) { this.apiStyle = apiStyle; }
    /** 获取API 密钥 */
    public String getApiKey() { return apiKey; }
    /** 设置API 密钥 */
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    /** 获取dimension */
    public Integer getDimension() { return dimension; }
    /** 设置dimension */
    public void setDimension(Integer dimension) { this.dimension = dimension; }
    /** 获取temperature */
    public Double getTemperature() { return temperature; }
    /** 设置temperature */
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    /** 获取是否启用 */
    public Integer getEnabled() { return enabled; }
    /** 设置是否启用 */
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    /** 获取备注 */
    public String getRemark() { return remark; }
    /** 设置备注 */
    public void setRemark(String remark) { this.remark = remark; }
}
