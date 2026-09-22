package com.exdemo.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细实体（对应 t_order_item 表）
 * B2C：一单可含多件商品，每件商品一条明细
 */
@Data
@TableName("t_order_item")
public class OrderItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属订单ID */
    private Long orderId;

    /** 商品ID */
    private Long productId;

    /** 商品所属卖家ID（B2C：恒为平台ID） */
    private Long sellerId;

    /** 商品名称（下单时快照，商品改名/删除不影响历史订单） */
    private String productName;

    /** 购买数量 */
    private Integer quantity;

    /** 商品单价（下单时快照） */
    private BigDecimal unitPrice;

    /** 小计 = 单价 × 数量 */
    private BigDecimal totalAmount;

    private LocalDateTime createTime;
}
