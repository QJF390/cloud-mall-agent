package com.exdemo.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exdemo.order.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单明细 Mapper
 * 表：t_order_item（商品快照存在这里，一单多商品）
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
