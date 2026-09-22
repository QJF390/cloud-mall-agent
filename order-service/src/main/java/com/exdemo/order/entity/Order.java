package com.exdemo.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表实体（B2C 自营：平台是唯一卖方，一次结算只生成一张订单）
 * 商品快照已迁移至 t_order_item，本表只保留订单级字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_order")
public class Order {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 订单号（幂等/查单用） */
    private String orderNo;

    private Long userId;

    /** 买家ID（对应 t_order.buyer_id） */
    @TableField("buyer_id")
    private Long buyerId;

    /** 卖家ID（B2C：恒为平台ID，用于资金结算归属平台） */
    private Long sellerId;

    /** 订单总金额 */
    private BigDecimal totalAmount;

    /** 订单状态（C2C 担保交易状态机）：
     *  PENDING-待付款 / FROZEN-已付款(资金冻结在平台) / SHIPPED-已发货 /
     *  RECEIVED-已收货 / COMPLETED-已完成(平台已打款给卖家) /
     *  CANCELLED-已取消 / REFUNDED-已退款 */
    private String status;

    /** 收货人姓名（下单时快照） */
    private String receiverName;

    /** 收货人电话（下单时快照） */
    private String receiverPhone;

    /** 收货地址（下单时快照） */
    private String receiverAddress;

    /** 退款原因（status=REFUNDED 时记录） */
    private String refundReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
