package com.exdemo.admin.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 通用分页返回结构。
 *
 * <p>全站统一一个分页壳，前端只写一套表格渲染逻辑，不用为每个接口适配字段名。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 总条数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private long pageNum;

    /** 每页条数 */
    private long pageSize;

    /** 当前页数据 */
    private List<T> records;

    /** 由 MyBatis-Plus 分页对象转换 */
    public static <T> PageResult<T> of(IPage<T> page) {
        if (page == null) {
            return empty(1, 0);
        }
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    /** 空结果 */
    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return new PageResult<>(0L, pageNum, pageSize, Collections.emptyList());
    }
}
