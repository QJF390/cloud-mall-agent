
CREATE DATABASE IF NOT EXISTS exdemo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE exdemo;

DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
    id          BIGINT        NOT NULL COMMENT '用户ID（雪花ID）',
    username    VARCHAR(50)   NOT NULL COMMENT '用户名',
    password    VARCHAR(100)  NOT NULL COMMENT '密码（BCrypt 加密）',
    phone       VARCHAR(20)   DEFAULT NULL COMMENT '手机号',
    email       VARCHAR(100)  DEFAULT NULL COMMENT '邮箱',
    avatar      VARCHAR(255)  DEFAULT NULL COMMENT '头像图片URL',
    bio         VARCHAR(500)  DEFAULT NULL COMMENT '个人简介（展示在个人主页/作品集）',
    credit_score INT          DEFAULT 100 COMMENT '信用分（C2C卖家信誉：交易完成加分，纠纷/差评扣分）',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-已禁用（后台封禁）',
    create_time DATETIME      DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME      DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB COMMENT='用户表（C2C：人人可买可卖，个人主页=作品集）';

DROP TABLE IF EXISTS t_product;
CREATE TABLE t_product (
    id          BIGINT         NOT NULL COMMENT '商品ID',
    seller_id   BIGINT         NOT NULL COMMENT '发布者ID（C2C核心：商品属于哪个用户）',
    name        VARCHAR(100)   NOT NULL COMMENT '商品名称',
    category    VARCHAR(50)    DEFAULT NULL COMMENT '商品分类：陶瓷/绘画/香薰/饰品/布艺/木作/其他',
    description TEXT           COMMENT '商品描述',
    story       TEXT           COMMENT '创作故事（文艺商品的灵魂，展示在详情页）',
    price       DECIMAL(10,2)  NOT NULL COMMENT '售价',
    stock       INT            NOT NULL DEFAULT 0 COMMENT '库存（手作孤品/限量款为1）',
    cover_image VARCHAR(255)   DEFAULT NULL COMMENT '封面图URL',
    images      TEXT           COMMENT '多图URL（JSON数组，如 ["/products/a.png","/products/b.png"]）',
    tags        VARCHAR(255)   DEFAULT NULL COMMENT '标签（JSON数组，如 ["手作","限量"]，预留AI推荐用）',
    status      TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1-上架中 0-已下架（软删除，可重新上架）',
    view_count  INT            NOT NULL DEFAULT 0 COMMENT '浏览量',
    like_count  INT            NOT NULL DEFAULT 0 COMMENT '点赞/收藏数',
    create_time DATETIME       DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME       DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_seller (seller_id),
    KEY idx_status_category (status, category)
) ENGINE=InnoDB COMMENT='商品表（C2C：卖家自己发布，下架=软删除）';

DROP TABLE IF EXISTS t_account;
CREATE TABLE t_account (
    id            BIGINT         NOT NULL COMMENT '账户ID',
    user_id       BIGINT         NOT NULL COMMENT '用户ID',
    balance       DECIMAL(12,2)  NOT NULL DEFAULT 0 COMMENT '可用余额',
    frozen_amount DECIMAL(12,2)  NOT NULL DEFAULT 0 COMMENT '冻结金额（担保交易：买家付款后冻结，确认收货后打款给卖家）',
    create_time   DATETIME       DEFAULT NULL COMMENT '创建时间',
    update_time   DATETIME       DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user (user_id)
) ENGINE=InnoDB COMMENT='账户表（C2C担保交易：余额与冻结金额分离）';

