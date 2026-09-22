package com.exdemo.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车实体（对应 t_cart 表）
 * 同一买家同一商品只有一条记录（唯一键 user_id + product_id），数量累加
 */
@Data
@TableName("t_cart")
public class Cart {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 买家ID */
    private Long userId;

    /** 商品ID */
    private Long productId;

    /** 数量 */
    private Integer quantity;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
