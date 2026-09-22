package com.notemind.common.util;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 统一对外时间字符串工具：格式 {@code yyyy-MM-dd HH:mm:ss}。
 * <p>
 * 避免各模块各自格式化导致前后端展示不一致。
 */
public final class DateTimes {
    /** 统一时间格式化器。 */
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 工具类禁止实例化。
     */
    private DateTimes() {}

    /**
     * 将 JDBC Timestamp 格式化为统一时间字符串。
     *
     * @param ts 时间戳，可为 null
     * @return 格式化字符串；入参为 null 时返回 null
     */
    public static String format(Timestamp ts) {
        // 空时间直接返回 null，避免 NPE
        if (ts == null) return null;
        return format(ts.toLocalDateTime());
    }

    /**
     * 将 LocalDateTime 格式化为统一时间字符串。
     *
     * @param dt 本地日期时间，可为 null
     * @return 格式化字符串；入参为 null 时返回 null
     */
    public static String format(LocalDateTime dt) {
        // 空时间直接返回 null，避免 NPE
        if (dt == null) return null;
        return dt.format(FMT);
    }
}
