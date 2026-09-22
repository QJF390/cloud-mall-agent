package com.exdemo.admin.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单信息（管理端展示用，字段与 order-service 的 Order 对齐）。
 */
@Data
public class OrderDTO {

    private Long id;
    private String orderNo;
    private Long buyerId;
    private Long sellerId;
    private BigDecimal totalAmount;
    /** PENDING/FROZEN/SHIPPED/RECEIVED/COMPLETED/CANCELLED/REFUNDED */
    private String status;
    private String receiverName;
    private String receiverPhone;
    private LocalDateTime createTime;
}
