package com.exdemo.admin.feign.fallback;

import com.exdemo.admin.dto.OrderDTO;
import com.exdemo.admin.feign.OrderFeignClient;
import com.exdemo.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class OrderFeignFallback implements OrderFeignClient {

    private static final String UNAVAILABLE = "订单服务暂时不可用，请稍后重试";

    @Override
    public R<OrderDTO> getOrderById(Long id) {
        log.warn("[fallback] order-service 不可用, getOrderById id={}", id);
        return R.fail(503, UNAVAILABLE);
    }

    @Override
    public R<List<OrderDTO>> listOrdersByUser(Long userId) {
        log.warn("[fallback] order-service 不可用, listOrdersByUser userId={}", userId);
        return R.fail(503, UNAVAILABLE);
    }

    @Override
    public R<List<OrderDTO>> listAllOrders(String status) {
        log.warn("[fallback] order-service 不可用, listAllOrders status={}", status);
        return R.fail(503, UNAVAILABLE);
    }

    @Override
    public R<Boolean> adminShip(Long orderId) {
        log.warn("[fallback] order-service 不可用, adminShip orderId={}", orderId);
        return R.fail(503, UNAVAILABLE);
    }

    @Override
    public R<Boolean> adminCancel(Long orderId, String reason) {
        log.warn("[fallback] order-service 不可用, adminCancel orderId={}", orderId);
        return R.fail(503, UNAVAILABLE);
    }

    @Override
    public R<Boolean> adminRefund(Long orderId, String reason) {
        log.warn("[fallback] order-service 不可用, adminRefund orderId={}", orderId);
        return R.fail(503, UNAVAILABLE);
    }
}
