package com.exdemo.account.controller;

import com.exdemo.account.entity.Account;
import com.exdemo.account.service.AccountService;
import com.exdemo.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 账户控制器
 * TODO: 补充 REST API 接口
 */
@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/balance")
    public R<BigDecimal> getBalance(Long userId) {
        return accountService.getBalance(userId);
    }

    @PostMapping("/deduct")
    public R<Boolean> deduct(Long userId, BigDecimal amount) {
        return accountService.deduct(userId, amount);
    }

    @Deprecated
    @PostMapping("/recharge")
    public R<Account> recharge(Long userId, BigDecimal amount) {
        return R.fail("该接口已废弃（不经过支付直接加钱），请改用 /account/recharge/create");
    }

    // ==================== 担保交易接口 ====================

    /** 付款冻结（下单支付） */
    @PostMapping("/freeze")
    public R<Boolean> freeze(Long userId, BigDecimal amount, String orderNo) {
        return accountService.freeze(userId, amount, orderNo);
    }

    /** 解冻（取消订单） */
    @PostMapping("/unfreeze")
    public R<Boolean> unfreeze(Long userId, BigDecimal amount, String orderNo) {
        return accountService.unfreeze(userId, amount, orderNo);
    }

    /** 打款给卖家（确认收货） */
    @PostMapping("/settle")
    public R<Boolean> settle(Long buyerId, Long sellerId, BigDecimal amount, String orderNo) {
        return accountService.settle(buyerId, sellerId, amount, orderNo);
    }

    /** 退款 */
    @PostMapping("/refund")
    public R<Boolean> refund(Long userId, BigDecimal amount, String orderNo) {
        return accountService.refund(userId, amount, orderNo);
    }
}
