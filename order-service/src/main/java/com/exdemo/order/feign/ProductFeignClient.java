package com.exdemo.order.feign;

import com.exdemo.common.result.R;
import com.exdemo.order.dto.ProductDTO;
import com.exdemo.order.feign.fallback.ProductFeignFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品服务 Feign 客户端
 * name = "product-service" → 自动从 Nacos 发现实例
 * LoadBalancer 自动在多个实例间轮询
 * Resilience4j 熔断 → ProductFeignFallback 兜底
 */
@FeignClient(name = "product-service", fallback = ProductFeignFallback.class)
public interface ProductFeignClient {

    /** 查询商品 */
    @GetMapping("/product/get/{id}")
    R<ProductDTO> getProductById(@PathVariable("id") Long id);

    /** 扣减库存 */
    @PostMapping("/product/deductStock")
    R<Boolean> deductStock(@RequestParam("productId") Long productId,
                           @RequestParam("quantity") Integer quantity);

    @PostMapping("/product/revertStock")
    R<Boolean> revertStock(@RequestParam("productId") Long productId,
                           @RequestParam("quantity") Integer quantity);

    /** 批量查询商品 */
    @PostMapping("/product/listByIds")
    R<List<ProductDTO>> listByIds(@RequestBody List<Long> ids);
}
