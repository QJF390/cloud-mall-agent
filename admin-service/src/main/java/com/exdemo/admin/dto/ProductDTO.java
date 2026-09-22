package com.exdemo.admin.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品信息（管理端展示用，字段与 product-service 的 Product 对齐）。
 */
@Data
public class ProductDTO {

    private Long id;
    private Long sellerId;
    private String name;
    private String category;
    private BigDecimal price;
    private Integer stock;
    private String coverImage;
    /** 1-上架中 0-已下架 */
    private Integer status;
    private LocalDateTime createTime;
}
