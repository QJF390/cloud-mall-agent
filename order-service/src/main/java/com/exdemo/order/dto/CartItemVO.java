package com.exdemo.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车列表项 VO（购物车记录 + 商品快照）
 */
@Data
public class CartItemVO {

    /** 购物车记录ID */
    private Long cartId;

    /** 商品ID */
    private Long productId;

    /** 商品名称（Feign 查 product-service 填充） */
    private String productName;

    /** 商品封面图（Feign 填充） */
    private String coverImage;

    /** 商品单价（Feign 填充，注意下单时应以最新价为准） */
    private BigDecimal unitPrice;

    /** 购买数量 */
    private Integer quantity;

    /** 小计 = 单价 × 数量 */
    private BigDecimal totalAmount;

    /** 商品所属卖家ID（B2C：下单时不再使用） */
    private Long sellerId;

    /** 商品失效标记（商品已下架/不存在时为 true，前端置灰提示） */
    private Boolean invalid;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
