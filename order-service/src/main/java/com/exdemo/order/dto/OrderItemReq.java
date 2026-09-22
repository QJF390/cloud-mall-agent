package com.exdemo.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 下单商品项请求（多商品订单中的一个明细）
 */
@Data
public class OrderItemReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品ID */
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    /** 购买数量 */
    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量必须大于0")
    private Integer quantity;
}
