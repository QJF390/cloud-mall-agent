package com.exdemo.order.task;

import com.exdemo.common.result.R;
import com.exdemo.order.entity.Order;
import com.exdemo.order.mapper.OrderMapper;
import com.exdemo.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时未支付订单自动关闭。
 * <p>为什么不用 MQ 延迟消息：延迟消息更精准，但要额外依赖且需要处理消息丢失；
 * 定时扫描补偿是兜底策略，两者在生产通常并存——延迟消息做主链路，扫描做兜底。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutCancelTask {

    /** 单次扫描上限：防止订单堆积时一次拉爆内存，也控制单轮耗时 */
    private static final int BATCH_SIZE = 200;

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    /** 超时阈值（分钟），可在 Nacos 配置中心动态调整 */
    @Value("${order.timeout-minutes:30}")
    private long timeoutMinutes;

    /**
     * 每 1 分钟扫描一次
     */
    @Scheduled(fixedDelay = 60_000)
    public void cancelTimeoutOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);

        List<Order> timeoutOrders;
        try {
            timeoutOrders = orderMapper.selectTimeoutPending(deadline, BATCH_SIZE);
        } catch (Exception e) {
            // 扫描失败不能吞掉：库存会一直挂在超时订单上，属于资损风险，必须留痕告警
            log.error("【超时取消】扫描超时订单失败, deadline={}", deadline, e);
            return;
        }

        if (timeoutOrders == null || timeoutOrders.isEmpty()) {
            return;
        }

        int success = 0;
        int failed = 0;
        for (Order order : timeoutOrders) {
            // buyerId 为空时回退 userId：历史数据两列并存，不能让旧订单成为漏网之鱼
            Long buyerId = order.getBuyerId() != null ? order.getBuyerId() : order.getUserId();
            try {
                R<Boolean> result = orderService.cancelOrder(buyerId, order.getId());
                if (result != null && result.getCode() == 200 && Boolean.TRUE.equals(result.getData())) {
                    success++;
                } else {
                    failed++;
                    log.error("【超时取消】关单失败, orderNo={}, orderId={}, msg={}",
                            order.getOrderNo(), order.getId(), result != null ? result.getMessage() : "无响应");
                }
            } catch (Exception e) {
                // 单条失败绝不能中断整轮扫描：否则前面那条卡住，后面所有订单都释放不了库存
                failed++;
                log.error("【超时取消】关单异常, orderNo={}, orderId={}",
                        order.getOrderNo(), order.getId(), e);
            }
        }

        log.info("【超时取消】本次扫描 {} 条，成功取消 {} 条，失败 {} 条，timeoutMinutes={}",
                timeoutOrders.size(), success, failed, timeoutMinutes);
    }
}
