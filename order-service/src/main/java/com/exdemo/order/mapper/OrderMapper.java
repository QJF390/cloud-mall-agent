package com.exdemo.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exdemo.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单数据访问层
 * <p>基础 CRUD 由 BaseMapper 提供，此处只放无法用 LambdaWrapper 表达的语句。</p>
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 扫描超时未支付订单。
     * <p>只扫 PENDING：FROZEN 及之后的状态资金已冻结/已履约，不能由定时任务擅自关单，
     * 否则会破坏担保交易状态机。LIMIT 限制单次处理量，避免大促堆积时一次拉爆内存。</p>
     */
    @Select("SELECT * FROM t_order " +
            "WHERE status = 'PENDING' AND create_time < #{deadline} " +
            "ORDER BY create_time " +
            "LIMIT #{limit}")
    List<Order> selectTimeoutPending(@Param("deadline") LocalDateTime deadline,
                                     @Param("limit") int limit);
}
