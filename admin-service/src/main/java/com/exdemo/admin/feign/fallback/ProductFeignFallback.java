package com.exdemo.admin.feign.fallback;

import com.exdemo.admin.dto.ProductDTO;
import com.exdemo.admin.dto.ProductSaveDTO;
import com.exdemo.admin.feign.ProductFeignClient;
import com.exdemo.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品服务熔断降级。
 */
@Slf4j
@Component
public class ProductFeignFallback implements ProductFeignClient {

    private static final String UNAVAILABLE = "商品服务暂时不可用，请稍后重试";

    @Override
    public R<List<ProductDTO>> listProducts() {
        log.warn("[fallback] product-service 不可用, listProducts");
        return R.fail(503, UNAVAILABLE);
    }

    @Override
    public R<ProductDTO> getProductById(Long id) {
        log.warn("[fallback] product-service 不可用, getProductById id={}", id);
        return R.fail(503, UNAVAILABLE);
    }

    @Override
    public R<ProductDTO> addProduct(ProductSaveDTO product) {
        log.warn("[fallback] product-service 不可用, addProduct name={}", product == null ? null : product.getName());
        return R.fail(503, UNAVAILABLE);
    }

    @Override
    public R<Boolean> updateStatus(Long id, Integer status) {
        log.warn("[fallback] product-service 不可用, updateStatus id={}, status={}", id, status);
        return R.fail(503, UNAVAILABLE);
    }

    @Override
    public R<Boolean> updateStock(Long id, Integer stock) {
        log.warn("[fallback] product-service 不可用, updateStock id={}, stock={}", id, stock);
        return R.fail(503, UNAVAILABLE);
    }
}
