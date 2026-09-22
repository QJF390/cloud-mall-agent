package com.exdemo.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.exdemo.common.result.R;
import com.exdemo.order.dto.CartCheckoutRequest;
import com.exdemo.order.dto.CartItemVO;
import com.exdemo.order.dto.CreateOrderRequest;
import com.exdemo.order.dto.OrderItemReq;
import com.exdemo.order.dto.ProductDTO;
import com.exdemo.order.entity.Cart;
import com.exdemo.order.entity.Order;
import com.exdemo.order.feign.ProductFeignClient;
import com.exdemo.order.mapper.CartMapper;
import com.exdemo.order.service.CartService;
import com.exdemo.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements CartService {
    private final ProductFeignClient productFeignClient;
    private final OrderService orderService;

    @Override
    public R<Boolean> addToCart(Long userId, Long productId, Integer quantity) {

        //    （利用 uk_user_product 唯一键做原子 upsert）
        // 1. 参数校验（userId/productId/quantity 非空且 > 0）
        if (userId == null || productId == null || quantity == null || quantity <= 0) {
            return R.badRequest("参数错误");
        }
        // 2. 校验商品存在且已上架：防止"下架商品"绕过前端被直接加购
        //    直接复用 C 端详情接口——它已把"已下架"归一到"不存在"，规则收口到一处，下游白捡
        R<ProductDTO> productR = productFeignClient.getProductById(productId);
        if (productR == null || productR.getCode() != 200 || productR.getData() == null) {
            return R.fail(productR != null && productR.getMessage() != null
                    ? productR.getMessage() : "商品不存在或已下架");
        }
        // 3. 查 t_cart 是否存在 (userId, productId)：
        Cart cart =new Cart();
        cart.setId(IdWorker.getId());
        cart.setUserId(userId);
        cart.setProductId(productId);
        cart.setQuantity(quantity);
        try {
            // 注意方法名大小写：insertOrUpdate，不是 insertorUpdate
            int rows = baseMapper.insertorUpdate(cart);
            if (rows > 0) {
                return R.ok(true);
            }
            return R.fail("添加购物车失败");
        } catch (Exception e) {
            log.error("addToCart failed, userId={}, productId={}, quantity={}",
                    userId, productId, quantity, e);
            return R.fail("添加购物车失败");
        }

    }

    @Override
    public R<Boolean> updateQuantity(Long userId, Long productId, Integer quantity) {
        // TODO[实现]：校验 quantity > 0 后 updateById / 或数量改为 0 时直接删除
        if (userId == null || productId == null || quantity == null || quantity < 0) {
            return R.badRequest("参数错误");
        }
        if (quantity > 99) {
            return R.fail("数量不能超过 99");
        }
//        当有条件的更新数据库可以用wapper和lambedb
        boolean success;
        if (quantity == 0) {
            // 删除：Lambda 条件，编译期安全；返回 boolean 语义清晰
            success = remove(
                    Wrappers.<Cart>lambdaQuery()
                            .eq(Cart::getUserId, userId)
                            .eq(Cart::getProductId, productId)
            );
        } else {
            success = update(
                    Wrappers.<Cart>lambdaUpdate()
                            .eq(Cart::getUserId, userId)
                            .eq(Cart::getProductId, productId)
                            .set(Cart::getQuantity, quantity)
                            .set(Cart::getUpdateTime, LocalDateTime.now())
            );
        }
        return success ? R.ok(true) : R.fail("购物车中无该商品");
    }

    @Override
    public R<Boolean> removeFromCart(Long userId, Long productId) {
        // TODO[实现]：DELETE FROM t_cart WHERE user_id=? AND product_id=?
        //            注意删除条件要带 userId，防止越权删别人的购物车
        boolean success = remove(
                Wrappers.<Cart>lambdaQuery()
                        .eq(Cart::getUserId, userId)
                        .eq(Cart::getProductId, productId)
        );
        return success ? R.ok(true) : R.fail("购物车中无该商品");
    }

    @Override
    public R<List<CartItemVO>> listCart(Long userId) {
        // 1. 查当前用户购物车记录（按 update_time 倒序）
        List<Cart> cartList = list(
                Wrappers.<Cart>lambdaQuery()
                        .eq(Cart::getUserId, userId)
                        .orderByDesc(Cart::getUpdateTime)
        );
        if (cartList == null || cartList.isEmpty()) {
            return R.ok(Collections.emptyList());
        }

        // 2. 收集所有 productId，一次 Feign 批量查询（避免 N+1 循环单查）
        List<Long> productIdList = cartList.stream()
                .map(Cart::getProductId)
                .distinct()
                .toList();

        R<List<ProductDTO>> productR = productFeignClient.listByIds(productIdList);
        List<ProductDTO> productList = (productR != null && productR.getData() != null)
                ? productR.getData() : Collections.emptyList();
        Map<Long, ProductDTO> productMap = productList.stream()
                .collect(Collectors.toMap(ProductDTO::getId, p -> p));

        // 3. 组装 VO（商品已下架/不存在 → 标记失效，不能静默丢掉）
        List<CartItemVO> result = new ArrayList<>();
        for (Cart cart : cartList) {
            CartItemVO vo = new CartItemVO();
            vo.setCartId(cart.getId());
            vo.setProductId(cart.getProductId());
            vo.setQuantity(cart.getQuantity());
            vo.setCreateTime(cart.getCreateTime());
            vo.setUpdateTime(cart.getUpdateTime());

            ProductDTO product = productMap.get(cart.getProductId());
            if (product != null && Integer.valueOf(1).equals(product.getStatus())) {
                vo.setProductName(product.getName());
                vo.setCoverImage(product.getCoverImage());
                vo.setUnitPrice(product.getPrice());
                vo.setSellerId(product.getSellerId());
                // 小计 = 单价 × 数量
                if (product.getPrice() != null) {
                    vo.setTotalAmount(product.getPrice().multiply(BigDecimal.valueOf(cart.getQuantity())));
                }
            } else {
                // 商品不存在或已下架 → 标记失效，前端置灰提示
                vo.setInvalid(true);
            }
            result.add(vo);
        }
        return R.ok(result);
    }

    @Override
    public R<Boolean> clearCart(Long userId) {
        // TODO[实现]：DELETE FROM t_cart WHERE user_id=?
        Wrapper<Cart> wrapper = Wrappers.<Cart>lambdaQuery()
                .eq(Cart::getUserId, userId);
        boolean success = remove(wrapper);
        return success ? R.ok(true) : R.fail("购物车中无商品");
    }

    @Override
    public R<List<Order>> checkout(CartCheckoutRequest request) {
        Long userId = request.getUserId();
        List<Long> cartIds = request.getCartIds();

        // 1. 参数校验 + 越权校验：购物车项必须都属于当前 userId
        if (userId == null || cartIds == null || cartIds.isEmpty()) {
            return R.badRequest("参数错误");
        }
        List<Cart> cartList = list(
                Wrappers.<Cart>lambdaQuery()
                        .eq(Cart::getUserId, userId)
                        .in(Cart::getId, cartIds)
        );
        if (cartList.size() != cartIds.size()) {
            return R.fail("购物车项异常或无权操作");
        }

        // 2. 批量查商品，构建 productId → Product 映射
        List<Long> productIds = cartList.stream()
                .map(Cart::getProductId)
                .distinct()
                .toList();
        R<List<ProductDTO>> productR = productFeignClient.listByIds(productIds);
        if (productR == null || productR.getData() == null || productR.getData().isEmpty()) {
            return R.fail("商品信息查询失败");
        }
        Map<Long, ProductDTO> productMap = productR.getData().stream()
                .collect(Collectors.toMap(ProductDTO::getId, p -> p));

        // 过滤已下架/不存在的商品，避免分组时 NPE
        List<Cart> validCartList = cartList.stream()
                .filter(cart -> {
                    ProductDTO product = productMap.get(cart.getProductId());
                    return product != null && Integer.valueOf(1).equals(product.getStatus());
                })
                .toList();
        if (validCartList.isEmpty()) {
            return R.fail("所选商品均已下架或不存在");
        }

        // 3. 组装下单明细（B2C 自营：不再按卖家拆单，一次结算只生成一张订单）
        List<OrderItemReq> items = validCartList.stream()
                .map(cart -> {
                    OrderItemReq item = new OrderItemReq();
                    item.setProductId(cart.getProductId());
                    item.setQuantity(cart.getQuantity());
                    return item;
                })
                .toList();

        CreateOrderRequest createRequest = new CreateOrderRequest();
        createRequest.setUserId(userId);
        createRequest.setItems(items);
        createRequest.setReceiverName(request.getReceiverName());
        createRequest.setReceiverPhone(request.getReceiverPhone());
        createRequest.setReceiverAddress(request.getReceiverAddress());

        R<Order> orderR = orderService.createOrder(createRequest);
        if (orderR == null || orderR.getCode() != 200 || orderR.getData() == null) {
            log.error("购物车结算失败, userId={}", userId);
            return R.fail("下单失败：" + (orderR != null ? orderR.getMessage() : "未知错误"));
        }

        // 4. 删除已结算的购物车项
        List<Long> settledCartIds = validCartList.stream()
                .map(Cart::getId)
                .toList();
        removeByIds(settledCartIds);

        return R.ok(List.of(orderR.getData()));
    }
}
