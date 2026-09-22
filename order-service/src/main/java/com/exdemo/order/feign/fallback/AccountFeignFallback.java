package com.exdemo.order.feign.fallback;

import com.exdemo.common.result.R;
import com.exdemo.order.feign.AccountFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 账户服务熔断降级
 */
@Slf4j
@Component
public class AccountFeignFallback implements AccountFeignClient {

    @Override
    public R<Boolean> deduct(Long userId, BigDecimal amount) {
        log.error("【熔断降级】账户扣款失败, userId={}, amount={}", userId, amount);
        return R.fail("账户服务不可用，请稍后重试");
    }

    @Override
    public R<Boolean> freeze(Long userId, BigDecimal amount, String orderNo) {
        log.error("【熔断降级】账户冻结失败, userId={}, amount={}, orderNo={}", userId, amount, orderNo);
        return R.fail("账户服务不可用，请稍后重试");
    }

    @Override
    public R<Boolean> unfreeze(Long userId, BigDecimal amount, String orderNo) {
        log.error("【熔断降级】账户解冻失败, userId={}, amount={}, orderNo={}", userId, amount, orderNo);
        return R.fail("账户服务不可用，请稍后重试");
    }

    @Override
    public R<Boolean> settle(Long buyerId, Long sellerId, BigDecimal amount, String orderNo) {
        log.error("【熔断降级】账户结算失败, buyerId={}, sellerId={}, amount={}, orderNo={}",
                buyerId, sellerId, amount, orderNo);
        return R.fail("账户服务不可用，请稍后重试");
    }

    @Override
    public R<Boolean> refund(Long userId, BigDecimal amount, String orderNo) {
        log.error("【熔断降级】账户退款失败, userId={}, amount={}, orderNo={}", userId, amount, orderNo);
        return R.fail("账户服务不可用，请稍后重试");
    }
}
