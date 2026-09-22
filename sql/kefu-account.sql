
USE user_db;

INSERT INTO t_user
    (id, username, password, phone, email, avatar, bio, credit_score, status, create_time, update_time)
VALUES
    (10000,
     'kefu',
     '$2a$10$AECaCryo.BjTk0omnJ4nR.dOiKG25MPY1OnkqnClDX0mDOJkf8upu',
     NULL,
     'kefu@exdemo.com',
     NULL,
     '平台官方客服',
     100,
     1,
     NOW(),
     NOW())
ON DUPLICATE KEY UPDATE
    username     = VALUES(username),
    bio          = VALUES(bio),
    status       = 1,
    update_time  = NOW();
