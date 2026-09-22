package com.exdemo.order.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品 DTO（Feign 远程调用返回）
 * TODO: 补充字段，与 product-service 返回结构一致
 */
@Data
public class ProductDTO {

    private Long id;

    private String name;

    private BigDecimal price;

    private Integer stock;

    /** 封面图URL */
    private String coverImage;

    /** 卖家ID（B2C：下单时不再使用，订单恒归属平台） */
    private Long sellerId;

    /** 状态：1-上架中 0-已下架 */
    private Integer status;
}
