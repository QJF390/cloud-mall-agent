package com.exdemo.account.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 发起充值的结果：前端拿 payUrl 跳支付宝，拿 rechargeNo 轮询状态
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RechargeCreateVO {

    /** 本地充值单号（轮询状态用） */
    private String rechargeNo;

    /** 支付宝支付页地址（前端 window.open 打开） */
    private String payUrl;

    /** 充值金额 */
    private BigDecimal amount;
}
