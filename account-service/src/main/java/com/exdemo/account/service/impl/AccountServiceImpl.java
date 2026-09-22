package com.exdemo.account.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.exdemo.account.constant.TransactionType;
import com.exdemo.account.entity.Account;
import com.exdemo.account.entity.Transaction;
import com.exdemo.account.mapper.AccountMapper;
import com.exdemo.account.mapper.TransactionMapper;
import com.exdemo.account.service.AccountService;
import com.exdemo.common.exception.BizException;
import com.exdemo.common.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户业务实现
 *
 * <p>设计说明：</p>
 * <ul>
 *   <li>t_account 与 t_user 通过 user_id 关联（一对一）。</li>
 *   <li>调用账户服务时若账户不存在，自动初始化一个余额为 0 的账户（兼容老用户）。</li>
 *   <li>所有余额变更均使用原子 SQL，避免"读-改-写"并发超扣/超冻。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl extends ServiceImpl<AccountMapper, Account> implements AccountService {

    private final TransactionMapper transactionMapper;

    /**
     * 写资金流水
     *
     * @param userId       资金所属用户
     * @param type         流水类型（TransactionType 常量）
     * @param amount       变动金额（正数进账 / 负数出账）
     * @param balanceAfter 变动后的可用余额（对账用）
     * @param orderNo      关联订单号
     * @param remark       备注
     */
    private void writeTransaction(Long userId, String type, BigDecimal amount,
                                  BigDecimal balanceAfter, String orderNo, String remark) {
        Transaction tx = new Transaction();
        tx.setUserId(userId);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setBalanceAfter(balanceAfter);
        tx.setOrderNo(orderNo);
        tx.setRemark(remark);
        tx.setCreateTime(LocalDateTime.now());
        transactionMapper.insert(tx);
    }

    private Account getOrCreateAccount(Long userId) {
        Account account = lambdaQuery().eq(Account::getUserId, userId).one();
        if (account != null) {
            return account;
        }

        Account newAccount = new Account();
        newAccount.setUserId(userId);
        newAccount.setBalance(BigDecimal.ZERO);
        newAccount.setFrozenAmount(BigDecimal.ZERO);
        newAccount.setCreateTime(LocalDateTime.now());
        newAccount.setUpdateTime(LocalDateTime.now());
        save(newAccount);
        return newAccount;
    }

    /**
     * 通用参数校验：用户ID与金额必须合法
     *
     * @return 错误信息，null 表示校验通过
     */
    private String validateUserAndAmount(Long userId, BigDecimal amount) {
        if (userId == null || amount == null) {
            return "用户ID或金额不能为空";
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return "金额必须大于0";
        }
        return null;
    }

    @Override
    public R<BigDecimal> getBalance(Long userId) {
        if (userId == null) {
            return R.fail("用户ID不能为空");
        }
        Account account = getOrCreateAccount(userId);
        return R.ok(account.getBalance());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> deduct(Long userId, BigDecimal amount) {
        String errMsg = validateUserAndAmount(userId, amount);
        if (errMsg != null) {
            return R.fail(errMsg);
        }

        getOrCreateAccount(userId);
        int rows = baseMapper.deductBalance(userId, amount);
        if (rows <= 0) {
            return R.fail("余额不足");
        }

        Account account = lambdaQuery().eq(Account::getUserId, userId).one();
        writeTransaction(userId, TransactionType.PAY, amount.negate(), account.getBalance(), null, "直接扣款");
        return R.ok(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Account> recharge(Long userId, BigDecimal amount) {
        String errMsg = validateUserAndAmount(userId, amount);
        if (errMsg != null) {
            return R.fail(errMsg);
        }

        getOrCreateAccount(userId);
        int rows = baseMapper.rechargeBalance(userId, amount);
        if (rows <= 0) {
            return R.fail("充值失败");
        }

        Account account = lambdaQuery().eq(Account::getUserId, userId).one();
        writeTransaction(userId, TransactionType.RECHARGE, amount, account.getBalance(), null, "账户充值");
        return R.ok(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> creditRecharge(Long userId, BigDecimal amount, String rechargeNo) {
        String errMsg = validateUserAndAmount(userId, amount);
        if (errMsg != null) {
            return R.fail(errMsg);
        }

        getOrCreateAccount(userId);
        int rows = baseMapper.rechargeBalance(userId, amount);
        if (rows <= 0) {
            return R.fail("入账失败");
        }

        Account account = lambdaQuery().eq(Account::getUserId, userId).one();
        writeTransaction(userId, TransactionType.RECHARGE, amount, account.getBalance(),
                rechargeNo, "支付宝充值入账");
        return R.ok(true);
    }

    // ==================== 担保交易 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> freeze(Long userId, BigDecimal amount, String orderNo) {
        if (userId == null || amount == null || orderNo == null || orderNo.isBlank()) {
            return R.fail("参数错误");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return R.fail("冻结金额必须大于0");
        }

        getOrCreateAccount(userId);
        int rows = baseMapper.freezeBalance(userId, amount);
        if (rows <= 0) {
            return R.fail("余额不足");
        }

        Account account = lambdaQuery().eq(Account::getUserId, userId).one();
        writeTransaction(userId, TransactionType.FREEZE, amount.negate(), account.getBalance(), orderNo, "下单冻结资金");
        return R.ok(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> unfreeze(Long userId, BigDecimal amount, String orderNo) {
        if (userId == null || amount == null || orderNo == null || orderNo.isBlank()) {
            return R.fail("参数错误");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return R.fail("解冻金额必须大于0");
        }

        getOrCreateAccount(userId);
        int rows = baseMapper.unfreezeBalance(userId, amount);
        if (rows <= 0) {
            return R.fail("冻结金额不足");
        }

        Account account = lambdaQuery().eq(Account::getUserId, userId).one();
        writeTransaction(userId, TransactionType.UNFREEZE, amount, account.getBalance(), orderNo, "取消订单解冻资金");
        return R.ok(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> settle(Long buyerId, Long sellerId, BigDecimal amount, String orderNo) {
        if (buyerId == null || sellerId == null || amount == null || orderNo == null || orderNo.isBlank()) {
            return R.fail("参数错误");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return R.fail("打款金额必须大于0");
        }
        if (buyerId.equals(sellerId)) {
            return R.fail("买家和卖家不能为同一人");
        }

        getOrCreateAccount(buyerId);
        getOrCreateAccount(sellerId);

        // 1. 买家冻结金额减少（担保金释放）
        int buyerRows = baseMapper.decreaseFrozen(buyerId, amount);
        if (buyerRows <= 0) {
            return R.fail("买家冻结金额不足");
        }

        // 2. 卖家可用余额增加（实际入账）
        int sellerRows = baseMapper.increaseBalance(sellerId, amount);
        if (sellerRows <= 0) {
            // 同一事务中抛出异常，买家冻结减少会自动回滚
            throw new BizException("卖家账户入账失败，事务已回滚，请排查");
        }

        // 3. 记录两笔流水：买家出账、卖家入账
        Account buyerAccount = lambdaQuery().eq(Account::getUserId, buyerId).one();
        Account sellerAccount = lambdaQuery().eq(Account::getUserId, sellerId).one();
        writeTransaction(buyerId, TransactionType.SETTLE, amount.negate(), buyerAccount.getBalance(), orderNo, "确认收货：冻结资金释放给卖家");
        writeTransaction(sellerId, TransactionType.SETTLE, amount, sellerAccount.getBalance(), orderNo, "确认收货：收到买家货款");

        return R.ok(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> refund(Long userId, BigDecimal amount, String orderNo) {
        if (userId == null || amount == null || orderNo == null || orderNo.isBlank()) {
            return R.fail("参数错误");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return R.fail("退款金额必须大于0");
        }

        getOrCreateAccount(userId);
        int rows = baseMapper.unfreezeBalance(userId, amount);
        if (rows <= 0) {
            return R.fail("冻结金额不足，退款失败");
        }

        Account account = lambdaQuery().eq(Account::getUserId, userId).one();
        writeTransaction(userId, TransactionType.REFUND, amount, account.getBalance(), orderNo, "订单退款");
        return R.ok(true);
    }
}
