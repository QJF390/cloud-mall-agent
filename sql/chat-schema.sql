
CREATE DATABASE IF NOT EXISTS chat_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE chat_db;

DROP TABLE IF EXISTS t_chat_session;
CREATE TABLE t_chat_session (
    id                BIGINT       NOT NULL COMMENT '主键（雪花ID）',
    session_id        VARCHAR(64)  NOT NULL COMMENT '会话ID：{minUid}_{maxUid} 或 {minUid}_{maxUid}_{productId}',
    buyer_id          BIGINT       NOT NULL COMMENT '买家（发起方）ID',
    seller_id         BIGINT       NOT NULL COMMENT '卖家（商品发布者）ID',
    product_id        BIGINT       DEFAULT NULL COMMENT '关联商品ID（从商品详情点进来咨询时带）',
    product_name      VARCHAR(100) DEFAULT NULL COMMENT '商品名快照（商品下架/改名后仍能显示）',
    last_message      VARCHAR(500) DEFAULT NULL COMMENT '最后一条消息摘要（会话列表展示）',
    last_message_time DATETIME     DEFAULT NULL COMMENT '最后一条消息时间（会话列表排序）',
    buyer_unread      INT          NOT NULL DEFAULT 0 COMMENT '买家未读数',
    seller_unread     INT          NOT NULL DEFAULT 0 COMMENT '卖家未读数',
    status            TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-已删除（软删）',
    create_time       DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time       DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_id (session_id),
    KEY idx_buyer (buyer_id, status, last_message_time),
    KEY idx_seller (seller_id, status, last_message_time)
) ENGINE=InnoDB COMMENT='聊天会话表（买家-卖家一对一，可挂商品）';

DROP TABLE IF EXISTS t_chat_message;
CREATE TABLE t_chat_message (
    id          BIGINT        NOT NULL COMMENT '消息ID（雪花ID，严格递增，作为分页游标）',
    msg_id      VARCHAR(64)   NOT NULL COMMENT '客户端消息ID（幂等去重键）',
    session_id  VARCHAR(64)   NOT NULL COMMENT '所属会话ID',
    sender_id   BIGINT        NOT NULL COMMENT '发送者ID',
    receiver_id BIGINT        NOT NULL COMMENT '接收者ID（冗余字段，避免回表查会话）',
    msg_type    TINYINT       NOT NULL DEFAULT 1 COMMENT '消息类型：1-文本 2-图片 3-商品卡片 4-系统通知',
    content     VARCHAR(2000) DEFAULT NULL COMMENT '消息正文（文本消息内容）',
    extra       VARCHAR(1000) DEFAULT NULL COMMENT '扩展JSON（图片URL / 商品卡片快照等）',
    status      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1-已送达 2-已读（预留，便于做已读回执）',
    create_time DATETIME      DEFAULT NULL COMMENT '发送时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_msg_id (msg_id),
    KEY idx_session_id_id (session_id, id)
) ENGINE=InnoDB COMMENT='聊天消息表（msg_id 唯一键保证幂等）';

DROP TABLE IF EXISTS t_chat_read_cursor;
CREATE TABLE t_chat_read_cursor (
    id             BIGINT   NOT NULL COMMENT '主键（雪花ID）',
    session_id     VARCHAR(64) NOT NULL COMMENT '会话ID',
    user_id        BIGINT   NOT NULL COMMENT '用户ID',
    last_read_msg_id BIGINT NOT NULL DEFAULT 0 COMMENT '已读到的最大消息ID',
    update_time    DATETIME DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_user (session_id, user_id)
) ENGINE=InnoDB COMMENT='会话已读水位线（离线消息增量补发 / 未读清零依据）';

