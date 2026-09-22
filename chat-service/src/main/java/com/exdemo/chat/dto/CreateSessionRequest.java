package com.exdemo.chat.dto;

import lombok.Data;

/**
 * 发起会话请求。
 */
@Data
public class CreateSessionRequest {

    /** 对方ID（商品发布者） */
    private Long sellerId;

    /** 关联商品ID（从商品详情进入咨询时带上，可为空） */
    private Long productId;

    /** 商品名快照（可为空） */
    private String productName;
}
