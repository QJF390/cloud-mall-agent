package com.exdemo.account.constant;

public final class RechargeStatus {

    private RechargeStatus() {
        // 工具类禁止实例化
    }

    /** 待支付：已下单，钱还没到账 */
    public static final String UNPAID = "UNPAID";

    /** 已支付：钱已到账且已入账 */
    public static final String PAID = "PAID";

    /** 已关闭：超时未支付 / 支付宝侧交易关闭 */
    public static final String CLOSED = "CLOSED";
}
