package com.exdemo.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exdemo.common.result.R;
import com.exdemo.product.entity.Product;
import com.exdemo.product.mapper.ProductMapper;
import com.exdemo.product.search.ProductDoc;
import com.exdemo.product.search.ProductDocConverter;
import com.exdemo.product.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchServiceImpl implements ProductSearchService {

    /** 上架中 */
    private static final int STATUS_ON_SALE = 1;
    /** 单页上限：防止前端传 size=100000 打爆 ES（深分页超过 10000 需要用 search_after） */
    private static final int MAX_PAGE_SIZE = 100;
    /** 全量同步每批条数 */
    private static final int SYNC_BATCH_SIZE = 500;
    /** 前端分类芯片的"全部"项 */
    private static final String CATEGORY_ALL = "全部";

    private final ProductMapper productMapper;
    private final ElasticsearchOperations elasticsearchOperations;

    // ==================== 搜索 ====================

    @Override
    public R<List<Product>> search(String keyword, String category, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String kw = normalize(keyword);
        String cat = normalizeCategory(category);

        try {
            return R.ok("搜索成功", searchFromEs(kw, cat, safePage, safeSize));
        } catch (Exception e) {

            log.warn("[ES] 检索失败，降级为 MySQL 查询。keyword={}, category={}, 原因={}",
                    kw, cat, e.getMessage());
            try {
                return R.ok("搜索成功(降级)", searchFromDb(kw, cat, safePage, safeSize));
            } catch (Exception dbEx) {
                log.error("[ES] 降级查询同样失败，keyword={}, category={}", kw, cat, dbEx);
                return R.fail("搜索服务暂时不可用，请稍后再试");
            }
        }
    }

    /** ES 检索：bool(filter status/category) + match(searchText) */
    private List<Product> searchFromEs(String keyword, String category, int page, int size) {
        Criteria criteria = new Criteria("status").is(STATUS_ON_SALE);
        if (category != null) {
            criteria = criteria.and(new Criteria("category").is(category));
        }
        if (keyword != null) {
            criteria = criteria.and(new Criteria("searchText").matches(keyword));
        }

        CriteriaQuery query = new CriteriaQuery(criteria, PageRequest.of(page - 1, size));
        // 纯分类浏览（无关键词）时相关度没有意义，按最新上架排序
        if (keyword == null) {
            query.addSort(Sort.by(Sort.Direction.DESC, "createTime"));
        }

        SearchHits<ProductDoc> hits = elasticsearchOperations.search(query, ProductDoc.class);
        return hits.getSearchHits().stream()
                .map(hit -> ProductDocConverter.to(hit.getContent()))
                .collect(Collectors.toList());
    }

    private List<Product> searchFromDb(String keyword, String category, int page, int size) {
        LambdaQueryWrapper<Product> wrapper = Wrappers.<Product>lambdaQuery()
                .eq(Product::getStatus, STATUS_ON_SALE);

        if (category != null) {
            wrapper.eq(Product::getCategory, category);
        }
        if (keyword != null) {
            // 这里必须用 and(...) 把 OR 条件括起来，否则会破坏前面 status 的 AND 语义
            wrapper.and(w -> w.like(Product::getName, keyword)
                    .or().like(Product::getDescription, keyword)
                    .or().like(Product::getStory, keyword)
                    .or().like(Product::getTags, keyword));
        }
        wrapper.orderByDesc(Product::getCreateTime);

        Page<Product> result = productMapper.selectPage(new Page<>(page, size), wrapper);
        return result.getRecords();
    }

    @Override
    public R<List<String>> listCategories() {
        // 分类取自 MySQL（权威数据）：ES 挂了分类导航也得能用；
        // 且 DISTINCT 结果一定与商品真实分布一致，不会出现"搜空的分类芯片"
        return R.ok("查询成功", productMapper.listDistinctCategories());
    }

    // ==================== 索引同步 ====================

    @Override
    public R<Boolean> syncOne(Long productId) {
        if (productId == null || productId <= 0) {
            return R.fail("商品ID不能为空");
        }
        Product product = productMapper.selectById(productId);
        if (product == null) {
            // 商品已被物理删除：顺手清掉 ES 里的残留文档，避免"搜得到、点进去 404"
            return deleteFromIndex(productId);
        }
        // save 带 @Id → upsert（存在则覆盖），重复调用天然幂等
        elasticsearchOperations.save(ProductDocConverter.from(product));
        return R.ok("索引同步成功", true);
    }

    @Override
    public R<Boolean> syncAll() {
        long page = 1;
        long total = 0;
        while (true) {
            // 按 id 排序分页拉取，避免"全量捞进内存"——那正是要消灭的定时炸弹写法
            Page<Product> batch = productMapper.selectPage(
                    new Page<>(page, SYNC_BATCH_SIZE),
                    Wrappers.<Product>lambdaQuery().orderByAsc(Product::getId));

            List<Product> records = batch.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }
            List<ProductDoc> docs = records.stream()
                    .map(ProductDocConverter::from)
                    .collect(Collectors.toList());
            // 逐条 save（避免 save(T) / save(Iterable) 的重载歧义）；
            // 追求吞吐时可换成 ES Bulk API，这里以正确性优先
            docs.forEach(elasticsearchOperations::save);

            total += docs.size();
            if (records.size() < SYNC_BATCH_SIZE) {
                break;
            }
            page++;
        }
        log.info("[ES] 全量同步完成，共写入 {} 条文档", total);
        return R.ok("全量同步完成，共 " + total + " 条", true);
    }

    @Override
    public R<Boolean> deleteFromIndex(Long productId) {
        if (productId == null || productId <= 0) {
            return R.fail("商品ID不能为空");
        }
        elasticsearchOperations.delete(String.valueOf(productId), ProductDoc.class);
        return R.ok("索引删除成功", true);
    }

    // ==================== 工具方法 ====================

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeCategory(String category) {
        String trimmed = normalize(category);
        return (trimmed == null || CATEGORY_ALL.equals(trimmed)) ? null : trimmed;
    }
}
