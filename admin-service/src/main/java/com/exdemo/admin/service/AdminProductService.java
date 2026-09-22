package com.exdemo.admin.service;

import com.exdemo.admin.dto.PageQuery;
import com.exdemo.admin.dto.PageResult;
import com.exdemo.admin.dto.ProductDTO;
import com.exdemo.admin.dto.ProductSaveDTO;
import com.exdemo.common.result.R;

/**
 * 商品管理服务（聚合 product-service）。
 */
public interface AdminProductService {

    /** 分页查询商品 */
    R<PageResult<ProductDTO>> pageProducts(PageQuery query);

    /** 新增商品 */
    R<Void> saveProduct(ProductSaveDTO product);

    /** 商品详情 */
    R<ProductDTO> getProduct(Long id);

    /** 上架/下架（status: 1-上架 0-下架） */
    R<Void> updateStatus(Long id, Integer status);

    /** 调整库存 */
    R<Void> updateStock(Long id, Integer stock);
}
