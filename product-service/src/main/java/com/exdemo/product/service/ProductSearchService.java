package com.exdemo.product.service;

import com.exdemo.common.result.R;
import com.exdemo.product.entity.Product;

import java.util.List;

public interface ProductSearchService {

    /**
     * 关键词搜索（名称/分类/描述/故事/标签，分词匹配 + 相关度排序，支持分页）。
     *
     * @param keyword  关键词，可为空（空则按分类浏览）
     * @param category 分类，可为空；传"全部"等同于不筛选
     * @param page     页码，从 1 开始
     * @param size     每页条数，内部有上限保护
     */
    R<List<Product>> search(String keyword, String category, int page, int size);

    /** 分类列表（供前端分类导航使用） */
    R<List<String>> listCategories();

    /** 全量同步：MySQL 全量数据刷入 ES（索引初始化 / 数据修复时调用） */
    R<Boolean> syncAll();

    /** 增量同步：单条商品写入/更新 ES（新增/编辑/上下架/改库存后调用） */
    R<Boolean> syncOne(Long productId);

    /** 删除索引：商品被物理删除时调用 */
    R<Boolean> deleteFromIndex(Long productId);
}
