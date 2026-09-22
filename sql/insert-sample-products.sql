
USE product_db;

-- 幂等写入：若商品已存在则跳过，避免重复执行时报主键冲突
INSERT IGNORE INTO t_product (
    id, seller_id, name, category, description, story,
    price, stock, cover_image, images, tags, status,
    view_count, like_count, create_time, update_time
) VALUES
(
    3092979519107317761, 1,
    '粗陶手作咖啡杯', '陶瓷',
    '手捏粗陶咖啡杯，保留自然的泥土颗粒与不规则杯口，每一只都是孤品。',
    '泥土在指尖成型，窑火赋予它温度。这一杯，盛的是晨光，也是慢下来的勇气。',
    128.00, 10, '/products/ceramic_coffee_cup.png',
    '["/products/ceramic_coffee_cup.png"]', '["手作","陶瓷","孤品"]',
    1, 0, 0, NOW(), NOW()
),
(
    3094403945019359234, 1,
    '植物染亚麻帆布袋', '布艺',
    '天然植物染色的亚麻帆布袋，渐变草木色调，容量刚好装下一本书与一束花。',
    '用板蓝根与栀子煮出的颜色，会随着使用慢慢褪色，留下属于你的时光痕迹。',
    168.00, 8, '/products/linen_tote_bag.png',
    '["/products/linen_tote_bag.png"]', '["手作","植物染","亚麻"]',
    1, 0, 0, NOW(), NOW()
),
(
    3095503945019359235, 1,
    '手工银质细戒指', '饰品',
    '极细手工锻打银戒指，镶嵌一颗小小原石，简约而不单调。',
    '银是温柔的金属，它会记住你佩戴的每一天，慢慢氧化成只属于你的光泽。',
    89.00, 15, '/products/silver_ring.png',
    '["/products/silver_ring.png"]', '["手作","银饰","极简"]',
    1, 0, 0, NOW(), NOW()
),
(
    3096603945019359236, 1,
    '黑胡桃木手机支架', '木作',
    '整块黑胡桃木手工雕刻的手机支架，弧面贴合手机，底部预留充电孔。',
    '从一块边角料开始，打磨出适合手掌的弧度。好的器物，应该让人想一直用下去。',
    118.00, 12, '/products/walnut_phone_stand.png',
    '["/products/walnut_phone_stand.png"]', '["手作","木作","黑胡桃"]',
    1, 0, 0, NOW(), NOW()
);
