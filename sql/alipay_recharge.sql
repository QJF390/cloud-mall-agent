
USE account_db;

CREATE TABLE IF NOT EXISTS t_recharge_order (
    id           BIGINT         NOT NULL COMMENT '主键',
    recharge_no  VARCHAR(32)    NOT NULL COMMENT '本地充值单号（发起支付时生成，即支付宝 out_trade_no）',
    user_id      BIGINT         NOT NULL COMMENT '用户ID',
    amount       DECIMAL(12,2)  NOT NULL COMMENT '充值金额',
    status       VARCHAR(16)    NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID待支付 / PAID已支付 / CLOSED已关闭',
    trade_no     VARCHAR(64)    DEFAULT NULL COMMENT '支付宝交易号（回调或查单返回）',
    pay_time     DATETIME       DEFAULT NULL COMMENT '支付成功时间',
    create_time  DATETIME       DEFAULT NULL COMMENT '创建时间',
    update_time  DATETIME       DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),

    UNIQUE KEY uk_recharge_no (recharge_no),
    -- 待支付单的扫描（对账补偿任务用）
    KEY idx_status_time (status, create_time),
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='充值单（支付宝支付中间态）';

