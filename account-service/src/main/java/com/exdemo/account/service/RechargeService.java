package com.exdemo.account.service;

import com.exdemo.account.dto.RechargeCreateVO;
import com.exdemo.account.dto.RechargeStatusVO;
import com.exdemo.common.result.R;

import java.math.BigDecimal;

/**
 * 充值业务（支付宝电脑网站支付）
 */
public interface RechargeService {

    /**
     * 发起充值：建本地充值单 + 调支付宝下单，返回支付页地址
     *
     * @param userId 用户ID
     * @param amount 充值金额（元，最多两位小数）
     * @return 支付页 URL + 充值单号
     */
    R<RechargeCreateVO> createRecharge(Long userId, BigDecimal amount);

    /**
     * 查询充值状态。
     * 若本地仍是待支付，会<b>主动向支付宝查一次单</b>并补入账（掉单补偿）。
     *
     * @param rechargeNo 充值单号
     * @param userId     用户ID（校验归属，防止查别人的单）
     */
    R<RechargeStatusVO> queryStatus(String rechargeNo, Long userId);

    void compensateUnpaidOrders();
}
