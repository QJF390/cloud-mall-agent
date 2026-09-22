package com.exdemo.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exdemo.account.entity.RechargeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RechargeOrderMapper extends BaseMapper<RechargeOrder> {

    /**
     * 标记已支付（CAS：只有 UNPAID 才能改成 PAID）
     *
     * @return 影响行数；0 表示已被处理过（幂等命中）或单号不存在
     */
    @Update("UPDATE t_recharge_order SET status = 'PAID', trade_no = #{tradeNo}, " +
            "pay_time = NOW(), update_time = NOW() " +
            "WHERE recharge_no = #{rechargeNo} AND status = 'UNPAID'")
    int markPaid(@Param("rechargeNo") String rechargeNo, @Param("tradeNo") String tradeNo);

    /**
     * 标记已关闭（CAS：只有 UNPAID 才能改成 CLOSED）
     */
    @Update("UPDATE t_recharge_order SET status = 'CLOSED', update_time = NOW() " +
            "WHERE recharge_no = #{rechargeNo} AND status = 'UNPAID'")
    int markClosed(@Param("rechargeNo") String rechargeNo);
}
