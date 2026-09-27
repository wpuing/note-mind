package com.notemind.domain.shared;

import java.util.Collections;
import java.util.List;

/**
 * 领域分页结果（不含 HTTP / Result 包装语义）。
 * <p>records 在构造时拷贝为不可变快照，对外只读。</p>
 *
 * @param <T> 记录元素类型
 */
public class PageData<T> {
    /** 总记录数 */
    private final long total;
    /** 当前页码（从 1 起） */
    private final int page;
    /** 每页条数 */
    private final int pageSize;
    /** 当前页记录列表（不可变） */
    private final List<T> records;

    /**
     * 构造分页结果；records 为 null 时视为空列表。
     *
     * @param total    总记录数
     * @param page     当前页码
     * @param pageSize 每页条数
     * @param records  当前页数据，可为 null
     */
    public PageData(long total, int page, int pageSize, List<T> records) {
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        // null 记为空列表，否则拷贝为不可变快照
        this.records = records == null ? List.of() : List.copyOf(records);
    }

    /** @return 总记录数 */ public long getTotal() { return total; }
    /** @return 当前页码 */ public int getPage() { return page; }
    /** @return 每页条数 */ public int getPageSize() { return pageSize; }
    /** @return 只读记录列表 */ public List<T> getRecords() { return Collections.unmodifiableList(records); }
}
