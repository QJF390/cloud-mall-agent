package com.exdemo.chat.constant;

/**
 * 聊天模块常量。
 *
 * <p>常量集中放一处，禁止在业务代码里写魔法字符串 —— 一旦 Topic 名拼错，
 * 生产上表现为「消息发出去石沉大海」，排查成本极高。</p>
 */
public interface ChatConstant {

    // ==================== RocketMQ Topic ====================

    /**
     * 上行 Topic：WebSocket 接入层 → 业务处理（削峰 + 异步落库）。
     * 这是抗住「大量用户同时发消息」的关键：瞬时流量先进 Broker 排队，
     * 消费者按固定并发慢慢消化，数据库不会被冲垮。
     */
    String TOPIC_CHAT_MSG_IN = "CHAT_MSG_IN_TOPIC";

    String TOPIC_CHAT_PUSH = "CHAT_PUSH_TOPIC";

    /** 上行消息 Tag */
    String TAG_CHAT_SEND = "SEND";

    /** 下行推送 Tag */
    String TAG_CHAT_PUSH = "PUSH";

    // ==================== 消费者组 ====================

    /** 落库消费者组（集群消费：同一条消息只被一个实例处理，天然负载均衡） */
    String GROUP_CHAT_PERSIST = "chat-persist-group";

    /** 推送消费者组（广播消费：每个实例都收全量，各自命中本地连接） */
    String GROUP_CHAT_PUSH = "chat-push-group";

    // ==================== Redis Key ====================

    /** 消息幂等去重：{msgId}，5 分钟过期（覆盖 MQ 重试周期即可） */
    String REDIS_KEY_MSG_DEDUP = "chat:dedup:";

    /** 单连接限流：{userId}，滑动窗口 */
    String REDIS_KEY_RATE_LIMIT = "chat:ratelimit:";

    /** 在线状态：{userId} -> 实例ID（便于排查问题，也用于「对方在线吗」提示） */
    String REDIS_KEY_ONLINE = "chat:online:";

    /** 会话 ID 分隔符 */
    String SESSION_ID_SEP = "_";

    // ==================== 限制阈值 ====================

    /** 单条消息最大长度（字符） */
    int MAX_CONTENT_LENGTH = 2000;

    /** 单用户每秒最多发多少条消息（防刷 / 防死循环打满带宽） */
    int RATE_LIMIT_PER_SECOND = 20;

    /** WebSocket 单次文本帧最大缓冲（字节），防 OOM */
    int MAX_TEXT_BUFFER_SIZE = 32 * 1024;
}
