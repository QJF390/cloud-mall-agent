package com.exdemo.order.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutCancelTask {

    /** 超时阈值（分钟），从配置读取更佳，如 @Value("${order.timeout-minutes:30}") */
    private static final long TIMEOUT_MINUTES = 30L;

    /**
     * 每 1 分钟扫描一次
     * TODO[实现]：查 PENDING 且 create_time < now - 30min 的订单，逐个取消并记日志
     */
    @Scheduled(fixedDelay = 60_000)
    public void cancelTimeoutOrders() {
        // TODO[实现]：
        // 1. SELECT * FROM t_order WHERE status = 'PENDING' AND create_time < #{deadline} LIMIT 200
        // 2. 遍历调用 orderService.cancelOrder(buyerId, orderId)，失败的要记日志并告警
        // 3. 全部完成后打一行汇总日志：本次扫描 N 条，成功取消 M 条
        log.info("【超时取消】扫描开始, timeoutMinutes={}", TIMEOUT_MINUTES);
    }
}
