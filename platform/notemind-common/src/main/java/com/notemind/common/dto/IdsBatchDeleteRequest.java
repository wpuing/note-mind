package com.notemind.common.dto;

import java.util.List;

/**
 * 通用批量删除请求体：{@code { "ids": ["..."] }}。
 * <p>
 * 各管理端「多选批量删除」接口共用此 DTO。
 */
public class IdsBatchDeleteRequest {
    /** 待删除的业务 ID 列表。 */
    private List<String> ids;

    /**
     * 获取待删除 ID 列表。
     *
     * @return ID 列表
     */
    public List<String> getIds() {
        return ids;
    }

    /**
     * 设置待删除 ID 列表。
     *
     * @param ids ID 列表
     */
    public void setIds(List<String> ids) {
        this.ids = ids;
    }
}
