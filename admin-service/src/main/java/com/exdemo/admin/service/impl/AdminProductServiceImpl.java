package com.exdemo.admin.service.impl;

import com.exdemo.admin.dto.PageQuery;
import com.exdemo.admin.dto.PageResult;
import com.exdemo.admin.dto.ProductDTO;
import com.exdemo.admin.dto.ProductSaveDTO;
import com.exdemo.admin.feign.ProductFeignClient;
import com.exdemo.admin.service.AdminProductService;
import com.exdemo.admin.util.PageUtil;
import com.exdemo.common.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 商品管理服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProductServiceImpl implements AdminProductService {

    /** 商品状态：上架 */
    private static final int STATUS_ON = 1;

    /** 商品状态：下架 */
    private static final int STATUS_OFF = 0;

    /** 库存上限（防止手误输入 999999999 把库存玩坏） */
    private static final int MAX_STOCK = 999_999;

    /** 价格上限（对齐 t_product.price DECIMAL(10,2) 的可表示范围，避免写库时被截断/报错） */
    private static final BigDecimal MAX_PRICE = new BigDecimal("999999.99");

    /** 名称长度上限（对齐 t_product.name VARCHAR(100)） */
    private static final int MAX_NAME_LENGTH = 100;

    private final ProductFeignClient productFeignClient;

    @Override
    public R<PageResult<ProductDTO>> pageProducts(PageQuery query) {
        R<List<ProductDTO>> remote = productFeignClient.listProducts();
        if (!isOk(remote)) {
            return R.fail(remote == null ? 500 : remote.getCode(),
                    remote == null ? "商品服务返回异常" : remote.getMessage());
        }

        String keyword = trimToNull(query.getKeyword());
        Integer status = query.getStatus();

        List<ProductDTO> filtered = remote.getData().stream()
                .filter(Objects::nonNull)
                .filter(product -> matchKeyword(product, keyword))
                .filter(product -> status == null || status.equals(product.getStatus()))
                .sorted(Comparator.comparing(ProductDTO::getCreateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        return R.ok(PageUtil.paginate(filtered, query));
    }

    @Override
    public R<Void> saveProduct(ProductSaveDTO product) {
        if (product == null) {
            return R.badRequest("商品信息不能为空");
        }
        // 入参校验在 BFF 与 product-service 各做一遍，这不是重复劳动：
        // 前端校验是"体验"，BFF 校验是"给运营看的即时反馈"，下游服务校验才是"数据最后一道门"。
        // 后台接口同样能被脚本直调，任何一层都不能假设"上游一定校验过"。
        String name = trimToNull(product.getName());
        if (name == null) {
            return R.badRequest("商品名称不能为空");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return R.badRequest("商品名称不能超过 " + MAX_NAME_LENGTH + " 个字符");
        }
        if (product.getPrice() == null) {
            return R.badRequest("商品价格不能为空");
        }
        if (product.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            return R.badRequest("商品价格不能为负数");
        }
        if (product.getPrice().compareTo(MAX_PRICE) > 0) {
            return R.badRequest("商品价格不能超过 " + MAX_PRICE);
        }
        // 库存不传按 0：避免 null 直穿到 DB 的 NOT NULL 列报错（错误信息对运营毫无意义）
        if (product.getStock() == null) {
            product.setStock(0);
        }
        if (product.getStock() < 0) {
            return R.badRequest("库存不能为负数");
        }
        if (product.getStock() > MAX_STOCK) {
            return R.badRequest("库存不能超过 " + MAX_STOCK);
        }
        if (product.getStatus() != null && product.getStatus() != STATUS_ON && product.getStatus() != STATUS_OFF) {
            return R.badRequest("商品状态只能是 1（上架）或 0（下架）");
        }
        if (product.getStatus() == null) {
            product.setStatus(STATUS_ON);
        }
        product.setName(name);

        R<ProductDTO> remote = productFeignClient.addProduct(product);
        if (!isOk(remote)) {
            return R.fail(remote == null ? 500 : remote.getCode(),
                    remote == null ? "商品服务返回异常" : remote.getMessage());
        }
        return R.ok(STATUS_ON == product.getStatus() ? "商品创建成功，已上架" : "商品创建成功", null);
    }

    @Override
    public R<ProductDTO> getProduct(Long id) {
        if (id == null || id <= 0) {
            return R.badRequest("商品ID不能为空");
        }
        R<ProductDTO> remote = productFeignClient.getProductById(id);
        if (!isOk(remote)) {
            return R.fail(remote == null ? 500 : remote.getCode(),
                    remote == null ? "商品服务返回异常" : remote.getMessage());
        }
        return R.ok(remote.getData());
    }

    @Override
    public R<Void> updateStatus(Long id, Integer status) {
        if (id == null || id <= 0) {
            return R.badRequest("商品ID不能为空");
        }
        if (status == null || (status != STATUS_ON && status != STATUS_OFF)) {
            return R.badRequest("商品状态只能是 1（上架）或 0（下架）");
        }
        R<Boolean> remote = productFeignClient.updateStatus(id, status);
        if (!isOk(remote)) {
            return R.fail(remote == null ? 500 : remote.getCode(),
                    remote == null ? "商品服务返回异常" : remote.getMessage());
        }
        if (!Boolean.TRUE.equals(remote.getData())) {
            return R.fail(status == STATUS_ON ? "商品上架失败" : "商品下架失败");
        }
        return R.ok(status == STATUS_ON ? "商品已上架" : "商品已下架", null);
    }

    @Override
    public R<Void> updateStock(Long id, Integer stock) {
        if (id == null || id <= 0) {
            return R.badRequest("商品ID不能为空");
        }
        if (stock == null || stock < 0) {
            return R.badRequest("库存不能为负数");
        }
        if (stock > MAX_STOCK) {
            return R.badRequest("库存不能超过 " + MAX_STOCK);
        }
        R<Boolean> remote = productFeignClient.updateStock(id, stock);
        if (!isOk(remote)) {
            return R.fail(remote == null ? 500 : remote.getCode(),
                    remote == null ? "商品服务返回异常" : remote.getMessage());
        }
        if (!Boolean.TRUE.equals(remote.getData())) {
            return R.fail("库存调整失败");
        }
        return R.ok("库存已更新", null);
    }

    // ==================== 私有方法 ====================

    private boolean matchKeyword(ProductDTO product, String keyword) {
        if (keyword == null) {
            return true;
        }
        String lower = keyword.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(product.getName(), lower)
                || containsIgnoreCase(product.getCategory(), lower);
    }

    private boolean containsIgnoreCase(String source, String lowerKeyword) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    private boolean isOk(R<?> remote) {
        return remote != null && remote.getCode() == 200 && remote.getData() != null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
