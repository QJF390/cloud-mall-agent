package com.exdemo.account.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.exdemo.account.entity.Account;
import com.exdemo.common.result.R;

import java.math.BigDecimal;

/**
 * 账户业务接口
 * <p>余额查询、扣款、充值</p>
 */
public interface AccountService extends IService<Account> {

    /**
     * 余额查询
     *
     * @param userId 用户ID
     * @return 账户余额
     */
    R<BigDecimal> getBalance(Long userId);

    /**
     * 扣款
     *
     * @param userId 用户ID
     *
     *
     * @param amount 扣款金额
     * @return 是否扣款成功
     */
    R<Boolean> deduct(Long userId, BigDecimal amount);

    /**
     * 充值
     *
     * @param userId 用户ID
     * @param amount 充值金额
     * @return 充值后的账户信息
     */
    R<Account> recharge(Long userId, BigDecimal amount);

    /**
     * 充值入账（**仅供支付回调 / 主动查单调用，不对外暴露 HTTP 接口**）
     *
     * <p>与 {@link #recharge} 的区别：本方法把充值单号写进流水的 orderNo，
     * 事后能按单号反查"这笔钱是哪次充值来的"，对账和客诉排查都靠它。</p>
     *
     * @param userId     用户ID
     * @param amount     入账金额
     * @param rechargeNo 充值单号（落 t_transaction.order_no）
     * @return 是否入账成功
     */
    R<Boolean> creditRecharge(Long userId, BigDecimal amount, String rechargeNo);

    // ==================== 担保交易（C2C 资金模型改造） ====================

    /**
     * 付款冻结：balance - amount，frozenAmount + amount，记 FREEZE 流水
     * 买家下单支付时调用（取代直接扣款 deduct）
     */
    R<Boolean> freeze(Long userId, BigDecimal amount, String orderNo);

    /**
     * 解冻：frozenAmount - amount，balance + amount，记 UNFREEZE 流水
     * 取消订单时调用，资金退回买家可用余额
     */
    R<Boolean> unfreeze(Long userId, BigDecimal amount, String orderNo);

    R<Boolean> settle(Long buyerId, Long sellerId, BigDecimal amount, String orderNo);

    /**
     * 退款：资金原路退回买家，记 REFUND 流水
     * 退款语义与 unfreeze 类似，但流水类型不同，便于对账区分"取消"与"退款"
     */
    R<Boolean> refund(Long userId, BigDecimal amount, String orderNo);
}

