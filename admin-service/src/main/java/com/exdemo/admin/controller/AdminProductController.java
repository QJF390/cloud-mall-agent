package com.exdemo.admin.controller;

import com.exdemo.admin.annotation.AdminOperation;
import com.exdemo.admin.dto.PageQuery;
import com.exdemo.admin.dto.PageResult;
import com.exdemo.admin.dto.ProductDTO;
import com.exdemo.admin.dto.ProductSaveDTO;
import com.exdemo.admin.service.AdminProductService;
import com.exdemo.admin.service.FileStorageService;
import com.exdemo.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 商品管理接口。
 *
 * <p>网关路由：/admin/** → admin-service；本类映射 /admin/product。</p>
 */
@RestController
@RequestMapping("/admin/product")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;

    private final FileStorageService fileStorageService;

    /**
     * 上传商品图片，返回可直接访问的相对 URL。
     *
     * <p>返回相对 URL（{@code /uploads/...}）而不是带域名的绝对 URL：
     * 绝对 URL 会把"当前部署域名"烧进数据库，换域名/上 CDN 时只能全表刷数据。</p>
     */
    @AdminOperation(module = "PRODUCT", action = "上传商品图片")
    @PostMapping("/upload")
    public R<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        return R.ok("上传成功", Map.of("url", fileStorageService.store(file)));
    }

    /** 商品列表（分页 + 分类/状态筛选） */
    @GetMapping("/page")
    public R<PageResult<ProductDTO>> page(PageQuery query) {
        return adminProductService.pageProducts(query);
    }

    /** 商品详情 */
    @GetMapping("/{id}")
    public R<ProductDTO> detail(@PathVariable Long id) {
        return adminProductService.getProduct(id);
    }

    /** 新增商品 */
    @AdminOperation(module = "PRODUCT", action = "新增商品")
    @PostMapping("/save")
    public R<Void> save(@RequestBody ProductSaveDTO product) {
        return adminProductService.saveProduct(product);
    }

    /** 上架/下架 */
    @AdminOperation(module = "PRODUCT", action = "商品上下架")
    @PostMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return adminProductService.updateStatus(id, status);
    }

    /** 调整库存 */
    @AdminOperation(module = "PRODUCT", action = "调整商品库存")
    @PostMapping("/{id}/stock")
    public R<Void> updateStock(@PathVariable Long id, @RequestParam Integer stock) {
        return adminProductService.updateStock(id, stock);
    }
}
