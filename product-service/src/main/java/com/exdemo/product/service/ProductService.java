package com.exdemo.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.exdemo.common.result.R;
import com.exdemo.product.entity.Product;

import java.util.List;

/**
 * 商品业务接口
 */
public interface ProductService extends IService<Product> {

    /**
     * 新增商品。
     * <p><b>管理端专用</b>：B2C 自营下商品只能由运营发布，接口只暴露在 {@code POST /product/admin/add}
     * （网关封禁外部直连，仅 admin-service 经 Feign 调用）。</p>
     */
    R<Product> addProduct(Product product);

    R<Product>  getProductById(Long id);                  // 根据ID查询商品

    List<Product> listProducts();                     // 查询全部商品

    R<Product> updateProduct(Product product);        // 更新商品

    R<Boolean> deleteProduct(Long id);                // 删除商品

    R<Boolean> deductStock(Long productId, Integer quantity);  // 扣减库存

    /** 回补库存（Saga 补偿：下单扣款失败 / 取消订单 / 退款时调用） */
    R<Boolean> revertStock(Long productId, Integer quantity);

    /** 批量查询商品（购物车/收藏列表用，避免 N+1 循环单查） */
    List<Product> listByIds(List<Long> ids);

    // ==================== 管理端专用（仅由 admin-service 经 Feign 调用） ====================

    R<Product> getProductForAdmin(Long id);

    /**
     * 管理端专用：查询全部商品（含已下架）。
     * <p>C 端市集只出上架商品，后台列表必须看全，两者语义相反，故拆成两个出口。</p>
     */
    List<Product> listProductsForAdmin();

    /** 上架/下架：1-上架 0-下架（独立接口，避免"读全量→改字段→整体写回"的并发覆盖） */
    R<Boolean> updateStatus(Long id, Integer status);

    /** 调整库存（绝对值，供后台盘点/纠错） */
    R<Boolean> updateStock(Long id, Integer stock);
}
