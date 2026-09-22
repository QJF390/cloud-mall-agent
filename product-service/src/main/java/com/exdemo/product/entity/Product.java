package com.exdemo.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体（B2C 自营平台）
 * 字段与 sql/schema.sql 中 t_product 表一一对应
 */
@Data
@TableName("t_product")
public class Product {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 归属方ID（B2C：恒为平台ID，由后端强制写入，前端不可指定） */
    private Long sellerId;

    /** 商品名称 */
    private String name;

    /** 商品分类：陶瓷/绘画/香薰/饰品/布艺/木作/其他 */
    private String category;

    /** 商品描述 */
    private String description;

    /** 创作故事（文艺商品的灵魂，展示在详情页） */
    private String story;

    /** 售价 */
    private BigDecimal price;

    /** 库存（手作孤品/限量款为 1） */
    private Integer stock;

    /** 封面图URL */
    private String coverImage;

    /** 多图URL（JSON数组，如 ["/products/a.png","/products/b.png"]） */
    private String images;

    /** 标签（JSON数组，如 ["手作","限量"]，预留AI推荐用） */
    private String tags;

    /** 状态：1-上架中 0-已下架（软删除，可重新上架） */
    private Integer status;

    /** 浏览量 */
    private Integer viewCount;

    /** 点赞/收藏数 */
    private Integer likeCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
