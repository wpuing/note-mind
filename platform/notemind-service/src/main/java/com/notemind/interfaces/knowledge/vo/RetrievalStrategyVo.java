package com.notemind.interfaces.knowledge.vo;

import java.math.BigDecimal;

/**
 * 检索策略视图对象。
 */
public class RetrievalStrategyVo {
    private String id;
    private String name;
    private Integer enableVector;
    private Integer enableBm25;
    private Integer enableRrf;
    private Integer enableRerank;
    private Integer enableRewrite;
    private Integer enableParentFill;
    private Integer topK;
    private Integer rerankTopN;
    private Integer vectorTopK;
    private Integer bm25TopK;
    private Integer rrfK;
    private BigDecimal cosineThreshold;
    private BigDecimal rerankThreshold;
    private String rewriteMode;
    private Integer rewriteCount;
    private Integer enabled;
    private Integer isDefault;
    private String remark;
    private String createTime;
    private Long refCount;

    /** 获取主键 ID */
    public String getId() { return id; }
    /** 设置主键 ID */
    public void setId(String id) { this.id = id; }
    /** 获取名称 */
    public String getName() { return name; }
    /** 设置名称 */
    public void setName(String name) { this.name = name; }
    /** 获取enable Vector */
    public Integer getEnableVector() { return enableVector; }
    /** 设置enable Vector */
    public void setEnableVector(Integer enableVector) { this.enableVector = enableVector; }
    /** 获取enable Bm25 */
    public Integer getEnableBm25() { return enableBm25; }
    /** 设置enable Bm25 */
    public void setEnableBm25(Integer enableBm25) { this.enableBm25 = enableBm25; }
    /** 获取enable Rrf */
    public Integer getEnableRrf() { return enableRrf; }
    /** 设置enable Rrf */
    public void setEnableRrf(Integer enableRrf) { this.enableRrf = enableRrf; }
    /** 获取enable Rerank */
    public Integer getEnableRerank() { return enableRerank; }
    /** 设置enable Rerank */
    public void setEnableRerank(Integer enableRerank) { this.enableRerank = enableRerank; }
    /** 获取enable Rewrite */
    public Integer getEnableRewrite() { return enableRewrite; }
    /** 设置enable Rewrite */
    public void setEnableRewrite(Integer enableRewrite) { this.enableRewrite = enableRewrite; }
    /** 获取enable Parent Fill */
    public Integer getEnableParentFill() { return enableParentFill; }
    /** 设置enable Parent Fill */
    public void setEnableParentFill(Integer enableParentFill) { this.enableParentFill = enableParentFill; }
    /** 获取TopK */
    public Integer getTopK() { return topK; }
    /** 设置TopK */
    public void setTopK(Integer topK) { this.topK = topK; }
    /** 获取rerank Top N */
    public Integer getRerankTopN() { return rerankTopN; }
    /** 设置rerank Top N */
    public void setRerankTopN(Integer rerankTopN) { this.rerankTopN = rerankTopN; }
    /** 获取vector Top K */
    public Integer getVectorTopK() { return vectorTopK; }
    /** 设置vector Top K */
    public void setVectorTopK(Integer vectorTopK) { this.vectorTopK = vectorTopK; }
    /** 获取bm25 Top K */
    public Integer getBm25TopK() { return bm25TopK; }
    /** 设置bm25 Top K */
    public void setBm25TopK(Integer bm25TopK) { this.bm25TopK = bm25TopK; }
    /** 获取rrf K */
    public Integer getRrfK() { return rrfK; }
    /** 设置rrf K */
    public void setRrfK(Integer rrfK) { this.rrfK = rrfK; }
    /** 获取cosine Threshold */
    public BigDecimal getCosineThreshold() { return cosineThreshold; }
    /** 设置cosine Threshold */
    public void setCosineThreshold(BigDecimal cosineThreshold) { this.cosineThreshold = cosineThreshold; }
    /** 获取rerank Threshold */
    public BigDecimal getRerankThreshold() { return rerankThreshold; }
    /** 设置rerank Threshold */
    public void setRerankThreshold(BigDecimal rerankThreshold) { this.rerankThreshold = rerankThreshold; }
    /** 获取rewrite Mode */
    public String getRewriteMode() { return rewriteMode; }
    /** 设置rewrite Mode */
    public void setRewriteMode(String rewriteMode) { this.rewriteMode = rewriteMode; }
    /** 获取rewrite Count */
    public Integer getRewriteCount() { return rewriteCount; }
    /** 设置rewrite Count */
    public void setRewriteCount(Integer rewriteCount) { this.rewriteCount = rewriteCount; }
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
    /** 获取创建时间 */
    public String getCreateTime() { return createTime; }
    /** 设置创建时间 */
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    /** 获取ref Count */
    public Long getRefCount() { return refCount; }
    /** 设置ref Count */
    public void setRefCount(Long refCount) { this.refCount = refCount; }
}
