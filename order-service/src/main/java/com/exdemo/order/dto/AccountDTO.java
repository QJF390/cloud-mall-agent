package com.exdemo.order.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 账户 DTO（Feign 远程调用返回）
 * TODO: 补充字段，与 account-service 返回结构一致
 */
@Data
public class AccountDTO {

    private Long id;

    private Long userId;

    private BigDecimal balance;
}
