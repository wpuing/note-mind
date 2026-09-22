package com.notemind.interfaces.knowledge.vo;

/**
 * 知识库分类视图对象。
 */
public class KnowledgeCategoryVo {
    private String id;
    private String code;
    private String name;
    private Integer sortNo;
    private Integer status;
    private String createTime;
    /** 被多少未删除知识库引用 */
    private Long refCount;

    /** 获取主键 ID */
    public String getId() {
        return id;
    }

    /** 设置主键 ID */
    public void setId(String id) {
        this.id = id;
    }

    /** 获取编码 */
    public String getCode() {
        return code;
    }

    /** 设置编码 */
    public void setCode(String code) {
        this.code = code;
    }

    /** 获取名称 */
    public String getName() {
        return name;
    }

    /** 设置名称 */
    public void setName(String name) {
        this.name = name;
    }

    /** 获取sort No */
    public Integer getSortNo() {
        return sortNo;
    }

    /** 设置sort No */
    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    /** 获取状态 */
    public Integer getStatus() {
        return status;
    }

    /** 设置状态 */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /** 获取创建时间 */
    public String getCreateTime() {
        return createTime;
    }

    /** 设置创建时间 */
    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    /** 获取ref Count */
    public Long getRefCount() {
        return refCount;
    }

    /** 设置ref Count */
    public void setRefCount(Long refCount) {
        this.refCount = refCount;
    }
}
