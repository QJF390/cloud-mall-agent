package com.exdemo.account.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值单状态（前端轮询 / 支付完成页展示）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RechargeStatusVO {

    private String rechargeNo;

    private Long userId;

    private BigDecimal amount;

    /** 状态：UNPAID / PAID / CLOSED，见 RechargeStatus */
    private String status;

    private LocalDateTime payTime;
}
