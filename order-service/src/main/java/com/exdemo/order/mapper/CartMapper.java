package com.exdemo.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exdemo.order.entity.Cart;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 购物车 Mapper
 * 表：t_cart（唯一键 uk_user_product = user_id + product_id）
 */
@Mapper
public interface CartMapper extends BaseMapper<Cart> {
    /**
     * 原子加购：存在则数量累加，不存在则插入
     * 利用唯一键 uk_user_product (user_id, product_id) 做 upsert，
     * 单条 SQL + 行锁保证并发下不丢更新（避免"先查后改"）
     *
     * @return 影响行数：1=新增，2=数量累加（MySQL 语义）
     */
    @Insert("""
            INSERT INTO t_cart (id, user_id, product_id, quantity, create_time, update_time)
            VALUES (#{id}, #{userId}, #{productId}, #{quantity}, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                quantity = LEAST(quantity + #{quantity}, 99),  -- 左侧 quantity 是当前行旧值，右侧是本次加购量
                update_time = NOW()
            """)
    int insertorUpdate(Cart cart);
}

