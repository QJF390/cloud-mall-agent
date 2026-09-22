package com.exdemo.admin.feign;

import com.exdemo.admin.dto.ProductDTO;
import com.exdemo.admin.dto.ProductSaveDTO;
import com.exdemo.admin.feign.fallback.ProductFeignFallback;
import com.exdemo.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 商品服务 Feign 客户端（商品管理聚合入口）。
 *
 * <p>上下架/改库存刻意用<b>专用管理接口</b>，而不是复用 {@code PUT /product/update}：
 * "读全量商品 → 改一个字段 → 整体写回"存在并发覆盖（丢失更新）问题，
 * 两个管理员同时操作时后写的一方会把人家的改动冲掉。</p>
 */
@FeignClient(name = "product-service", fallback = ProductFeignFallback.class)
public interface ProductFeignClient {

    /** 商品列表（管理端专用出口：含已下架，管理端需要看到全部才能重新上架） */
    @GetMapping("/product/admin/list")
    R<List<ProductDTO>> listProducts();

    /** 新增商品（管理端专用出口） */
    @PostMapping("/product/admin/add")
    R<ProductDTO> addProduct(@RequestBody ProductSaveDTO product);

    /** 商品详情（管理端专用出口：含已下架） */
    @GetMapping("/product/admin/{id}")
    R<ProductDTO> getProductById(@PathVariable("id") Long id);

    /** 上架/下架：1-上架 0-下架 */
    @PostMapping("/product/admin/{id}/status")
    R<Boolean> updateStatus(@PathVariable("id") Long id, @RequestParam("status") Integer status);

    /** 调整库存（绝对值） */
    @PostMapping("/product/admin/{id}/stock")
    R<Boolean> updateStock(@PathVariable("id") Long id, @RequestParam("stock") Integer stock);
}
