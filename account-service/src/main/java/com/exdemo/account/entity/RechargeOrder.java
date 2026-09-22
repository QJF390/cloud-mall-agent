package com.exdemo.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_recharge_order")
public class RechargeOrder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 本地充值单号，同时作为支付宝的 out_trade_no */
    private String rechargeNo;

    private Long userId;

    private BigDecimal amount;

    /** 状态：见 RechargeStatus */
    private String status;

    /** 支付宝交易号（回调 / 查单返回） */
    private String tradeNo;

    private LocalDateTime payTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
