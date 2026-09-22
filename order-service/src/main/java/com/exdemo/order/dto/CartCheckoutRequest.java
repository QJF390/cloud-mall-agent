package com.exdemo.order.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 购物车结算请求体
 */
@Data
public class CartCheckoutRequest {

    /** 买家ID */
    @NotNull(message = "userId 不能为空")
    private Long userId;

    /**
     * 要结算的购物车项ID列表
     * 为空 = 结算整个购物车；不为空 = 只结算勾选的商品
     */
    @NotEmpty(message = "请选择要结算的商品")
    private List<Long> cartIds;

    /** 收货人姓名（下单时快照） */
    private String receiverName;

    /** 收货人电话（下单时快照） */
    private String receiverPhone;

    /** 收货地址（下单时快照） */
    private String receiverAddress;
}
