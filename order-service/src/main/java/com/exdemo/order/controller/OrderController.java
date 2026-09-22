package com.exdemo.order.controller;

import com.exdemo.common.result.R;
import com.exdemo.order.dto.CreateOrderRequest;
import com.exdemo.order.entity.Order;
import com.exdemo.order.entity.OrderItem;
import com.exdemo.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单控制器
 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 下单（多商品版：支持一单多明细） */
    @PostMapping("/create")
    public R<Order> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    /** 按ID查订单 */
    @GetMapping("/get/{id}")
    public R<Order> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id);
    }

    /** 按用户ID查订单列表（B2C：即本人订单） */
    @GetMapping("/list/user/{userId}")
    public R<List<Order>> listOrdersByUserId(@PathVariable Long userId) {
        return orderService.listOrdersByUserId(userId);
    }

    /** 我买到的订单（buyer_id = userId） */
    @GetMapping("/list/buy/{userId}")
    public R<List<Order>> listBuyOrders(@PathVariable Long userId) {
        return orderService.listBuyOrders(userId);
    }

    /**
     * 批量查询订单明细（商品快照）
     * 一单多商品，按 orderIds 一次性取回，避免前端 N+1 逐单查询。
     */
    @GetMapping("/items")
    public R<List<OrderItem>> listOrderItems(@RequestParam List<Long> orderIds) {
        return orderService.listItemsByOrderIds(orderIds);
    }

    // ==================== 担保交易状态机接口 ====================

    /** 买家取消订单 */
    @PostMapping("/cancel")
    public R<Boolean> cancelOrder(@RequestParam Long userId, @RequestParam Long orderId) {
        return orderService.cancelOrder(userId, orderId);
    }

    @PostMapping("/admin/ship")
    public R<Boolean> shipOrder(@RequestParam Long orderId) {
        return orderService.shipOrder(orderId);
    }

    /** 买家确认收货 */
    @PostMapping("/receive")
    public R<Boolean> receiveOrder(@RequestParam Long buyerId, @RequestParam Long orderId) {
        return orderService.receiveOrder(buyerId, orderId);
    }

    /** 买家申请退款 */
    @PostMapping("/refund")
    public R<Boolean> refundOrder(@RequestParam Long userId,
                                  @RequestParam Long orderId,
                                  @RequestParam String reason) {
        return orderService.refundOrder(userId, orderId, reason);
    }

    // ==================== 管理端专用（仅由 admin-service 经 Feign 调用，网关侧禁止外部直连） ====================

    /** 全量订单（管理端）：status 为空表示不过滤 */
    @GetMapping("/admin/list")
    public R<List<Order>> listAllOrders(@RequestParam(required = false) String status) {
        return orderService.listAllOrders(status);
    }

    /** 后台强制关单 */
    @PostMapping("/admin/cancel")
    public R<Boolean> adminCancel(@RequestParam Long orderId,
                                  @RequestParam(required = false) String reason) {
        return orderService.adminCancel(orderId, reason);
    }

    /** 后台介入退款 */
    @PostMapping("/admin/refund")
    public R<Boolean> adminRefund(@RequestParam Long orderId,
                                  @RequestParam(required = false) String reason) {
        return orderService.adminRefund(orderId, reason);
    }
}
