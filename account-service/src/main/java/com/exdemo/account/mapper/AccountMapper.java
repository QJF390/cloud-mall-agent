package com.exdemo.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exdemo.account.entity.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 账户数据访问层
 *
 * <p>所有余额变更都通过原子 SQL 完成，避免"读-改-写"导致的并发竞态。</p>
 */
@Mapper
public interface AccountMapper extends BaseMapper<Account> {

    /**
     * 直接扣减可用余额（非担保交易，已不推荐直接使用）
     *
     * @param userId 用户ID
     * @param amount 扣减金额（正数）
     * @return 影响行数，0 表示余额不足或账户不存在
     */
    @Update("UPDATE t_account SET balance = balance - #{amount}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND balance >= #{amount}")
    int deductBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 充值：增加可用余额
     */
    @Update("UPDATE t_account SET balance = balance + #{amount}, update_time = NOW() WHERE user_id = #{userId}")
    int rechargeBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 下单冻结：可用余额减少，冻结金额增加
     */
    @Update("UPDATE t_account SET balance = balance - #{amount}, frozen_amount = frozen_amount + #{amount}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND balance >= #{amount}")
    int freezeBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 取消订单/退款解冻：冻结金额减少，可用余额增加
     */
    @Update("UPDATE t_account SET frozen_amount = frozen_amount - #{amount}, balance = balance + #{amount}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND frozen_amount >= #{amount}")
    int unfreezeBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 确认收货：减少买家冻结金额
     */
    @Update("UPDATE t_account SET frozen_amount = frozen_amount - #{amount}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND frozen_amount >= #{amount}")
    int decreaseFrozen(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 确认收货：增加卖家可用余额
     */
    @Update("UPDATE t_account SET balance = balance + #{amount}, update_time = NOW() WHERE user_id = #{userId}")
    int increaseBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}

