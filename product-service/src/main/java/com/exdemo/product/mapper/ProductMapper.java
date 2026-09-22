package com.exdemo.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exdemo.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 商品数据访问层
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    @Select("SELECT stock FROM t_product WHERE id = #{productId}")
    Integer getStockById(@Param("productId") Long productId);
//    为了防止库存超卖现象，定义一个把对数据库读判断和写都写在一句sql语句中防止后续的超卖问题
    @Update("UPDATE t_product SET stock = stock - #{quantity}, update_time = NOW() " +
            "WHERE id = #{productId} AND stock >= #{quantity}")
    int deductStockAtomic(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 上架商品的分类列表（去重）。
     * <p>分类导航取自权威数据源 MySQL 而非 ES：ES 抖动时分类栏不能空，
     * 且 DISTINCT 结果与商品真实分布一致，不会出现"点进去没结果"的空分类。</p>
     */
    @Select("SELECT DISTINCT category FROM t_product " +
            "WHERE status = 1 AND category IS NOT NULL AND category <> '' " +
            "ORDER BY category")
    List<String> listDistinctCategories();
}
