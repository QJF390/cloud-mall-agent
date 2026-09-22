package com.exdemo.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.exdemo.common.result.R;
import com.exdemo.order.dto.CreateOrderRequest;
import com.exdemo.order.entity.Order;
import com.exdemo.order.entity.OrderItem;

import java.util.List;

/**
 * 订单业务接口
 */
public interface OrderService extends IService<Order> {

    /**
     * 下单：多商品版（B2C 自营，一单可含多明细）
     * 由购物车 checkout 调用，一次结算只生成一张订单。
     */
    R<Order> createOrder(CreateOrderRequest request);

    /** 下单：单商品版（旧接口，保留兼容） */
    @Deprecated
    R<Order> createOrder(Long userId, Long productId, Integer quantity);

    /** 按ID查订单 */
    R<Order> getOrderById(Long id);

    /** 按用户ID查订单列表（B2C：即本人作为买家的订单） */
    R<List<Order>> listOrdersByUserId(Long userId);

    /** 我买到的订单（WHERE buyer_id = ?） */
    R<List<Order>> listBuyOrders(Long userId);

    /** 批量查询订单明细（商品快照），按 orderIds 一次取回 */
    R<List<OrderItem>> listItemsByOrderIds(List<Long> orderIds);

    /** 生成订单号 */
    String generateOrderNo(Long userId);

    // ==================== 担保交易状态机 ====================

    /** 取消订单（PENDING/FROZEN → CANCELLED）：回补库存 + 解冻资金 + 写资金流水 */
    R<Boolean> cancelOrder(Long userId, Long orderId);

    /** 平台发货（FROZEN → SHIPPED）：B2C 自营由平台运营操作，用户侧无此入口 */
    R<Boolean> shipOrder(Long orderId);

    /** 买家确认收货（SHIPPED → RECEIVED → COMPLETED）：触发平台打款给卖家 */
    R<Boolean> receiveOrder(Long buyerId, Long orderId);

    /** 退款（SHIPPED/RECEIVED → REFUNDED）：回补库存 + 资金原路退回 + 写资金流水 */
    R<Boolean> refundOrder(Long userId, Long orderId, String reason);

    // ==================== 管理端专用（仅由 admin-service 经 Feign 调用） ====================

    /** 全量订单（管理端）：status 为空表示不过滤 */
    R<List<Order>> listAllOrders(String status);

    /** 后台强制关单（不校验买家归属，管理员鉴权与审计在 admin-service 完成） */
    R<Boolean> adminCancel(Long orderId, String reason);

    /** 后台介入退款（不校验买家归属） */
    R<Boolean> adminRefund(Long orderId, String reason);
}
