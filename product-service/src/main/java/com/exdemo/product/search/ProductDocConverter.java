package com.exdemo.product.search;

import com.exdemo.product.entity.Product;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public final class ProductDocConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProductDocConverter() {
    }

    /** MySQL 实体 → ES 文档 */
    public static ProductDoc from(Product p) {
        ProductDoc doc = new ProductDoc();
        doc.setId(p.getId());
        doc.setName(p.getName());
        doc.setCategory(p.getCategory());
        doc.setDescription(p.getDescription());
        doc.setStory(p.getStory());

        List<String> tags = parseStringArray(p.getTags());
        doc.setTags(tags);

        // DECIMAL(10,2) → scaled_float：只做类型转换，不改变精度语义
        doc.setPrice(p.getPrice() == null ? null : p.getPrice().doubleValue());
        doc.setStock(p.getStock());
        doc.setStatus(p.getStatus());
        doc.setSellerId(p.getSellerId());
        doc.setViewCount(p.getViewCount());
        doc.setLikeCount(p.getLikeCount());
        doc.setCoverImage(p.getCoverImage());
        doc.setCreateTime(p.getCreateTime());
        doc.setUpdateTime(p.getUpdateTime());

        doc.setSearchText(buildSearchText(p, tags));
        return doc;
    }

    public static Product to(ProductDoc doc) {
        Product p = new Product();
        p.setId(doc.getId());
        p.setName(doc.getName());
        p.setCategory(doc.getCategory());
        p.setDescription(doc.getDescription());
        p.setStory(doc.getStory());
        p.setTags(toJsonArray(doc.getTags()));
        p.setPrice(doc.getPrice() == null ? null : BigDecimal.valueOf(doc.getPrice()));
        p.setStock(doc.getStock());
        p.setStatus(doc.getStatus());
        p.setSellerId(doc.getSellerId());
        p.setViewCount(doc.getViewCount());
        p.setLikeCount(doc.getLikeCount());
        p.setCoverImage(doc.getCoverImage());
        p.setCreateTime(doc.getCreateTime());
        p.setUpdateTime(doc.getUpdateTime());
        return p;
    }

    /**
     * 解析 JSON 数组字符串（如 {@code ["手作","陶瓷"]}）。
     *
     * <p>容错处理：DB 里这一列是创作者/运营填的，可能是 null、空串、甚至非法 JSON。
     * 这里任何一种异常都退化为空列表，绝不让"一条脏数据"导致整批索引同步失败。</p>
     */
    public static List<String> parseStringArray(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            // 兼容非 JSON 的逗号分隔写法，例如 "手作,陶瓷"
            return List.of(json.replace("[", "").replace("]", "").replace("\"", "").split(","));
        }
    }

    private static String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(list);
        } catch (Exception e) {
            return null;
        }
    }

    /** 把"用户可能搜到的所有文本"拼成一个召回字段 */
    private static String buildSearchText(Product p, List<String> tags) {
        StringBuilder sb = new StringBuilder();
        append(sb, p.getName());
        append(sb, p.getCategory());
        append(sb, p.getDescription());
        append(sb, p.getStory());
        if (tags != null) {
            tags.forEach(t -> append(sb, t));
        }
        return sb.toString().trim();
    }

    private static void append(StringBuilder sb, String v) {
        if (v != null && !v.isBlank()) {
            sb.append(v).append(' ');
        }
    }
}
