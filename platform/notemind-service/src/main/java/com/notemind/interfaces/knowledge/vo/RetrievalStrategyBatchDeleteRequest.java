package com.notemind.interfaces.knowledge.vo;

import java.util.List;

/**
 * 检索策略批量删除请求体。
 */
public class RetrievalStrategyBatchDeleteRequest {
    private List<String> ids;

    /** 获取ID 列表 */
    public List<String> getIds() {
        return ids;
    }

    /** 设置ID 列表 */
    public void setIds(List<String> ids) {
        this.ids = ids;
    }
}
