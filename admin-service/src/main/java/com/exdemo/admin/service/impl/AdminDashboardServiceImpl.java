package com.exdemo.admin.service.impl;

import com.exdemo.admin.dto.OrderDTO;
import com.exdemo.admin.dto.ProductDTO;
import com.exdemo.admin.dto.UserDTO;
import com.exdemo.admin.feign.OrderFeignClient;
import com.exdemo.admin.feign.ProductFeignClient;
import com.exdemo.admin.feign.UserFeignClient;
import com.exdemo.admin.service.AdminDashboardService;
import com.exdemo.common.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    /** 已付款（计入成交额）的订单状态 */
    private static final Set<String> PAID_STATUSES = Set.of("FROZEN", "SHIPPED", "RECEIVED", "COMPLETED");

    /** 待处理订单状态（运营需要盯的） */
    private static final Set<String> PENDING_STATUSES = Set.of("PENDING", "FROZEN");

    private final UserFeignClient userFeignClient;
    private final ProductFeignClient productFeignClient;
    private final OrderFeignClient orderFeignClient;

    @Override
    public R<Map<String, Object>> stats() {
        boolean degraded = false;

        R<List<UserDTO>> users = userFeignClient.listUsers();
        if (!isOk(users)) {
            degraded = true;
            log.warn("[控制台] 用户服务不可用: {}", users == null ? "null" : users.getMessage());
        }

        R<List<ProductDTO>> products = productFeignClient.listProducts();
        if (!isOk(products)) {
            degraded = true;
            log.warn("[控制台] 商品服务不可用: {}", products == null ? "null" : products.getMessage());
        }

        // 传空串而不是 null：Feign 对 null 的 @RequestParam 处理存在版本差异，"" 最稳（下游按"不过滤"处理）
        R<List<OrderDTO>> orders = orderFeignClient.listAllOrders("");
        if (!isOk(orders)) {
            degraded = true;
            log.warn("[控制台] 订单服务不可用: {}", orders == null ? "null" : orders.getMessage());
        }

        List<OrderDTO> orderList = isOk(orders) ? orders.getData() : List.of();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        long todayAmount = orderList.stream()
                .filter(order -> order != null && order.getTotalAmount() != null)
                .filter(order -> PAID_STATUSES.contains(order.getStatus()))
                .filter(order -> order.getCreateTime() != null && !order.getCreateTime().isBefore(todayStart))
                .map(OrderDTO::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .longValue();

        long pendingOrderCount = orderList.stream()
                .filter(order -> order != null && PENDING_STATUSES.contains(order.getStatus()))
                .count();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userCount", isOk(users) ? users.getData().size() : 0);
        data.put("productCount", isOk(products) ? products.getData().size() : 0);
        data.put("orderCount", orderList.size());
        data.put("todayAmount", todayAmount);
        data.put("pendingOrderCount", pendingOrderCount);
        data.put("degraded", degraded);
        return R.ok(data);
    }

    @Override
    public R<Map<String, Object>> trend(int days) {
        R<List<OrderDTO>> orders = orderFeignClient.listAllOrders("");
        if (!isOk(orders)) {
            log.warn("[控制台] 订单服务不可用: {}", orders == null ? "null" : orders.getMessage());
        }
        List<OrderDTO> orderList = isOk(orders) ? orders.getData() : List.of();

        // 上限 30 天：接口是给图表用的，时间跨度太大点会太密；下限 1 天防恶意传参
        int span = Math.min(Math.max(days, 1), 30);
        LocalDate today = LocalDate.now();

        // 先按天建满桶（含无数据的日期），否则图上"断档日"会被吞掉，误导运营
        Map<LocalDate, long[]> buckets = new LinkedHashMap<>();
        for (int i = span - 1; i >= 0; i--) {
            buckets.put(today.minusDays(i), new long[2]); // [0]=销量单数, [1]=成交额
        }

        for (OrderDTO order : orderList) {
            if (order == null || order.getCreateTime() == null
                    || !PAID_STATUSES.contains(order.getStatus())) {
                continue;
            }
            long[] bucket = buckets.get(order.getCreateTime().toLocalDate());
            if (bucket == null) {
                continue;
            }
            bucket[0]++;
            if (order.getTotalAmount() != null) {
                bucket[1] += order.getTotalAmount().longValue();
            }
        }

        List<String> dates = new ArrayList<>();
        List<Long> orderCounts = new ArrayList<>();
        List<Long> amounts = new ArrayList<>();
        buckets.forEach((date, bucket) -> {
            dates.add(date.toString());
            orderCounts.add(bucket[0]);
            amounts.add(bucket[1]);
        });

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dates", dates);
        data.put("orderCount", orderCounts);
        data.put("amount", amounts);
        data.put("degraded", !isOk(orders));
        return R.ok(data);
    }

    private boolean isOk(R<?> remote) {
        return remote != null && remote.getCode() == 200 && remote.getData() != null;
    }
}
