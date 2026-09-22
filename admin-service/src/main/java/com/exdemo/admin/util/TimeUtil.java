package com.exdemo.admin.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 时间区间解析工具。
 *
 * <p>前端 {@code <input type="date">} 只会传 {@code 2026-09-01}，
 * 如果直接把 00:00:00 当成结束时间，"9月1日"的订单会全部查不到 ——
 * 这是后台筛选功能最经典的 bug。</p>
 */
public final class TimeUtil {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter SECOND_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TimeUtil() {
    }

    /** 解析起始时间：只给日期时从当天 00:00:00 开始 */
    public static LocalDateTime parseStart(String text) {
        LocalDateTime time = parse(text);
        if (time == null) {
            return null;
        }
        return isDateOnly(text) ? time.toLocalDate().atStartOfDay() : time;
    }

    /** 解析结束时间：只给日期时补到当天 23:59:59，保证"闭区间"语义 */
    public static LocalDateTime parseEnd(String text) {
        LocalDateTime time = parse(text);
        if (time == null) {
            return null;
        }
        return isDateOnly(text) ? time.toLocalDate().atTime(23, 59, 59) : time;
    }

    /** 判断某个时间点是否落在 [start, end] 区间内（边界为 null 表示该侧不限） */
    public static boolean inRange(LocalDateTime time, LocalDateTime start, LocalDateTime end) {
        if (time == null) {
            return start == null && end == null;
        }
        if (start != null && time.isBefore(start)) {
            return false;
        }
        return end == null || !time.isAfter(end);
    }

    private static boolean isDateOnly(String text) {
        return text != null && text.trim().length() <= 10;
    }

    /** 宽松解析，格式不对返回 null（由业务层决定是忽略还是报错） */
    private static LocalDateTime parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        try {
            if (value.length() <= 10) {
                return LocalDate.parse(value, DATE_FMT).atStartOfDay();
            }
            if (value.length() <= 16) {
                return LocalDateTime.parse(value, MINUTE_FMT);
            }
            return LocalDateTime.parse(value, SECOND_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
