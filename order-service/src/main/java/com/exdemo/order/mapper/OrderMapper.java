package com.exdemo.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exdemo.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单数据访问层
 * TODO: 补充自定义 SQL 方法
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
