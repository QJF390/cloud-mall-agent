package com.exdemo.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 多商品下单请求体（B2C 自营：一次结算生成一张订单，可含多件商品）
 */
@Data
public class CreateOrderRequest {

    /** 买家ID */
    @NotNull(message = "userId 不能为空")
    private Long userId;

    /** 下单商品列表（同一卖家） */
    @NotEmpty(message = "请至少选择一件商品")
    @Valid
    private List<OrderItemReq> items;

    // ============ 收货地址快照（下单时写入 t_order，原 t_address 可免查） ============
    // TODO[C2C改造]：完整版应增加 t_address 地址簿（增删改查 + 默认地址），
    //                下单时从前端传入或按 userId 查默认地址快照到订单。
    //                当前先直接接收前端快照字段。

    /** 收货人姓名 */
    private String receiverName;

    /** 收货人电话 */
    private String receiverPhone;

    /** 收货地址 */
    private String receiverAddress;
}
