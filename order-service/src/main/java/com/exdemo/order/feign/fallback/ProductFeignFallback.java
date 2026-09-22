package com.exdemo.order.feign.fallback;

import com.exdemo.common.result.R;
import com.exdemo.order.dto.ProductDTO;
import com.exdemo.order.feign.ProductFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品服务熔断降级
 * 当下游 product-service 挂了或超时，走这里兜底
 */
@Slf4j
@Component
public class ProductFeignFallback implements ProductFeignClient {

    @Override
    public R<ProductDTO> getProductById(Long id) {
        log.error("【熔断降级】查询商品失败, productId={}", id);
        return R.fail("商品服务不可用，请稍后重试");
    }

    @Override
    public R<Boolean> deductStock(Long productId, Integer quantity) {
        log.error("【熔断降级】扣减库存失败, productId={}, quantity={}", productId, quantity);
        return R.fail("商品服务不可用，请稍后重试");
    }

    @Override
    public R<Boolean> revertStock(Long productId, Integer quantity) {
        log.error("【熔断降级】回补库存失败, productId={}, quantity={}", productId, quantity);
        return R.fail("商品服务不可用，请稍后重试");
    }

    @Override
    public R<List<ProductDTO>> listByIds(List<Long> ids) {
        log.error("【熔断降级】批量查询商品失败, ids={}", ids);
        return R.fail("商品服务不可用，请稍后重试");
    }
}
