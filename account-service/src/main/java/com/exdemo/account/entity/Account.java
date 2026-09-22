package com.exdemo.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户实体
 * TODO: 补充字段、业务逻辑
 */
@Data
@TableName("t_account")
public class Account {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private BigDecimal balance;

    /** 冻结金额（担保交易：买家付款后冻结在平台，确认收货后才打款给卖家） */
    private BigDecimal frozenAmount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
