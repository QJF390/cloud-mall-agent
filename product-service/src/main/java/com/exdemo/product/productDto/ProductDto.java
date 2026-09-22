package com.exdemo.product.productDto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

import java.math.BigDecimal;

public class ProductDto {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    private String description;

    private BigDecimal price;
}
