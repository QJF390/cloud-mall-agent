package com.exdemo.product.service.impl;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.exdemo.common.constant.PlatformConstant;
import com.exdemo.common.result.R;
import com.exdemo.product.entity.Product;
import com.exdemo.product.mapper.ProductMapper;
import com.exdemo.product.service.ProductSearchService;
import com.exdemo.product.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

/**
 * 商品业务实现
 * TODO: 补充业务逻辑
 * 查询时带上状态条件，普通用户看不到失效商品
 */

@Service
@Slf4j
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Autowired
    ProductMapper productMapper;
    @Autowired
    StringRedisTemplate redisTemplate;
    /** 商品检索读模型（ES）同步入口，失败不抛异常（见 syncToEsBestEffort） */
    @Autowired
    ProductSearchService productSearchService;
    /**
     * Redis 分布式锁释放脚本：只有锁的 value 匹配时才删除，防止误删别人的锁
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("lua/unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public R<Product> addProduct(Product product) {
        // 1. 判空
        if (product == null) {
            return R.fail("商品信息不能为空");
        }
        // 2. 名称必填（DB 为 NOT NULL）：先 trim 再校验，否则 "   " 能绕过非空判断
        String name = product.getName() == null ? null : product.getName().trim();
        if (name == null || name.isEmpty()) {
            return R.fail("商品名称不能为空");
        }
        if (name.length() > 100) {
            return R.fail("商品名称不能超过 100 个字符");
        }
        product.setName(name);
        // 3. 价格必填且非负（DB 为 NOT NULL，负数属业务脏数据）
        if (product.getPrice() == null) {
            return R.fail("商品价格不能为空");
        }
        if (product.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            return R.fail("商品价格不能为负数");
        }
        // 4. 库存兜底 + 非负校验：库存是计数列，负数会让后续"扣减"永远失败
        if (product.getStock() == null) {
            product.setStock(0);
        }
        if (product.getStock() < 0) {
            return R.fail("商品库存不能为负数");
        }
        // 5. 服务端接管不可信字段（这道防线必须放在写库前，不能只靠前端/BFF）
        //    id：前端传 id 就能指定/覆盖别人的商品主键，必须清空交给雪花算法生成
        product.setId(null);
        //    sellerId：B2C 自营，归属恒为平台，防止伪造商品归属
        product.setSellerId(PlatformConstant.PLATFORM_SELLER_ID);
        //    互动计数：由用户行为累加，新增时一律从 0 起算
        product.setViewCount(0);
        product.setLikeCount(0);
        // 6. 状态：默认上架，且只接受 1/0
        if (product.getStatus() == null) {
            product.setStatus(1);
        } else if (product.getStatus() != 1 && product.getStatus() != 0) {
            return R.fail("商品状态只能是 1（上架）或 0（下架）");
        }
        // 7. 时间戳：DB 这两列没有默认值，不显式写入就会是 NULL，
        //    而 C 端列表按 create_time 倒序 ——新商品会沉到列表最底部（甚至排序随机）
        LocalDateTime now = LocalDateTime.now();
        product.setCreateTime(now);
        product.setUpdateTime(now);

        int insert = productMapper.insert(product);
        if (insert > 0) {
            // 写库成功后同步读模型（尽力而为：ES 抖动不影响商品发布，靠 /admin/reindex 兜底）
            syncToEsBestEffort(product.getId(), false);
            return R.ok("商品添加成功", product);
        }
        return R.fail("商品添加失败");
    }

    @Override
    public R<Product> getProductById(Long id) {
        // 判空
        if (id == null || id <= 0){
            return R.fail("商品ID不能为空");
        }
        Product product = productMapper.selectById(id);
        // 已下架商品对 C 端按"不存在"处理：不返回任何有效信息，避免被当成"还能买"或被爬虫探测
        // 注意：后台需要看到已下架商品（否则无法重新上架），走 getProductForAdmin，勿在此开口子
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
            return R.fail("商品不存在或已下架");
        }
        return R.ok("商品查询成功", product);
    }

    @Override
    public List<Product> listProducts() {
        // C 端市集：只出 status=1，下架商品不出现在列表
        // TODO[后续]：支持分页、category 筛选、关键词搜索、按 view_count/create_time 排序
        return productMapper.selectList(
                Wrappers.<Product>lambdaQuery()
                        .eq(Product::getStatus, 1)
                        .orderByDesc(Product::getCreateTime));
    }

    @Override
    public R<Product> updateProduct(Product product) {
        if (product==null){
            return R.fail("商品信息不能为空");
        }
        int update = productMapper.updateById(product);
        if (update>0) {
            // 编辑后必须重建索引文档：否则搜索里还是旧标题/旧价格（用户投诉重灾区）
            syncToEsBestEffort(product.getId(), false);
            return R.ok("商品更新成功", product);
        } else
            return R.fail("商品更新失败");
    }

    @Override
    public R<Boolean> deleteProduct(Long id) {
        if (id==null||id<=0){
            return R.fail("商品ID不能为空");
        }
        int delete = productMapper.deleteById(id);
        if (delete>0) {
            // 物理删除商品：索引文档必须一起删，否则搜索会召回"点进去 404"的幽灵商品
            syncToEsBestEffort(id, true);
            return R.ok("商品删除成功", true);
        } else
            return R.fail("商品删除失败");
    }

    @Override
    public R<Boolean> deductStock(Long productId, Integer quantity) {
    //TODO解决库存超卖问题
        if (quantity==null||quantity<=0){
            return R.fail("数量不能为空");
        }
    //  -----------------分布式锁--------------------
        String lockKey = "lock:product:deduct:" + productId;
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 10L, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(locked)){
            return R.fail("商品库存操作失败，请稍后再试");
        }
        try {
            int result = productMapper.deductStockAtomic(productId, quantity);
            if (result>0)
                return R.ok("商品库存扣除成功", true);
            else
                return R.fail("商品库存不足");

        } catch (Exception e) {
            return R.fail("商品库存操作失败，请稍后再试");
        } finally {
            //  -----------------释放分布式锁--------------------
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), lockValue);
        }
    }

    @Override
    public R<Boolean> revertStock(Long productId, Integer quantity) {
        // TODO[实现]：回补库存
        // 1. 参数校验（productId / quantity 非空且 > 0）
        if (productId==null||productId<=0||quantity==null||quantity<=0){
            return R.fail("传入参数错误");
        }
        // 3. 可以复用扣库存的分布式锁 key（lock:product:deduct:{productId}），
        //    保证"扣减"和"回补"互斥，避免并发下库存错乱
        String lockKey = "lock:product:deduct:" + productId;
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 10L, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(locked)){
            return R.fail("商品库存回补操作失败，请稍后再试");
        }
        try{
            // 2. UPDATE t_product SET stock = stock + #{quantity} WHERE id = #{productId}
            boolean affected = update(
                    Wrappers.<Product>lambdaUpdate()
                            .eq(Product::getId, productId)
                            .setSql("stock = stock + " + quantity)
            );
            if (affected){
                return R.ok("商品库存回补成功", true);
            } else {
                return R.fail("商品库存回补失败");
            }
        } catch (Exception e) {
            log.error("库存回补异常, productId={}, quantity={}", productId, quantity, e);
            return R.fail("商品库存回补操作失败，请稍后再试");
        } finally {
            // 4. 释放分布式锁
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), lockValue);
        }

    }

    @Override
    public List<Product> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        // 用 selectBatchIds 避免 lambdaQuery.in 在某些 MyBatis-Plus 版本/配置下 500
        // 过滤 status=1：下架商品不出现在购物车列表
        // 查不到的商品由购物车侧标记为 invalid（前端置灰提示），不能静默丢弃
        List<Product> products = baseMapper.selectBatchIds(ids);
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }
        return products.stream()
                .filter(p -> Integer.valueOf(1).equals(p.getStatus()))
                .collect(Collectors.toList());
    }

    // ==================== 管理端专用 ====================

    @Override
    public R<Product> getProductForAdmin(Long id) {
        if (id == null || id <= 0) {
            return R.fail("商品ID不能为空");
        }
        Product product = productMapper.selectById(id);
        if (product == null) {
            return R.fail("商品不存在");
        }
        // 管理端不过滤 status：否则已下架商品查不到，运营永远无法重新上架
        return R.ok("商品查询成功", product);
    }

    @Override
    public List<Product> listProductsForAdmin() {
        // 管理端列表：不过滤 status，含已下架
        return productMapper.selectList(
                Wrappers.<Product>lambdaQuery()
                        .orderByDesc(Product::getCreateTime));
    }

    @Override
    public R<Boolean> updateStatus(Long id, Integer status) {
        if (id == null || id <= 0) {
            return R.fail("商品ID不能为空");
        }
        if (status == null || (status != 1 && status != 0)) {
            return R.fail("状态只能是 1（上架）或 0（下架）");
        }
        Product existing = productMapper.selectById(id);
        if (existing == null) {
            return R.fail("商品不存在");
        }
        // 幂等：状态没变直接成功
        if (status.equals(existing.getStatus())) {
            return R.ok(status == 1 ? "商品已是上架状态" : "商品已是下架状态", true);
        }
        // 只更新目标列，避免整对象写回把并发修改冲掉
        boolean updated = update(Wrappers.<Product>lambdaUpdate()
                .eq(Product::getId, id)
                .set(Product::getStatus, status)
                .set(Product::getUpdateTime, LocalDateTime.now()));
        if (!updated) {
            return R.fail("商品状态更新失败");
        }
        // 上下架同步：注意这里同步的是整篇文档（含 status），
        // 检索侧再按 status=1 过滤 —— 下架商品不必从索引删除，重新上架也无需重灌
        syncToEsBestEffort(id, false);
        return R.ok(status == 1 ? "商品已上架" : "商品已下架", true);
    }

    @Override
    public R<Boolean> updateStock(Long id, Integer stock) {
        if (id == null || id <= 0) {
            return R.fail("商品ID不能为空");
        }
        if (stock == null || stock < 0) {
            return R.fail("库存不能为负数");
        }
        if (productMapper.selectById(id) == null) {
            return R.fail("商品不存在");
        }
        // 绝对值覆盖：后台盘点的语义是"以我填的为准"，不是增量
        boolean updated = update(Wrappers.<Product>lambdaUpdate()
                .eq(Product::getId, id)
                .set(Product::getStock, stock)
                .set(Product::getUpdateTime, LocalDateTime.now()));
        if (!updated) {
            return R.fail("库存更新失败");
        }
        syncToEsBestEffort(id, false);
        return R.ok("库存已更新", true);
    }

    private void syncToEsBestEffort(Long productId, boolean delete) {
        if (productId == null || productId <= 0) {
            return;
        }
        try {
            if (delete) {
                productSearchService.deleteFromIndex(productId);
            } else {
                productSearchService.syncOne(productId);
            }
        } catch (Exception e) {
            // 同步失败只告警不抛出：靠 /product/admin/reindex 全量重建兜底
            log.warn("商品索引同步失败(不影响主流程，可全量重建补偿), productId={}, delete={}",
                    productId, delete, e);
        }
    }

}
