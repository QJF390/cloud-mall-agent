package com.exdemo.admin.controller;

import com.exdemo.admin.service.AdminDashboardService;
import com.exdemo.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 控制台概览接口。
 *
 * <p>网关路由：/admin/** → admin-service；本类映射 /admin/dashboard。</p>
 */
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    /** 控制台统计概览（只读，无需审计） */
    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        return adminDashboardService.stats();
    }

    /**
     * 近 N 天销量 / 成交额趋势（默认 7 天，上限 30）。
     * 只读接口；days 做钳制，防止一次拉爆内存。
     */
    @GetMapping("/trend")
    public R<Map<String, Object>> trend(@RequestParam(defaultValue = "7") int days) {
        return adminDashboardService.trend(days);
    }
}
