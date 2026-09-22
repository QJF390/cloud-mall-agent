package com.exdemo.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_transaction")
public class Transaction {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联订单号（对账、售后排查用） */
    private String orderNo;

    /** 资金所属用户 */
    private Long userId;

    /** 类型：FREEZE / UNFREEZE / SETTLE / REFUND / RECHARGE ... */
    private String type;

    /** 变动金额（正数进账 / 负数出账） */
    private BigDecimal amount;

    /** 变动后余额（对账用，查"钱去哪了"） */
    private BigDecimal balanceAfter;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;
}
