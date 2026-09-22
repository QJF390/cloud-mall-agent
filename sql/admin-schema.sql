
CREATE DATABASE IF NOT EXISTS admin_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE admin_db;

CREATE TABLE IF NOT EXISTS t_admin_user (
    id              BIGINT       NOT NULL COMMENT '管理员ID（雪花ID，应用侧 ASSIGN_ID）',
    username        VARCHAR(50)  NOT NULL COMMENT '登录名，唯一',
    password        VARCHAR(100) NOT NULL COMMENT '密码（BCrypt 哈希，禁止明文/可逆加密）',
    nickname        VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    role_id         BIGINT       DEFAULT NULL COMMENT '角色ID（t_sys_role.id）',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
    last_login_time DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_username (username),
    KEY idx_admin_role (role_id)
) ENGINE=InnoDB COMMENT='后台管理员账号';

CREATE TABLE IF NOT EXISTS t_sys_role (
    id          BIGINT      NOT NULL COMMENT '角色ID',
    role_code   VARCHAR(50) NOT NULL COMMENT '角色编码，如 SUPER_ADMIN / OPERATOR',
    role_name   VARCHAR(50) NOT NULL COMMENT '角色名称',
    permissions TEXT        COMMENT '权限点（JSON数组，如 ["admin:user:list","admin:product:edit"]）',
    create_time DATETIME    DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME    DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB COMMENT='后台角色（RBAC）';

CREATE TABLE IF NOT EXISTS t_sys_log (
    id          BIGINT       NOT NULL COMMENT '日志ID',
    operator    VARCHAR(50)  DEFAULT NULL COMMENT '操作人（管理员登录名）',
    module      VARCHAR(20)  DEFAULT NULL COMMENT '模块：USER/PRODUCT/ORDER/SYSTEM',
    action      VARCHAR(100) DEFAULT NULL COMMENT '操作描述',
    request_uri VARCHAR(255) DEFAULT NULL COMMENT '请求方法 + 路径',
    success     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功：1-成功 0-失败',
    cost_ms     BIGINT       DEFAULT NULL COMMENT '耗时（毫秒）',
    error_msg   VARCHAR(255) DEFAULT NULL COMMENT '错误信息（失败时）',
    create_time DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_log_operator (operator),
    KEY idx_log_create_time (create_time)
) ENGINE=InnoDB COMMENT='后台操作审计日志';

CREATE TABLE IF NOT EXISTS t_sys_config (
    id           BIGINT        NOT NULL COMMENT '配置ID',
    config_key   VARCHAR(100)  NOT NULL COMMENT '配置键，唯一',
    config_value VARCHAR(1000) DEFAULT NULL COMMENT '配置值',
    config_group VARCHAR(20)   DEFAULT NULL COMMENT '分组：ORDER/USER/PRODUCT/SYSTEM',
    remark       VARCHAR(255)  DEFAULT NULL COMMENT '备注说明',
    create_time  DATETIME      DEFAULT NULL COMMENT '创建时间',
    update_time  DATETIME      DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB COMMENT='系统参数配置';

INSERT IGNORE INTO t_sys_role (id, role_code, role_name, permissions, create_time, update_time) VALUES
(1, 'SUPER_ADMIN', '超级管理员',
 '["admin:user:list","admin:user:edit","admin:product:list","admin:product:edit","admin:order:list","admin:order:edit","admin:system:admin","admin:system:role","admin:system:log","admin:system:config"]',
 NOW(), NOW()),
(2, 'OPERATOR', '运营',
 '["admin:user:list","admin:product:list","admin:product:edit","admin:order:list","admin:order:edit"]',
 NOW(), NOW());

INSERT IGNORE INTO t_sys_config (id, config_key, config_value, config_group, remark, create_time, update_time) VALUES
(1, 'order.timeout.minutes',        '30',                    'ORDER',   '待付款订单自动关单时长（分钟）', NOW(), NOW()),
(2, 'order.auto.receive.days',      '7',                     'ORDER',   '发货后自动确认收货天数', NOW(), NOW()),
(3, 'user.credit.init',             '100',                   'USER',    '新用户注册初始信用分', NOW(), NOW()),
(4, 'product.max.stock',            '999999',                'PRODUCT', '单品库存上限', NOW(), NOW()),
(5, 'system.maintenance.notice',    '欢迎使用巧见后台',        'SYSTEM',  '后台首页公告', NOW(), NOW()),
(6, 'system.login.max.fail',        '5',                     'SYSTEM',  '管理员登录失败锁定阈值', NOW(), NOW());

