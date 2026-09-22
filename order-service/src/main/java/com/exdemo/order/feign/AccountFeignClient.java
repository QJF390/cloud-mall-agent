package com.exdemo.order.feign;

import com.exdemo.common.result.R;
import com.exdemo.order.feign.fallback.AccountFeignFallback;
import lombok.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * 账户服务 Feign 客户端
 */
@FeignClient(value="account-service",fallback = AccountFeignFallback.class)
public interface AccountFeignClient {
//      请求路径要和提供者一致，方法参数也要一样
    /** 扣款（旧模型：直接扣减余额，担保交易改造后应弃用，改 freeze） */
    @PostMapping("/account/deduct")
    R<Boolean> deduct(@RequestParam("userId") Long userId,
                      @RequestParam("amount") BigDecimal amount);

    /**
     * 付款冻结（担保交易）：balance - amount，frozenAmount + amount，记 t_transaction 流水
     * 下单支付时调用
     */
    @PostMapping("/account/freeze")
    R<Boolean> freeze(@RequestParam("userId") Long userId,
                      @RequestParam("amount") BigDecimal amount,
                      @RequestParam("orderNo") String orderNo);

    /**
     * 解冻退款（取消订单/退款）：frozenAmount - amount，balance + amount，记流水
     */
    @PostMapping("/account/unfreeze")
    R<Boolean> unfreeze(@RequestParam("userId") Long userId,
                        @RequestParam("amount") BigDecimal amount,
                        @RequestParam("orderNo") String orderNo);

    /**
     * 打款给卖家（买家确认收货时）：买家 frozenAmount - amount，
     * 卖家 balance + amount，记流水
     */
    @PostMapping("/account/settle")
    R<Boolean> settle(@RequestParam("buyerId") Long buyerId,
                      @RequestParam("sellerId") Long sellerId,
                      @RequestParam("amount") BigDecimal amount,
                      @RequestParam("orderNo") String orderNo);

    /**
     * 退款：frozenAmount - amount，balance + amount，记 REFUND 流水
     * 订单处于 SHIPPED 状态时调用，资金仍在冻结池
     */
    @PostMapping("/account/refund")
    R<Boolean> refund(@RequestParam("userId") Long userId,
                      @RequestParam("amount") BigDecimal amount,
                      @RequestParam("orderNo") String orderNo);
}
