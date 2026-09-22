package com.exdemo.common.constant;

/**
 * 平台常量（B2C 自营模式，跨服务共享）
 *
 * <p>B2C 自营下，平台是唯一的交易卖方：
 * <ul>
 *   <li>所有订单的 {@code seller_id} 恒为 {@link #PLATFORM_SELLER_ID}，不再随商品发布者变化；</li>
 *   <li>所有商品的 {@code seller_id} 也恒为该值，商品归属由后端强制写入，不接受前端传入。</li>
 * </ul>
 * 用户侧只有"买家"一种身份，不存在"卖家"。</p>
 *
 * <p>放在 common 模块是为了让 product-service / order-service 共用同一份定义，
 * 避免各服务各自维护常量导致取值漂移（单一数据源）。</p>
 */
public final class PlatformConstant {

    /**
     * 平台卖家ID：0 表示平台本身。
     * <p>真实用户ID由雪花算法生成，恒大于 0，故不会与真实用户冲突。</p>
     */
    public static final Long PLATFORM_SELLER_ID = 0L;

    private PlatformConstant() {
    }
}
