package com.exdemo.product.config;

import com.exdemo.product.search.ProductDoc;
import com.exdemo.product.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductIndexInitializer implements ApplicationRunner {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ProductSearchService productSearchService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(ProductDoc.class);
            if (!indexOps.exists()) {
                indexOps.createWithMapping();
                log.info("[ES] 索引 '{}' 创建成功", ProductDoc.INDEX_NAME);
            }

            // 空索引自动灌一次数据，保证"第一次跑起来就能搜到东西"
            Long onSaleCount = elasticsearchOperations.count(
                    new CriteriaQuery(new Criteria("status").is(1)), ProductDoc.class);
            if (onSaleCount != null && onSaleCount == 0) {
                log.info("[ES] 索引为空，触发首次全量同步…");
                productSearchService.syncAll();
            }
        } catch (Exception e) {

            log.warn("[ES] 索引初始化失败，搜索将降级为 MySQL。原因: {}", e.getMessage());
            log.warn("[ES] 排查提示：1) ES 是否已启动(默认 http://127.0.0.1:9200)；2) 是否已安装 IK 分词器插件");
        }
    }
}
