package com.exdemo.order.controller;

import com.exdemo.common.result.R;
import com.exdemo.order.dto.CartAddRequest;
import com.exdemo.order.dto.CartCheckoutRequest;
import com.exdemo.order.dto.CartItemVO;
import com.exdemo.order.entity.Order;
import com.exdemo.order.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车控制器
 */
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /** 加入购物车 */
    @PostMapping("/add")
    public R<Boolean> addToCart(@Valid @RequestBody CartAddRequest request) {
        return cartService.addToCart(request.getUserId(), request.getProductId(), request.getQuantity());
    }

    /** 修改数量 */
    @PutMapping("/quantity")
    public R<Boolean> updateQuantity(@RequestParam Long userId,
                                     @RequestParam Long productId,
                                     @RequestParam Integer quantity) {
        return cartService.updateQuantity(userId, productId, quantity);
    }

    /** 删除购物车项 */
    @DeleteMapping("/remove")
    public R<Boolean> removeFromCart(@RequestParam Long userId, @RequestParam Long productId) {
        return cartService.removeFromCart(userId, productId);
    }

    /** 购物车列表 */
    @GetMapping("/list/{userId}")
    public R<List<CartItemVO>> listCart(@PathVariable Long userId) {
        return cartService.listCart(userId);
    }

    /** 清空购物车 */
    @DeleteMapping("/clear/{userId}")
    public R<Boolean> clearCart(@PathVariable Long userId) {
        return cartService.clearCart(userId);
    }

    /** 结算：购物车 → 订单 */
    @PostMapping("/checkout")
    public R<List<Order>> checkout(@Valid @RequestBody CartCheckoutRequest request) {
        return cartService.checkout(request);
    }
}
