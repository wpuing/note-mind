package com.notemind.interfaces.knowledge.vo;

/**
 * 知识库分类保存请求体。
 */
public class KnowledgeCategorySaveRequest {
    private String code;
    private String name;
    private Integer sortNo;
    private Integer status;

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
}
