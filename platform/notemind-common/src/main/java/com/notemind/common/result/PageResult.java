package com.notemind.common.result;

import java.io.Serializable;
import java.util.List;

/**
 * 通用分页结果包装。
 * <p>
 * 与前端列表分页约定一致：total / page / pageSize / records。
 *
 * @param <T> 记录元素类型
 */
public class PageResult<T> implements Serializable {
    /** 总记录数。 */
    private long total;
    /** 当前页码（从 1 起）。 */
    private int page;
    /** 每页条数。 */
    private int pageSize;
    /** 当前页数据。 */
    private List<T> records;

    /**
     * 工厂方法：一次性组装分页结果。
     *
     * @param total    总条数
     * @param page     页码
     * @param pageSize 页大小
     * @param records  当前页数据
     * @param <T>      元素类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(long total, int page, int pageSize, List<T> records) {
        PageResult<T> r = new PageResult<>();
        r.total = total;
        r.page = page;
        r.pageSize = pageSize;
        r.records = records;
        return r;
    }

    /**
     * 获取总记录数。
     *
     * @return 总数
     */
    public long getTotal() {
        return total;
    }

    /**
     * 获取当前页码。
     *
     * @return 页码
     */
    public int getPage() {
        return page;
    }

    /**
     * 获取每页条数。
     *
     * @return 页大小
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * 获取当前页记录。
     *
     * @return 记录列表
     */
    public List<T> getRecords() {
        return records;
    }
}
