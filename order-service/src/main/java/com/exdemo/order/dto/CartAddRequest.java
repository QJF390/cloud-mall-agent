package com.exdemo.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 加购请求体
 */
@Data
public class CartAddRequest {

    @NotNull(message = "userId 不能为空")
    private Long userId;

    @NotNull(message = "productId 不能为空")
    private Long productId;

    @NotNull(message = "quantity 不能为空")
    @Min(value = 1, message = "quantity 必须大于 0")
    private Integer quantity;
}
