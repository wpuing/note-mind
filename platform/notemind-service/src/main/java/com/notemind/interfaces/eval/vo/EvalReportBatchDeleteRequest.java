package com.notemind.interfaces.eval.vo;

import java.util.List;

/**
 * 评测报告批量删除请求体。
 */
public class EvalReportBatchDeleteRequest {
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