DROP TABLE IF EXISTS t_order;
CREATE TABLE t_order (
    id               BIGINT        NOT NULL COMMENT '订单ID',
    order_no         VARCHAR(32)   NOT NULL COMMENT '订单号（幂等/查单用）',
    buyer_id         BIGINT        NOT NULL COMMENT '买家ID',
    seller_id        BIGINT        NOT NULL COMMENT '卖家ID（C2C：商品发布者）',
    total_amount     DECIMAL(12,2) NOT NULL COMMENT '订单总金额',
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING待付款/FROZEN已付款(资金冻结)/SHIPPED已发货/RECEIVED已收货/COMPLETED已完成/CANCELLED已取消/REFUNDED已退款',
    receiver_name    VARCHAR(50)   DEFAULT NULL COMMENT '收货人姓名（下单时快照）',
    receiver_phone   VARCHAR(20)   DEFAULT NULL COMMENT '收货人电话（下单时快照）',
    receiver_address VARCHAR(255)  DEFAULT NULL COMMENT '收货地址（下单时快照）',
    refund_reason    VARCHAR(255)  DEFAULT NULL COMMENT '退款原因（status=REFUNDED 时记录）',
    create_time      DATETIME      DEFAULT NULL COMMENT '创建时间',
    update_time      DATETIME      DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_buyer (buyer_id),
    KEY idx_seller (seller_id)
) ENGINE=InnoDB COMMENT='订单主表（C2C：一单可能含多个卖家的商品，按卖家拆单）';

DROP TABLE IF EXISTS t_order_item;
CREATE TABLE t_order_item (
    id           BIGINT         NOT NULL COMMENT '明细ID',
    order_id     BIGINT         NOT NULL COMMENT '订单ID',
    product_id   BIGINT         NOT NULL COMMENT '商品ID',
    seller_id    BIGINT         NOT NULL COMMENT '商品所属卖家ID',
    product_name VARCHAR(100)   NOT NULL COMMENT '商品名称（下单时快照）',
    quantity     INT            NOT NULL COMMENT '购买数量',
    unit_price   DECIMAL(10,2)  NOT NULL COMMENT '商品单价（下单时快照）',
    total_amount DECIMAL(12,2)  NOT NULL COMMENT '小计 = 单价 × 数量',
    create_time  DATETIME       DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_order (order_id)
) ENGINE=InnoDB COMMENT='订单明细表（商品快照存这里，原 t_order 的 product 字段移除）';

DROP TABLE IF EXISTS t_transaction;
CREATE TABLE t_transaction (
    id            BIGINT        NOT NULL COMMENT '流水ID',
    order_no      VARCHAR(32)   DEFAULT NULL COMMENT '关联订单号',
    user_id       BIGINT        NOT NULL COMMENT '资金所属用户',
    type          VARCHAR(20)   NOT NULL COMMENT '类型：RECHARGE充值/PAY付款/FREEZE冻结/UNFREEZE解冻/SETTLE打款给卖家/REFUND退款/WITHDRAW提现',
    amount        DECIMAL(12,2) NOT NULL COMMENT '变动金额（正数进账/负数出账）',
    balance_after DECIMAL(12,2) DEFAULT NULL COMMENT '变动后余额（对账用）',
    remark        VARCHAR(255)  DEFAULT NULL COMMENT '备注',
    create_time   DATETIME      DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_order (order_no)
) ENGINE=InnoDB COMMENT='资金流水表（担保交易每一笔资金的来龙去脉）';

DROP TABLE IF EXISTS t_cart;
CREATE TABLE t_cart (
    id          BIGINT      NOT NULL COMMENT '购物车项ID',
    user_id     BIGINT      NOT NULL COMMENT '买家ID',
    product_id  BIGINT      NOT NULL COMMENT '商品ID',
    quantity    INT         NOT NULL DEFAULT 1 COMMENT '数量',
    create_time DATETIME    DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME    DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_product (user_id, product_id)
) ENGINE=InnoDB COMMENT='购物车表（同一买家同一商品唯一）';

DROP TABLE IF EXISTS t_favorite;
CREATE TABLE t_favorite (
    id          BIGINT   NOT NULL COMMENT '收藏记录ID',
    user_id     BIGINT   NOT NULL COMMENT '收藏者ID',
    product_id  BIGINT   NOT NULL COMMENT '被收藏商品ID',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_product (user_id, product_id)
) ENGINE=InnoDB COMMENT='收藏表（也是后期AI推荐的素材）';

