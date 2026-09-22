package com.exdemo.product.search;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(indexName = ProductDoc.INDEX_NAME, createIndex = false)
public class ProductDoc {

    public static final String INDEX_NAME = "product";

    /** 文档 id 直接取商品 id —— 天然幂等：重复同步只是覆盖，不会产生脏文档 */
    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String name;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String searchText;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String description;

    /** 创作故事：非结构化、语义强，是最好的召回语料（也直接给 AI 做 RAG 用） */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String story;

    /** 分类：keyword 不做分词，用于精确 filter 与聚合（分类导航） */
    @Field(type = FieldType.Keyword)
    private String category;

    /** 标签数组：keyword 不做分词，精确筛选 / 聚合 */
    @Field(type = FieldType.Keyword)
    private List<String> tags;

    /** 金额用 scaled_float（scaling_factor=100）避免 double 浮点误差，对应 DB 的 DECIMAL(10,2) */
    @Field(type = FieldType.Scaled_Float, scalingFactor = 100)
    private Double price;

    @Field(type = FieldType.Integer)
    private Integer stock;

    @Field(type = FieldType.Byte)
    private Integer status;

    @Field(type = FieldType.Long)
    private Long sellerId;

    /** 排序加权因子（后续做 function_score 用，取 log 防头部商品垄断） */
    @Field(type = FieldType.Integer)
    private Integer viewCount;

    @Field(type = FieldType.Integer)
    private Integer likeCount;

    /** 封面图只存不检索：index=false 既省索引空间，也避免用户搜"png"搜出一堆图片 */
    @Field(type = FieldType.Keyword, index = false)
    private String coverImage;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime createTime;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime updateTime;
}
