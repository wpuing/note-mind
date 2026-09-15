package com.notemind.interfaces.system.vo;

import java.util.List;

/**
 * 系统配置批量保存请求体。
 */
public class SystemConfigBatchSaveRequest {
    private List<Item> items;

    /** 获取配置项列表 */
    public List<Item> getItems() { return items; }
    /** 设置配置项列表 */
    public void setItems(List<Item> items) { this.items = items; }

    /**
     * 单条配置项（键值对）。
     */
    public static class Item {
        private String key;
        private String value;

        /** 获取配置键 */
        public String getKey() { return key; }
        /** 设置配置键 */
        public void setKey(String key) { this.key = key; }
        /** 获取配置值 */
        public String getValue() { return value; }
        /** 设置配置值 */
        public void setValue(String value) { this.value = value; }
    }
}
