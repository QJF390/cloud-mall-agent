package com.exdemo.admin.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增商品入参（管理端专用）。
 *
 * <p>刻意与读模型 {@link ProductDTO} 分开，属于"读写模型分离"：
 * <ul>
 *   <li>读模型是"服务端给前端看什么"，字段越全越好；</li>
 *   <li>写模型是"前端能提交什么"，字段必须最小化。</li>
 * </ul>
 * 如果直接复用 ProductDTO 接收入参，前端就能顺手把 {@code id} / {@code sellerId} /
 * {@code createTime} / {@code viewCount} 一起传上来——这些字段必须由服务端生成，
 * 一旦能被外部指定，就是越权写入（伪造商品归属、覆盖他人商品主键）。</p>
 */
@Data
public class ProductSaveDTO {

    /** 商品名称（必填） */
    private String name;

    /** 商品分类：陶瓷/绘画/香薰/饰品/布艺/木作/其他 */
    private String category;

    /** 售价（必填，非负） */
    private BigDecimal price;

    /** 库存（不传按 0，非负） */
    private Integer stock;

    /** 封面图URL */
    private String coverImage;

    /** 商品描述 */
    private String description;

    /** 创作故事 */
    private String story;

    /** 多图URL（JSON数组字符串，如 ["/products/a.png"]） */
    private String images;

    /** 标签（JSON数组字符串，如 ["手作","限量"]） */
    private String tags;

    /** 状态：1-上架中 0-已下架（不传默认上架） */
    private Integer status;
}