DROP TABLE IF EXISTS t_address;
CREATE TABLE t_address (
    id             BIGINT       NOT NULL COMMENT '地址ID',
    user_id        BIGINT       NOT NULL COMMENT '所属用户ID',
    receiver_name  VARCHAR(50)  NOT NULL COMMENT '收货人姓名',
    receiver_phone VARCHAR(20)  NOT NULL COMMENT '收货人电话',
    province       VARCHAR(50)  DEFAULT NULL COMMENT '省',
    city           VARCHAR(50)  DEFAULT NULL COMMENT '市',
    district       VARCHAR(50)  DEFAULT NULL COMMENT '区/县',
    detail         VARCHAR(255) NOT NULL COMMENT '详细地址',
    is_default     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认：1-是 0-否',
    create_time    DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time    DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='收货地址表（下单时快照到订单）';

DROP TABLE IF EXISTS t_gallery;
CREATE TABLE t_gallery (
    id          BIGINT        NOT NULL COMMENT '作品/动态ID',
    user_id     BIGINT        NOT NULL COMMENT '作者ID（创作者）',
    title       VARCHAR(100)  DEFAULT NULL COMMENT '标题',
    content     TEXT          COMMENT '图文内容（创作灵感/过程分享）',
    images      TEXT          COMMENT '图片URL（JSON数组）',
    product_id  BIGINT        DEFAULT NULL COMMENT '关联商品ID（作品可挂购买链接）',
    create_time DATETIME      DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME      DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='作品展示/动态表（引流+建立信任，AI内容生成场景）';

DROP TABLE IF EXISTS t_follow;
CREATE TABLE t_follow (
    id          BIGINT   NOT NULL COMMENT '关注记录ID',
    follower_id BIGINT   NOT NULL COMMENT '关注者ID',
    followee_id BIGINT   NOT NULL COMMENT '被关注者ID（创作者）',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_follow (follower_id, followee_id)
) ENGINE=InnoDB COMMENT='关注表（创作者粉丝体系）';

DROP TABLE IF EXISTS t_comment;
CREATE TABLE t_comment (
    id             BIGINT       NOT NULL COMMENT '评价ID',
    order_id       BIGINT       DEFAULT NULL COMMENT '关联订单ID',
    user_id        BIGINT       NOT NULL COMMENT '评价人ID（买家）',
    target_user_id BIGINT       NOT NULL COMMENT '被评价人ID（卖家）',
    product_id     BIGINT       DEFAULT NULL COMMENT '关联商品ID',
    rating         TINYINT      DEFAULT 5 COMMENT '评分 1-5',
    content        VARCHAR(500) DEFAULT NULL COMMENT '评价内容',
    create_time    DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_target (target_user_id)
) ENGINE=InnoDB COMMENT='评价表（卖家信用分来源，C2C信任体系核心）';

DROP TABLE IF EXISTS t_customize;
CREATE TABLE t_customize (
    id               BIGINT       NOT NULL COMMENT '定制需求ID',
    user_id          BIGINT       NOT NULL COMMENT '发起定制的买家ID',
    creator_id       BIGINT       DEFAULT NULL COMMENT '承接定制的创作者ID',
    title            VARCHAR(100) DEFAULT NULL COMMENT '需求标题',
    description      TEXT         COMMENT '需求描述（富文本，预留AI需求分析）',
    reference_images TEXT         COMMENT '参考图URL（JSON数组）',
    budget           DECIMAL(10,2) DEFAULT NULL COMMENT '预算金额',
    deadline         DATE         DEFAULT NULL COMMENT '期望完成日期',
    status           VARCHAR(20)  DEFAULT 'PENDING' COMMENT '状态：PENDING待接单/QUOTED已报价/CONFIRMED已确认/MAKING制作中/DONE已完成/CANCELLED已取消',
    create_time      DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time      DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='定制需求表（差异化卖点，AI分析场景）';
