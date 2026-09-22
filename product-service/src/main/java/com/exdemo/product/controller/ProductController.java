package com.exdemo.product.controller;

import com.exdemo.common.result.R;
import com.exdemo.product.entity.Product;
import com.exdemo.product.service.ProductSearchService;
import com.exdemo.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品控制器
 * TODO: 补充 REST API 接口
 */
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {
    @Autowired
    private final ProductService productService;

    /** 搜索/分类导航走 ES 读模型（ES 挂了自动降级到 MySQL，见 ProductSearchServiceImpl） */
    @Autowired
    private final ProductSearchService productSearchService;

    @PostMapping("/deductStock")
    public R<Boolean> deductStock(@RequestParam Long productId, @RequestParam Integer quantity) {
        return productService.deductStock(productId, quantity);
    }

    /** 回补库存（Saga 补偿用，仅允许内部服务调用，建议加白名单校验） */
    @PostMapping("/revertStock")
    public R<Boolean> revertStock(@RequestParam Long productId, @RequestParam Integer quantity) {
        return productService.revertStock(productId, quantity);
    }

    @GetMapping("/get/{id}")
    public R<Product> getProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    @GetMapping("/list")
    public R<List<Product>> listProducts() {
        return R.ok(productService.listProducts());
    }

    /**
     * 商品搜索（公开接口，游客可搜）。
     *
     * @param keyword  关键词，可为空（空则按分类浏览）
     * @param category 分类，可为空；"全部" 等同于不筛选
     */
    @GetMapping("/search")
    public R<List<Product>> search(@RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) String category,
                                   @RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "12") int size) {
        return productSearchService.search(keyword, category, page, size);
    }

    /** 分类导航（公开接口）：返回上架商品的分类列表 */
    @GetMapping("/categories")
    public R<List<String>> listCategories() {
        return productSearchService.listCategories();
    }

    /** 批量查询商品（购物车/收藏列表用，避免循环单查造成 N+1） */
    @PostMapping("/listByIds")
    public R<List<Product>> listByIds(@RequestBody List<Long> ids) {
        return R.ok(productService.listByIds(ids));
    }

    @PutMapping("/update")
    public R<Product> updateProduct(@RequestBody Product product) {
        return productService.updateProduct(product);
    }

    @DeleteMapping("/delete/{id}")
    public R<Boolean> deleteProduct(@PathVariable Long id) {
        return productService.deleteProduct(id);
    }

    // ==================== 管理端专用（仅由 admin-service 经 Feign 调用，网关侧禁止外部直连） ====================

    @PostMapping("/admin/add")
    public R<Product> addForAdmin(@RequestBody Product product) {
        return productService.addProduct(product);
    }

    /**
     * 商品列表（管理端专用：含已下架商品）。
     * <p>路径 /product/admin/list 比 /product/admin/{id} 更具体，Spring 会优先精确匹配，不会误入详情。</p>
     */
    @GetMapping("/admin/list")
    public R<List<Product>> listForAdmin() {
        return R.ok(productService.listProductsForAdmin());
    }

    /** 商品详情（管理端专用：含已下架商品，供后台重新上架） */
    @GetMapping("/admin/{id}")
    public R<Product> getForAdmin(@PathVariable Long id) {
        return productService.getProductForAdmin(id);
    }

    /** 上架/下架：1-上架 0-下架 */
    @PostMapping("/admin/{id}/status")
    public R<Boolean> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return productService.updateStatus(id, status);
    }

    /** 调整库存（绝对值） */
    @PostMapping("/admin/{id}/stock")
    public R<Boolean> updateStock(@PathVariable Long id, @RequestParam Integer stock) {
        return productService.updateStock(id, stock);
    }

    @PostMapping("/admin/reindex")
    public R<Boolean> reindex() {
        return productSearchService.syncAll();
    }

}
