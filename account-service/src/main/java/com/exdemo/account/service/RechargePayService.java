package com.exdemo.account.service;

import java.math.BigDecimal;

public interface RechargePayService {

    boolean confirmPaid(String rechargeNo, String tradeNo, BigDecimal paidAmount);
}
