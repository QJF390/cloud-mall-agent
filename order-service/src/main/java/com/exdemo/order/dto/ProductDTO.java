package com.exdemo.order.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品 DTO（Feign 远程调用返回）
 * <p>status 必须带上：购物车要据此把已下架商品标记为失效，而不是静默丢弃。</p>
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
