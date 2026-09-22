package com.exdemo.admin.dto;

import lombok.Data;

/**
 * 通用分页查询参数（管理端列表统一入参）。
 */
@Data
public class PageQuery {

    /** 页码，从 1 开始 */
    private Integer pageNum = 1;

    /** 每页条数（上限由 PageUtil 统一收敛） */
    private Integer pageSize = 10;

    /** 关键词（模糊搜索，按业务解释） */
    private String keyword;

    /** 数字状态过滤（如用户 1-正常 0-禁用、商品 1-上架 0-下架） */
    private Integer status;

    /**
     * 文本状态过滤（订单状态是 PENDING/FROZEN 这类字符串，无法复用 Integer status）。
     * 单独开一个字段，避免把订单状态硬塞进数字字段导致前端传值永远绑定不上。
     */
    private String statusText;

    /** 起始时间（支持 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss） */
    private String startTime;

    /** 结束时间（只传日期时，自动补到当天 23:59:59） */
    private String endTime;
}
