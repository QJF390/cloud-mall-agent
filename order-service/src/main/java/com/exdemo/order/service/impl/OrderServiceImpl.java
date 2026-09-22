package com.exdemo.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.exdemo.common.constant.PlatformConstant;
import com.exdemo.common.result.R;
import com.exdemo.order.constant.OrderStatus;
import com.exdemo.order.dto.CreateOrderRequest;
import com.exdemo.order.dto.OrderItemReq;
import com.exdemo.order.dto.ProductDTO;
import com.exdemo.order.dto.UserDTO;
import com.exdemo.order.entity.Order;
import com.exdemo.order.entity.OrderItem;
import com.exdemo.order.feign.AccountFeignClient;
import com.exdemo.order.feign.ProductFeignClient;
import com.exdemo.order.feign.UserFeignClient;
import com.exdemo.order.mapper.OrderItemMapper;
import com.exdemo.order.mapper.OrderMapper;
import com.exdemo.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.exceptions.PersistenceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 订单业务实现
 *
 * 下单流程（跨 4 个微服务协作）：
 * 1. 校验参数
 * 2. Feign → user-service 查用户是否存在
 * 3. Feign → product-service 查商品信息 + 扣库存
 * 4. Feign → account-service 扣款
 * 5. 本地保存订单
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private final UserFeignClient userFeignClient;
    private final ProductFeignClient productFeignClient;
    private final AccountFeignClient accountFeignClient;
    private final OrderItemMapper orderItemMapper;
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Override
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public R<Order> createOrder(Long userId, Long productId, Integer quantity) {
        // 旧单商品下单兼容：包装成多商品请求，复用新流程
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(userId);
        OrderItemReq item = new OrderItemReq();
        item.setProductId(productId);
        item.setQuantity(quantity);
        request.setItems(List.of(item));
        return createOrder(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Order> createOrder(CreateOrderRequest request) {
        Long userId = request.getUserId();
        List<OrderItemReq> items = request.getItems();

        // ====== 1. 参数校验 ======
        if (userId == null || items == null || items.isEmpty()) {
            return R.fail("参数错误：userId、items 不能为空");
        }

        // ====== 2. 生成订单号 ======
        String orderNo = generateOrderNo(userId);

        // ====== 3. 幂等检查（基于用户+商品列表，10s 内重复提交拦截） ======
        String idemKey = buildIdempotentKey(userId, items);
        Boolean isIdempotent = stringRedisTemplate.opsForValue().setIfAbsent(idemKey, "1", Duration.ofSeconds(10));
        log.info("【下单-步骤0】幂等检查, idemKey={}", idemKey);
        if (Boolean.FALSE.equals(isIdempotent)) {
            return R.fail("请勿重复提交订单");
        }

        boolean success = false;
        try {
            // ====== 4. 调用用户服务，确认用户存在 ======
            log.info("【下单-步骤1】查询用户, userId={}", userId);
            R<UserDTO> userResult = userFeignClient.getUserById(userId);
            if (userResult.getCode() != 200 || userResult.getData() == null) {
                return R.fail("用户不存在或用户服务异常");
            }

            // ====== 5. 批量查询商品（避免 N+1） ======
            List<Long> productIds = items.stream()
                    .map(OrderItemReq::getProductId)
                    .distinct()
                    .toList();
            log.info("【下单-步骤2】批量查询商品, productIds={}", productIds);
            R<List<ProductDTO>> productR = productFeignClient.listByIds(productIds);
            if (productR == null || productR.getData() == null || productR.getData().isEmpty()) {
                return R.fail("商品信息查询失败");
            }
            Map<Long, ProductDTO> productMap = productR.getData().stream()
                    .collect(Collectors.toMap(ProductDTO::getId, p -> p));

            // ====== 6. 校验商品、计算金额、构建订单明细 ======
            List<OrderItem> orderItems = new ArrayList<>();
            BigDecimal totalAmount = BigDecimal.ZERO;

            for (OrderItemReq itemReq : items) {
                ProductDTO product = productMap.get(itemReq.getProductId());
                if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
                    return R.fail("商品已下架或不存在：" + itemReq.getProductId());
                }

                BigDecimal itemTotal = product.getPrice()
                        .multiply(BigDecimal.valueOf(itemReq.getQuantity()));
                totalAmount = totalAmount.add(itemTotal);

                OrderItem orderItem = new OrderItem();
                orderItem.setProductId(product.getId());
                // B2C 自营：商品所属方恒为平台，不再取商品发布者
                orderItem.setSellerId(PlatformConstant.PLATFORM_SELLER_ID);
                orderItem.setProductName(product.getName());
                orderItem.setQuantity(itemReq.getQuantity());
                orderItem.setUnitPrice(product.getPrice());
                orderItem.setTotalAmount(itemTotal);
                orderItem.setCreateTime(LocalDateTime.now());
                orderItems.add(orderItem);
            }

            // ====== 7. 扣减库存（当前 Feign 只有单商品接口，循环扣；失败时回补已扣部分） ======
            log.info("【下单-步骤3】扣减库存, items={}", items);
            Map<Long, Integer> deductedMap = new LinkedHashMap<>();
            for (OrderItemReq itemReq : items) {
                R<Boolean> stockResult = productFeignClient.deductStock(
                        itemReq.getProductId(), itemReq.getQuantity());
                if (stockResult.getCode() != 200 || stockResult.getData() == null || !stockResult.getData()) {
                    // 扣库存失败：回滚已扣库存
                    deductedMap.forEach((pid, qty) ->
                            productFeignClient.revertStock(pid, qty));
                    return R.fail("库存扣减失败：" + itemReq.getProductId());
                }
                deductedMap.put(itemReq.getProductId(), itemReq.getQuantity());
            }

            // ====== 8. 账户冻结（担保交易：资金先冻结在平台） ======
            log.info("【下单-步骤4】账户冻结, userId={}, amount={}", userId, totalAmount);
            R<Boolean> accountResult = accountFeignClient.freeze(userId, totalAmount, orderNo);
            if (accountResult.getCode() != 200 || accountResult.getData() == null || !accountResult.getData()) {
                // 冻结失败：回补全部库存
                deductedMap.forEach((pid, qty) ->
                        productFeignClient.revertStock(pid, qty));
                return R.fail("账户冻结失败：" + accountResult.getMessage());
            }

            // ====== 9. 保存订单主表 ======
            log.info("【下单-步骤5】保存订单");
            Order order = Order.builder()
                    .userId(userId)
                    .orderNo(orderNo)
                    .buyerId(userId)
                    .sellerId(PlatformConstant.PLATFORM_SELLER_ID)
                    .totalAmount(totalAmount)
                    .status("FROZEN")
                    .receiverName(request.getReceiverName())
                    .receiverPhone(request.getReceiverPhone())
                    .receiverAddress(request.getReceiverAddress())
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            baseMapper.insert(order);

            // ====== 10. 保存订单明细（商品快照） ======
            for (OrderItem orderItem : orderItems) {
                orderItem.setOrderId(order.getId());
                orderItemMapper.insert(orderItem);
            }

            success = true;
            log.info("【下单成功】orderId={}, buyerId={}, totalAmount={}",
                    order.getId(), userId, totalAmount);
            return R.ok("下单成功", order);

        } catch (Exception e) {
            log.error("【下单失败】request={}", request, e);
            return R.fail("下单失败：" + e.getMessage());
        } finally {
            // 只有业务失败或异常时才释放幂等 key，允许用户重试；
            // 下单成功时保留 key，靠 TTL 自然过期，期间拦截重复提交。
            if (!success) {
                deleteIdemKey(idemKey);
            }
        }
    }

    /**
     * 构造幂等 key：用户ID + 每个商品ID_数量的拼接
     * 保证同样的商品清单在短时间内只能下一次单
     */
    private String buildIdempotentKey(Long userId, List<OrderItemReq> items) {
        StringBuilder sb = new StringBuilder("order:idempotent:").append(userId);
        for (OrderItemReq item : items) {
            sb.append(":").append(item.getProductId()).append("_").append(item.getQuantity());
        }
        return sb.toString();
    }

    @Override
    public R<Order> getOrderById(Long id) {
        log.info("查询订单，orderId:{}", id);
        if (id == null || id <= 0) {
            log.warn("查询订单非法id：{}", id);
            return R.fail("订单ID不能为空");
        }
        try {
            Order order = baseMapper.selectById(id);
            if (order == null) {
                log.warn("订单不存在，orderId:{}", id);
                return R.fail("订单不存在");
            }
            return R.ok(order);
        }catch (PersistenceException e){
            log.error("查询订单数据库异常,id={}",id,e);
            return R.fail("数据库暂时无法访问，请稍后重试");
        }
    }

    @Override
    public R<List<Order>> listOrdersByUserId(Long userId) {
        // B2C 自营：用户只有"买家"一种身份，直接返回本人订单
        return listBuyOrders(userId);
    }

    @Override
    public R<List<Order>> listBuyOrders(Long userId) {

        if (userId == null || userId <= 0) {
            return R.fail("用户ID不能为空");
        }
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getBuyerId, userId)
               .orderByDesc(Order::getCreateTime);
        return R.ok(baseMapper.selectList(wrapper));
    }

    @Override
    public R<List<OrderItem>> listItemsByOrderIds(List<Long> orderIds) {
        // 空集合不能直接拼 IN（会生成非法 SQL），提前返回空列表
        if (orderIds == null || orderIds.isEmpty()) {
            return R.ok(new ArrayList<>());
        }
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(OrderItem::getOrderId, orderIds)
               .orderByAsc(OrderItem::getId);
        return R.ok(orderItemMapper.selectList(wrapper));
    }

    @Override
    public String generateOrderNo(Long userId) {
        // yyyyMMddHHmmss + 用户ID后6位 + 4位随机数 → 约 24 位
        return DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + String.format("%06d", userId % 1000000)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    /**
     * 业务失败时删除幂等 key，允许用户重试；
     * 下单成功时不删，靠 10s TTL 自然过期，期间拦截同参数重复提交。
     */
    private void deleteIdemKey(String idemKey) {
        stringRedisTemplate.delete(idemKey);
        log.info("【下单-失败】释放幂等 key, idemKey={}", idemKey);
    }

    // ==================== 担保交易状态机 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> cancelOrder(Long userId, Long orderId) {
        // 1. 参数与权限校验
        if (userId == null || orderId == null) {
            return R.fail("用户ID和订单ID不能为空");
        }
        Order order = baseMapper.selectById(orderId);
        if (order == null) {
            return R.fail("订单不存在");
        }
        if (!userId.equals(order.getBuyerId())) {
            log.warn("越权取消订单, orderId={}, operator={}, buyerId={}", orderId, userId, order.getBuyerId());
            return R.fail("无权操作该订单");
        }
        return doCancel(order);
    }

    /**
     * 后台强制关单：与买家取消共用同一套状态机（{@link #doCancel}），
     * 区别只是不校验买家归属——管理员身份与审计由 admin-service 负责。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> adminCancel(Long orderId, String reason) {
        if (orderId == null) {
            return R.fail("订单ID不能为空");
        }
        Order order = baseMapper.selectById(orderId);
        if (order == null) {
            return R.fail("订单不存在");
        }
        log.warn("【后台强制关单】orderId={}, status={}, reason={}", orderId, order.getStatus(), reason);
        return doCancel(order);
    }

    /**
     * 关单公共逻辑（PENDING/FROZEN → CANCELLED）：解冻资金 + 回补库存 + 落状态。
     * 抽出来是为了不出现"买家取消"和"后台关单"两份状态机（两份 = 两份 bug）。
     */
    private R<Boolean> doCancel(Order order) {
        Long orderId = order.getId();

        // 2. 幂等：已取消直接成功
        if (OrderStatus.CANCELLED.equals(order.getStatus())) {
            return R.ok(true);
        }

        // 3. 状态机校验：仅 PENDING / FROZEN 可取消
        if (!OrderStatus.PENDING.equals(order.getStatus()) && !OrderStatus.FROZEN.equals(order.getStatus())) {
            return R.fail("当前订单状态不允许取消");
        }

        // 4. PENDING 未冻结资金，无需解冻；FROZEN 需解冻
        if (OrderStatus.FROZEN.equals(order.getStatus())) {
            R<Boolean> unfreezeResult = accountFeignClient.unfreeze(
                    order.getBuyerId(), order.getTotalAmount(), order.getOrderNo());
            if (unfreezeResult.getCode() != 200 || !Boolean.TRUE.equals(unfreezeResult.getData())) {
                log.error("取消订单解冻资金失败, orderId={}, orderNo={}, msg={}",
                        orderId, order.getOrderNo(), unfreezeResult.getMessage());
                return R.fail("取消订单失败：资金解冻异常，请稍后重试");
            }
        }

        // 5. 回补库存（一单多商品逐条回补）
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        for (OrderItem item : items) {
            R<Boolean> stockResult = productFeignClient.revertStock(item.getProductId(), item.getQuantity());
            if (stockResult.getCode() != 200 || !Boolean.TRUE.equals(stockResult.getData())) {
                // 库存回补失败属于分布式事务问题，必须人工/定时补偿介入
                log.error("取消订单回补库存失败, orderId={}, productId={}, quantity={}",
                        orderId, item.getProductId(), item.getQuantity());
                // TODO[Saga补偿]：推送库存回补补偿消息到 MQ，避免资损
                throw new RuntimeException("取消订单失败：库存回补异常");
            }
        }

        // 6. 更新订单状态
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(order);

        log.info("【取消订单成功】orderId={}, buyerId={}", orderId, order.getBuyerId());
        return R.ok(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> shipOrder(Long orderId) {
        if (orderId == null) {
            return R.fail("订单ID不能为空");
        }
        Order order = baseMapper.selectById(orderId);
        if (order == null) {
            return R.fail("订单不存在");
        }

        // 幂等
        if (OrderStatus.SHIPPED.equals(order.getStatus())) {
            return R.ok(true);
        }

        if (!OrderStatus.FROZEN.equals(order.getStatus())) {
            return R.fail("当前订单状态不允许发货");
        }

        order.setStatus(OrderStatus.SHIPPED);
        order.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(order);

        // TODO[安全]：B2C 下"发货"属于平台运营动作，用户侧不应直接调用。
        //  当前为快速验证先去掉卖家校验，后续应改为运营后台鉴权（管理员 Token / 网关白名单）。
        log.info("【平台发货成功】orderId={}", orderId);
        return R.ok(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> receiveOrder(Long buyerId, Long orderId) {
        if (buyerId == null || orderId == null) {
            return R.fail("买家ID和订单ID不能为空");
        }
        Order order = baseMapper.selectById(orderId);
        if (order == null) {
            return R.fail("订单不存在");
        }
        if (!buyerId.equals(order.getBuyerId())) {
            log.warn("越权确认收货, orderId={}, operator={}, buyerId={}", orderId, buyerId, order.getBuyerId());
            return R.fail("无权操作该订单");
        }

        // 幂等
        if (OrderStatus.COMPLETED.equals(order.getStatus())) {
            return R.ok(true);
        }

        if (!OrderStatus.SHIPPED.equals(order.getStatus())) {
            return R.fail("当前订单状态不允许确认收货");
        }

        // B2C 结算：买家冻结金额减少，平台余额增加（sellerId 恒为平台）
        R<Boolean> settleResult = accountFeignClient.settle(
                buyerId, order.getSellerId(), order.getTotalAmount(), order.getOrderNo());
        if (settleResult.getCode() != 200 || !Boolean.TRUE.equals(settleResult.getData())) {
            log.error("确认收货打款失败, orderId={}, orderNo={}, msg={}",
                    orderId, order.getOrderNo(), settleResult.getMessage());
            return R.fail("确认收货失败：平台打款异常，请稍后重试");
        }

        // settle 成功后更新订单为已完成
        order.setStatus(OrderStatus.COMPLETED);
        order.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(order);

        log.info("【确认收货成功】orderId={}, buyerId={}, sellerId={}, amount={}",
                orderId, buyerId, order.getSellerId(), order.getTotalAmount());
        return R.ok(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> refundOrder(Long userId, Long orderId, String reason) {
        if (userId == null || orderId == null) {
            return R.fail("用户ID和订单ID不能为空");
        }
        Order order = baseMapper.selectById(orderId);
        if (order == null) {
            return R.fail("订单不存在");
        }
        if (!userId.equals(order.getBuyerId())) {
            log.warn("越权退款, orderId={}, operator={}, buyerId={}", orderId, userId, order.getBuyerId());
            return R.fail("无权操作该订单");
        }
        return doRefund(order, reason);
    }

    /**
     * 后台介入退款：与买家申请退款共用同一套资金/库存回滚逻辑（{@link #doRefund}），
     * 区别同样只是不校验买家归属。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> adminRefund(Long orderId, String reason) {
        if (orderId == null) {
            return R.fail("订单ID不能为空");
        }
        Order order = baseMapper.selectById(orderId);
        if (order == null) {
            return R.fail("订单不存在");
        }
        log.warn("【后台介入退款】orderId={}, status={}, amount={}, reason={}",
                orderId, order.getStatus(), order.getTotalAmount(), reason);
        return doRefund(order, reason);
    }

    /** 退款公共逻辑（SHIPPED → REFUNDED）：资金原路退回 + 回补库存 + 落状态。 */
    private R<Boolean> doRefund(Order order, String reason) {
        Long orderId = order.getId();

        // 幂等
        if (OrderStatus.REFUNDED.equals(order.getStatus())) {
            return R.ok(true);
        }

        // 仅 SHIPPED 状态可直接退款（资金仍在冻结池）
        // COMPLETED 状态需先从卖家账户扣回，本实现暂不支持，需人工/扩展接口处理
        if (!OrderStatus.SHIPPED.equals(order.getStatus())) {
            return R.fail("当前订单状态不允许退款");
        }

        // 资金原路退回：frozenAmount - amount，balance + amount
        R<Boolean> refundResult = accountFeignClient.refund(
                order.getBuyerId(), order.getTotalAmount(), order.getOrderNo());
        if (refundResult.getCode() != 200 || !Boolean.TRUE.equals(refundResult.getData())) {
            log.error("退款失败, orderId={}, orderNo={}, msg={}",
                    orderId, order.getOrderNo(), refundResult.getMessage());
            return R.fail("退款失败：资金退回异常，请稍后重试");
        }

        // 回补库存
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        for (OrderItem item : items) {
            R<Boolean> stockResult = productFeignClient.revertStock(item.getProductId(), item.getQuantity());
            if (stockResult.getCode() != 200 || !Boolean.TRUE.equals(stockResult.getData())) {
                log.error("退款回补库存失败, orderId={}, productId={}, quantity={}",
                        orderId, item.getProductId(), item.getQuantity());
                // TODO[Saga补偿]：库存回补失败需补偿
                throw new RuntimeException("退款失败：库存回补异常");
            }
        }

        // 更新订单状态并记录退款原因
        order.setStatus(OrderStatus.REFUNDED);
        order.setRefundReason(reason);
        order.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(order);

        log.info("【退款成功】orderId={}, buyerId={}, amount={}, reason={}",
                orderId, order.getBuyerId(), order.getTotalAmount(), reason);
        return R.ok(true);
    }

    // ==================== 管理端专用 ====================

    @Override
    public R<List<Order>> listAllOrders(String status) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        // status 为空表示"全部"，避免前端传空串时拼出 status = '' 查不到任何数据
        String filter = (status == null || status.isBlank()) ? null : status.trim();
        if (filter != null) {
            wrapper.eq(Order::getStatus, filter);
        }
        wrapper.orderByDesc(Order::getCreateTime);
        return R.ok(baseMapper.selectList(wrapper));
    }

}
