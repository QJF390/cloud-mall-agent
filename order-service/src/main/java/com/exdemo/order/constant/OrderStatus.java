package com.exdemo.order.constant;

public final class OrderStatus {

    /** 待付款：下单成功但未支付 */
    public static final String PENDING = "PENDING";

    /** 已付款（资金冻结在平台）：担保交易核心状态 */
    public static final String FROZEN = "FROZEN";

    /** 已发货：平台已发货 */
    public static final String SHIPPED = "SHIPPED";

    /** 已收货：买家确认收货，触发平台结算 */
    public static final String RECEIVED = "RECEIVED";

    /** 已完成：平台已完成结算，交易闭环 */
    public static final String COMPLETED = "COMPLETED";

    /** 已取消：回补库存 + 解冻资金 */
    public static final String CANCELLED = "CANCELLED";

    /** 已退款：买家退款成功 */
    public static final String REFUNDED = "REFUNDED";

    private OrderStatus() {
    }
}
