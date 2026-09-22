
USE user_db;

DROP PROCEDURE IF EXISTS sp_add_column;
DELIMITER $$
CREATE PROCEDURE sp_add_column(
    IN p_table  VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_ddl    VARCHAR(800)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = p_table
          AND COLUMN_NAME  = p_column
    ) THEN
        SET @ddl_sql = p_ddl;
        PREPARE stmt FROM @ddl_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- 1. t_user（对应 User 实体）
--    实体字段：id, username, password, phone, email, avatar, bio,
--              creditScore, status, createTime, updateTime
CREATE TABLE IF NOT EXISTS t_user (
    id           BIGINT        NOT NULL COMMENT '用户ID（雪花ID）',
    username     VARCHAR(50)   NOT NULL COMMENT '用户名',
    password     VARCHAR(100)  NOT NULL COMMENT '密码（BCrypt 加密）',
    phone        VARCHAR(20)   DEFAULT NULL COMMENT '手机号',
    email        VARCHAR(100)  DEFAULT NULL COMMENT '邮箱',
    avatar       VARCHAR(255)  DEFAULT NULL COMMENT '头像图片URL',
    bio          VARCHAR(500)  DEFAULT NULL COMMENT '个人简介（展示在个人主页/作品集）',
    credit_score INT           DEFAULT 100 COMMENT '信用分（C2C卖家信誉）',
    status       TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-已禁用（后台封禁）',
    create_time  DATETIME      DEFAULT NULL COMMENT '创建时间',
    update_time  DATETIME      DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB COMMENT='用户表（C2C：人人可买可卖，个人主页=作品集）';

CALL sp_add_column('t_user', 'avatar',       'ALTER TABLE t_user ADD COLUMN avatar       VARCHAR(255) DEFAULT NULL COMMENT ''头像图片URL''');
CALL sp_add_column('t_user', 'bio',          'ALTER TABLE t_user ADD COLUMN bio          VARCHAR(500) DEFAULT NULL COMMENT ''个人简介''');
CALL sp_add_column('t_user', 'credit_score', 'ALTER TABLE t_user ADD COLUMN credit_score INT DEFAULT 100 COMMENT ''信用分（C2C卖家信誉）''');
-- status：后台"禁用/启用用户"依赖此列（默认 1=正常，存量用户不会因此被误封）
CALL sp_add_column('t_user', 'status',       'ALTER TABLE t_user ADD COLUMN status       TINYINT NOT NULL DEFAULT 1 COMMENT ''状态：1-正常 0-已禁用''');

DROP PROCEDURE IF EXISTS sp_add_column;

USE account_db;

DROP PROCEDURE IF EXISTS sp_add_column;
DELIMITER $$
CREATE PROCEDURE sp_add_column(
    IN p_table  VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_ddl    VARCHAR(800)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = p_table
          AND COLUMN_NAME  = p_column
    ) THEN
        SET @ddl_sql = p_ddl;
        PREPARE stmt FROM @ddl_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- 2. t_account（对应 Account 实体）
--    实体字段：id, userId, balance, frozenAmount, createTime, updateTime
CREATE TABLE IF NOT EXISTS t_account (
    id            BIGINT         NOT NULL COMMENT '账户ID',
    user_id       BIGINT         NOT NULL COMMENT '用户ID',
    balance       DECIMAL(12,2)  NOT NULL DEFAULT 0 COMMENT '可用余额',
    frozen_amount DECIMAL(12,2)  NOT NULL DEFAULT 0 COMMENT '冻结金额（担保交易）',
    create_time   DATETIME       DEFAULT NULL COMMENT '创建时间',
    update_time   DATETIME       DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user (user_id)
) ENGINE=InnoDB COMMENT='账户表（C2C担保交易：余额与冻结金额分离）';

CALL sp_add_column('t_account', 'frozen_amount', 'ALTER TABLE t_account ADD COLUMN frozen_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT ''冻结金额（担保交易）''');

DROP PROCEDURE IF EXISTS sp_add_column;

USE product_db;

DROP PROCEDURE IF EXISTS sp_add_column;
DELIMITER $$
CREATE PROCEDURE sp_add_column(
    IN p_table  VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_ddl    VARCHAR(800)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = p_table
          AND COLUMN_NAME  = p_column
    ) THEN
        SET @ddl_sql = p_ddl;
        PREPARE stmt FROM @ddl_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- 3. t_product（对应 Product 实体）
--    实体字段：id, sellerId, name, category, description, story, price,
--              stock, coverImage, images, tags, status, viewCount,
--              likeCount, createTime, updateTime
CREATE TABLE IF NOT EXISTS t_product (
    id          BIGINT         NOT NULL COMMENT '商品ID',
    seller_id   BIGINT         NOT NULL COMMENT '发布者ID（卖家）',
    name        VARCHAR(100)   NOT NULL COMMENT '商品名称',
    category    VARCHAR(50)    DEFAULT NULL COMMENT '商品分类：陶瓷/绘画/香薰/饰品/布艺/木作/其他',
    description TEXT           COMMENT '商品描述',
    story       TEXT           COMMENT '创作故事',
    price       DECIMAL(10,2)  NOT NULL COMMENT '售价',
    stock       INT            NOT NULL DEFAULT 0 COMMENT '库存',
    cover_image VARCHAR(255)   DEFAULT NULL COMMENT '封面图URL',
    images      TEXT           COMMENT '多图URL（JSON数组）',
    tags        VARCHAR(255)   DEFAULT NULL COMMENT '标签（JSON数组）',
    status      TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1-上架中 0-已下架',
    view_count  INT            NOT NULL DEFAULT 0 COMMENT '浏览量',
    like_count  INT            NOT NULL DEFAULT 0 COMMENT '点赞/收藏数',
    create_time DATETIME       DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME       DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_seller (seller_id),
    KEY idx_status_category (status, category)
) ENGINE=InnoDB COMMENT='商品表（C2C：卖家自己发布，下架=软删除）';

-- 说明：seller_id 给 DEFAULT NULL 是为了兼容存量数据（加 NOT NULL 无默认值列，表非空会失败），
--       上线前建议回填存量商品归属后再收紧为 NOT NULL
CALL sp_add_column('t_product', 'seller_id',   'ALTER TABLE t_product ADD COLUMN seller_id   BIGINT DEFAULT NULL COMMENT ''发布者ID（卖家）''');
CALL sp_add_column('t_product', 'category',    'ALTER TABLE t_product ADD COLUMN category    VARCHAR(50) DEFAULT NULL COMMENT ''商品分类''');
CALL sp_add_column('t_product', 'description', 'ALTER TABLE t_product ADD COLUMN description TEXT COMMENT ''商品描述''');
CALL sp_add_column('t_product', 'story',       'ALTER TABLE t_product ADD COLUMN story       TEXT COMMENT ''创作故事''');
CALL sp_add_column('t_product', 'cover_image', 'ALTER TABLE t_product ADD COLUMN cover_image VARCHAR(255) DEFAULT NULL COMMENT ''封面图URL''');
CALL sp_add_column('t_product', 'images',      'ALTER TABLE t_product ADD COLUMN images      TEXT COMMENT ''多图URL（JSON数组）''');
CALL sp_add_column('t_product', 'tags',        'ALTER TABLE t_product ADD COLUMN tags        VARCHAR(255) DEFAULT NULL COMMENT ''标签（JSON数组）''');
CALL sp_add_column('t_product', 'view_count',  'ALTER TABLE t_product ADD COLUMN view_count  INT NOT NULL DEFAULT 0 COMMENT ''浏览量''');
CALL sp_add_column('t_product', 'like_count',  'ALTER TABLE t_product ADD COLUMN like_count  INT NOT NULL DEFAULT 0 COMMENT ''点赞/收藏数''');
-- status 列如果旧表也没有，取消下面这行注释再执行（默认值 1 表示上架中，存量商品不会丢）
-- CALL sp_add_column('t_product', 'status', 'ALTER TABLE t_product ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT ''状态：1-上架中 0-已下架''');

DROP PROCEDURE IF EXISTS sp_add_column;

USE order_db;

DROP PROCEDURE IF EXISTS sp_add_column;
DELIMITER $$
CREATE PROCEDURE sp_add_column(
    IN p_table  VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_ddl    VARCHAR(800)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = p_table
          AND COLUMN_NAME  = p_column
    ) THEN
        SET @ddl_sql = p_ddl;
        PREPARE stmt FROM @ddl_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CREATE TABLE IF NOT EXISTS t_order (
    id               BIGINT        NOT NULL COMMENT '订单ID',
    order_no         VARCHAR(32)   NOT NULL COMMENT '订单号（幂等/查单用）',
    user_id          BIGINT        NOT NULL COMMENT '买家ID（下单人）',
    seller_id        BIGINT        NOT NULL COMMENT '卖家ID（商品发布者）',
    product_id       BIGINT        DEFAULT NULL COMMENT '商品ID',
    product_name     VARCHAR(100)  DEFAULT NULL COMMENT '商品名称（下单时快照）',
    quantity         INT           DEFAULT NULL COMMENT '购买数量',
    unit_price       DECIMAL(10,2) DEFAULT NULL COMMENT '商品单价（下单时快照）',
    total_amount     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '订单总金额',
    receiver_name    VARCHAR(50)   DEFAULT NULL COMMENT '收货人姓名（下单时快照）',
    receiver_phone   VARCHAR(20)   DEFAULT NULL COMMENT '收货人电话（下单时快照）',
    receiver_address VARCHAR(255)  DEFAULT NULL COMMENT '收货地址（下单时快照）',
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/FROZEN/SHIPPED/RECEIVED/COMPLETED/CANCELLED/REFUNDED',
    create_time      DATETIME      DEFAULT NULL COMMENT '创建时间',
    update_time      DATETIME      DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_buyer (user_id)
) ENGINE=InnoDB COMMENT='订单主表（C2C：一单含一个卖家商品，快照存明细表）';

-- 若 t_order 只有 buyer_id 没有 user_id：改名（语义一致：下单人=买家）
SET @has_uid = (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'user_id');
SET @has_bid = (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'buyer_id');
SET @rename_sql = IF(@has_uid = 0 AND @has_bid = 1,
    'ALTER TABLE t_order CHANGE COLUMN buyer_id user_id BIGINT NOT NULL COMMENT ''买家ID（下单人）''',
    'SELECT 1');
PREPARE stmt FROM @rename_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 补齐 t_order 其余缺失列（已存在会自动跳过）
CALL sp_add_column('t_order', 'order_no',         'ALTER TABLE t_order ADD COLUMN order_no         VARCHAR(32) NOT NULL DEFAULT '''' COMMENT ''订单号（幂等/查单用）''');
CALL sp_add_column('t_order', 'user_id',          'ALTER TABLE t_order ADD COLUMN user_id          BIGINT NOT NULL COMMENT ''买家ID（下单人）''');
CALL sp_add_column('t_order', 'seller_id',        'ALTER TABLE t_order ADD COLUMN seller_id        BIGINT DEFAULT NULL COMMENT ''卖家ID（商品发布者）''');
CALL sp_add_column('t_order', 'product_id',       'ALTER TABLE t_order ADD COLUMN product_id       BIGINT DEFAULT NULL COMMENT ''商品ID''');
CALL sp_add_column('t_order', 'product_name',       'ALTER TABLE t_order ADD COLUMN product_name     VARCHAR(100) DEFAULT NULL COMMENT ''商品名称（下单时快照）''');
CALL sp_add_column('t_order', 'quantity',         'ALTER TABLE t_order ADD COLUMN quantity         INT DEFAULT NULL COMMENT ''购买数量''');
CALL sp_add_column('t_order', 'unit_price',       'ALTER TABLE t_order ADD COLUMN unit_price       DECIMAL(10,2) DEFAULT NULL COMMENT ''商品单价（下单时快照）''');
CALL sp_add_column('t_order', 'total_amount',     'ALTER TABLE t_order ADD COLUMN total_amount     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT ''订单总金额''');
CALL sp_add_column('t_order', 'receiver_name',    'ALTER TABLE t_order ADD COLUMN receiver_name    VARCHAR(50) DEFAULT NULL COMMENT ''收货人姓名''');
CALL sp_add_column('t_order', 'receiver_phone',   'ALTER TABLE t_order ADD COLUMN receiver_phone   VARCHAR(20) DEFAULT NULL COMMENT ''收货人电话''');
CALL sp_add_column('t_order', 'receiver_address', 'ALTER TABLE t_order ADD COLUMN receiver_address VARCHAR(255) DEFAULT NULL COMMENT ''收货地址''');
CALL sp_add_column('t_order', 'refund_reason',    'ALTER TABLE t_order ADD COLUMN refund_reason    VARCHAR(255) DEFAULT NULL COMMENT ''退款原因''');
CALL sp_add_column('t_order', 'status',           'ALTER TABLE t_order ADD COLUMN status           VARCHAR(20) NOT NULL DEFAULT ''PENDING'' COMMENT ''订单状态''');

-- 索引（如旧表缺，取消下面注释执行；重复执行会报 Duplicate key name，可忽略）
-- ALTER TABLE t_order ADD UNIQUE KEY uk_order_no (order_no);
-- ALTER TABLE t_order ADD KEY idx_buyer (user_id);

-- 4.2 t_order_item（对应 OrderItem 实体，旧库没有则自动创建）
CREATE TABLE IF NOT EXISTS t_order_item (
    id           BIGINT         NOT NULL COMMENT '明细ID',
    order_id     BIGINT         NOT NULL COMMENT '所属订单ID',
    product_id   BIGINT         NOT NULL COMMENT '商品ID',
    seller_id    BIGINT         NOT NULL COMMENT '商品所属卖家ID',
    product_name VARCHAR(100)   NOT NULL COMMENT '商品名称（下单时快照）',
    quantity     INT            NOT NULL COMMENT '购买数量',
    unit_price   DECIMAL(10,2)  NOT NULL COMMENT '商品单价（下单时快照）',
    total_amount DECIMAL(12,2)  NOT NULL COMMENT '小计 = 单价 × 数量',
    create_time  DATETIME       DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_order (order_id)
) ENGINE=InnoDB COMMENT='订单明细表（商品快照存这里）';

-- 4.3 t_cart（对应 Cart 实体，旧库没有则自动创建）
CREATE TABLE IF NOT EXISTS t_cart (
    id          BIGINT      NOT NULL COMMENT '购物车项ID',
    user_id     BIGINT      NOT NULL COMMENT '买家ID',
    product_id  BIGINT      NOT NULL COMMENT '商品ID',
    quantity    INT         NOT NULL DEFAULT 1 COMMENT '数量',
    create_time DATETIME    DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME    DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_product (user_id, product_id)
) ENGINE=InnoDB COMMENT='购物车表（同一买家同一商品唯一）';

DROP PROCEDURE IF EXISTS sp_add_column;

