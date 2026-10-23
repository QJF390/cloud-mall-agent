package com.exdemo.order.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 账户 DTO（Feign 远程调用返回）
 * <p>只透出余额，不下发任何可推导用户资产的敏感字段。</p>
 */
@Data
public class AccountDTO {

    private Long id;

    private Long userId;

    private BigDecimal balance;
}
