package com.exdemo.account.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exdemo.account.constant.RechargeStatus;
import com.exdemo.account.entity.RechargeOrder;
import com.exdemo.account.mapper.RechargeOrderMapper;
import com.exdemo.account.service.AccountService;
import com.exdemo.account.service.RechargePayService;
import com.exdemo.common.exception.BizException;
import com.exdemo.common.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class RechargePayServiceImpl implements RechargePayService {

    private final RechargeOrderMapper rechargeOrderMapper;
    private final AccountService accountService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmPaid(String rechargeNo, String tradeNo, BigDecimal paidAmount) {
        if (rechargeNo == null || rechargeNo.isBlank()) {
            log.warn("[recharge] 充值单号为空");
            return false;
        }

        RechargeOrder order = rechargeOrderMapper.selectOne(
                new LambdaQueryWrapper<RechargeOrder>().eq(RechargeOrder::getRechargeNo, rechargeNo));
        if (order == null) {
            log.warn("[recharge] 充值单不存在 rechargeNo={}", rechargeNo);
            return false;
        }

        // ── 防线①：已是终态，直接幂等返回 ──
        if (RechargeStatus.PAID.equals(order.getStatus())) {
            log.info("[recharge] 重复通知，已入账过，忽略 rechargeNo={}", rechargeNo);
            return true;
        }
        if (!RechargeStatus.UNPAID.equals(order.getStatus())) {
            log.warn("[recharge] 充值单状态异常，忽略 rechargeNo={} status={}",
                    rechargeNo, order.getStatus());
            return false;
        }

        // ── 金额校验：回调金额必须与本地单一致 ──
        if (paidAmount != null && order.getAmount().compareTo(paidAmount) != 0) {
            log.error("[recharge] 金额不一致，拒绝入账 rechargeNo={} 本地={} 回调={}",
                    rechargeNo, order.getAmount(), paidAmount);
            return false;
        }

        // ── 防线②：CAS 更新，只有 UNPAID 能改成 PAID ──
        int rows = rechargeOrderMapper.markPaid(rechargeNo, tradeNo);
        if (rows == 0) {
            // 并发下被别的线程抢先处理了 —— 同样视为成功（幂等），但绝不能再加钱
            log.info("[recharge] 并发下已被其他线程处理 rechargeNo={}", rechargeNo);
            return true;
        }

        // ── 入账：加余额 + 写流水 ──
        R<Boolean> credit = accountService.creditRecharge(order.getUserId(), order.getAmount(), rechargeNo);
        if (credit == null || !Boolean.TRUE.equals(credit.getData())) {

            throw new BizException("充值入账失败，事务已回滚 rechargeNo=" + rechargeNo);
        }

        log.info("[recharge] 充值入账成功 rechargeNo={} userId={} amount={} tradeNo={}",
                rechargeNo, order.getUserId(), order.getAmount(), tradeNo);
        return true;
    }
}
