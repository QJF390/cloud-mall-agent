package com.exdemo.admin.controller;

import com.exdemo.admin.annotation.AdminOperation;
import com.exdemo.admin.dto.OrderDTO;
import com.exdemo.admin.dto.PageQuery;
import com.exdemo.admin.dto.PageResult;
import com.exdemo.admin.service.AdminOrderService;
import com.exdemo.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 订单管理接口。
 *
 * <p>网关路由：/admin/** → admin-service；本类映射 /admin/order。</p>
 */
@RestController
@RequestMapping("/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    /** 订单列表（分页 + 状态/时间筛选） */
    @GetMapping("/page")
    public R<PageResult<OrderDTO>> page(PageQuery query) {
        return adminOrderService.pageOrders(query);
    }

    /** 订单详情 */
    @GetMapping("/{id}")
    public R<OrderDTO> detail(@PathVariable Long id) {
        return adminOrderService.getOrder(id);
    }

    /** 发货（FROZEN → SHIPPED） */
    @AdminOperation(module = "ORDER", action = "发货")
    @PostMapping("/{id}/ship")
    public R<Void> ship(@PathVariable Long id) {
        return adminOrderService.ship(id);
    }

    /** 强制关单 */
    @AdminOperation(module = "ORDER", action = "强制关单")
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return adminOrderService.forceCancel(id, reason);
    }

    /** 后台介入退款 */
    @AdminOperation(module = "ORDER", action = "后台退款")
    @PostMapping("/{id}/refund")
    public R<Void> refund(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return adminOrderService.refund(id, reason);
    }
}
