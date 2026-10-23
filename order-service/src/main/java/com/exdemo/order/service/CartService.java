package com.exdemo.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.exdemo.common.result.R;
import com.exdemo.order.dto.CartCheckoutRequest;
import com.exdemo.order.dto.CartItemVO;
import com.exdemo.order.entity.Cart;
import com.exdemo.order.entity.Order;

import java.util.List;

public interface CartService extends IService<Cart> {

    /** 加入购物车：已存在则数量累加（并发下用 INSERT ... ON DUPLICATE KEY UPDATE 或先查后更） */
    R<Boolean> addToCart(Long userId, Long productId, Integer quantity);

    /** 修改数量（quantity <= 0 视为删除） */
    R<Boolean> updateQuantity(Long userId, Long productId, Integer quantity);

    /** 删除购物车中某个商品 */
    R<Boolean> removeFromCart(Long userId, Long productId);

    /** 查询用户购物车列表（含商品信息快照） */
    R<List<CartItemVO>> listCart(Long userId);

    /** 清空用户购物车 */
    R<Boolean> clearCart(Long userId);

    /**
     * 购物车结算：多商品 → 一个订单。
     * <p>B2C 自营模式下平台是唯一卖方，一次结算只生成一张订单，无需按卖家拆单。
     * 若后续改造为 C2C 多卖家，需在此按 sellerId 分组拆单，每单只写一个 seller_id。</p>
     */
    R<List<Order>> checkout(CartCheckoutRequest request);
}
