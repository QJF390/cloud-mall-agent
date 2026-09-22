package com.exdemo.admin.service;

import com.exdemo.common.result.R;

import java.util.Map;

/**
 * 控制台统计服务（聚合 user / product / order 三个下游的概览数据）。
 */
public interface AdminDashboardService {

    /**
     * 控制台概览统计。
     *
     * <p>返回结构（Map）：userCount / productCount / orderCount / todayAmount /
     * pendingOrderCount / degraded（是否有下游不可用）。</p>
     */
    R<Map<String, Object>> stats();

    /**
     * 近 N 天销量 / 成交额趋势（按天聚合，只统计已付款订单）。
     *
     * <p>返回结构（Map）：dates（日期轴）/ orderCount（每日销量单数）/
     * amount（每日成交额，单位元）/ degraded。</p>
     */
    R<Map<String, Object>> trend(int days);
}
