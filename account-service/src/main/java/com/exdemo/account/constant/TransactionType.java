package com.exdemo.account.constant;

/**
 * 资金流水类型常量
 *
 * <p>与 t_transaction.type 字段对应，统一用常量避免硬编码写错。</p>
 */
public final class TransactionType {

    private TransactionType() {
        // 工具类禁止实例化
    }

    /** 充值：可用余额增加 */
    public static final String RECHARGE = "RECHARGE";

    /** 直接付款：可用余额减少（非担保交易，已不推荐直接使用） */
    public static final String PAY = "PAY";

    /** 下单冻结：可用余额 → 冻结金额 */
    public static final String FREEZE = "FREEZE";

    /** 取消订单解冻：冻结金额 → 可用余额 */
    public static final String UNFREEZE = "UNFREEZE";

    /** 确认收货打款：买家冻结金额 → 卖家可用余额 */
    public static final String SETTLE = "SETTLE";

    /** 退款：冻结金额 → 可用余额（对账上与 UNFREEZE 区分） */
    public static final String REFUND = "REFUND";

    /** 提现：可用余额 → 外部渠道 */
    public static final String WITHDRAW = "WITHDRAW";
}
