package com.exdemo.admin.feign;

import com.exdemo.admin.dto.OrderDTO;
import com.exdemo.admin.feign.fallback.OrderFeignFallback;
import com.exdemo.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "order-service", fallback = OrderFeignFallback.class)
public interface OrderFeignClient {

    /** 订单详情 */
    @GetMapping("/order/get/{id}")
    R<OrderDTO> getOrderById(@PathVariable("id") Long id);

    /** 某用户的订单列表（买家维度） */
    @GetMapping("/order/list/user/{userId}")
    R<List<OrderDTO>> listOrdersByUser(@PathVariable("userId") Long userId);

    /** 全量订单（管理端专用，status 为空表示不过滤） */
    @GetMapping("/order/admin/list")
    R<List<OrderDTO>> listAllOrders(@RequestParam(value = "status", required = false) String status);

    /** 后台发货（管理端专用，FROZEN → SHIPPED） */
    @PostMapping("/order/admin/ship")
    R<Boolean> adminShip(@RequestParam("orderId") Long orderId);

    /** 后台强制关单（管理端专用） */
    @PostMapping("/order/admin/cancel")
    R<Boolean> adminCancel(@RequestParam("orderId") Long orderId,
                           @RequestParam(value = "reason", required = false) String reason);

    /** 后台介入退款（管理端专用） */
    @PostMapping("/order/admin/refund")
    R<Boolean> adminRefund(@RequestParam("orderId") Long orderId,
                           @RequestParam(value = "reason", required = false) String reason);
}
