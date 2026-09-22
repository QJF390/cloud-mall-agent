package com.exdemo.admin.util;

import com.exdemo.admin.dto.PageQuery;
import com.exdemo.admin.dto.PageResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PageUtil {

    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 单页上限，防止前端传 pageSize=100000 直接把内存打爆 */
    private static final int MAX_PAGE_SIZE = 200;

    private PageUtil() {
    }

    public static int normalizePageNum(Integer pageNum) {
        return (pageNum == null || pageNum < 1) ? 1 : pageNum;
    }

    public static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 对已过滤/排序好的列表做分页切片。
     */
    public static <T> PageResult<T> paginate(List<T> source, PageQuery query) {
        int pageNum = normalizePageNum(query == null ? null : query.getPageNum());
        int pageSize = normalizePageSize(query == null ? null : query.getPageSize());

        List<T> safe = (source == null) ? Collections.emptyList() : source;
        long total = safe.size();

        // 用 long 计算偏移量，避免 pageNum 极大时 int 溢出成负数
        long offset = (long) (pageNum - 1) * pageSize;
        int from = (int) Math.min(offset, safe.size());
        int to = (int) Math.min(offset + pageSize, safe.size());

        List<T> records = (from >= to)
                ? Collections.emptyList()
                : new ArrayList<>(safe.subList(from, to));

        return new PageResult<>(total, pageNum, pageSize, records);
    }
}
