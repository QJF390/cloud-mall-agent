package com.exdemo.chat.websocket;

import com.exdemo.chat.constant.ChatConstant;
import com.exdemo.chat.dto.ChatMessagePayload;
import com.exdemo.chat.enums.ChatErrorCode;
import com.exdemo.chat.enums.MessageTypeEnum;
import com.exdemo.chat.service.ChatProducer;
import com.exdemo.chat.support.RedisRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Duration;
import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final ChatProducer chatProducer;
    private final RedisRateLimiter rateLimiter;
    private final LocalSessionManager sessionManager;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${chat.rate-limit.permits-per-second}")
    private int permitsPerSecond;

    @Value("${chat.rate-limit.capacity}")
    private int capacity;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get(AuthHandshakeInterceptor.ATTR_USER_ID);
        if (userId == null) {
            // 理论上不可能：拦截器已经挡过了。这里兜底是为了防御「拦截器被误删」的改动
            closeQuietly(session);
            return;
        }
        sessionManager.register(userId, session);
        // 在线状态写 Redis：用于「对方在线吗」的展示，以及排障时定位用户在哪台机器
        stringRedisTemplate.opsForValue().set(
                ChatConstant.REDIS_KEY_ONLINE + userId,
                currentInstanceId(session),
                // TTL 留得比心跳间隔长：即使进程被 kill -9 来不及清理，状态也会自动过期
                Duration.ofMillis(60_000));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get(AuthHandshakeInterceptor.ATTR_USER_ID);
        if (userId == null) {
            return;
        }

        ChatMessagePayload payload;
        try {
            payload = objectMapper.readValue(message.getPayload(), ChatMessagePayload.class);
        } catch (Exception e) {
            send(session, ChatMessagePayload.error(null, ChatErrorCode.INVALID_PARAM, "消息格式非法"));
            return;
        }

        // ---------- 心跳 ----------
        if ("ping".equalsIgnoreCase(payload.getType())) {
            send(session, ChatMessagePayload.pong());
            // 心跳顺带续期在线状态（否则 60s 后被判定离线）
            stringRedisTemplate.expire(ChatConstant.REDIS_KEY_ONLINE + userId, Duration.ofMillis(60_000));
            return;
        }

        // ---------- 聊天消息 ----------
        if (!"chat".equalsIgnoreCase(payload.getType())) {
            send(session, ChatMessagePayload.error(payload.getMsgId(), ChatErrorCode.INVALID_PARAM, "不支持的消息类型：" + payload.getType()));
            return;
        }

        // ① 参数校验：先挡掉明显的脏数据，别让它们占用 MQ 和 DB
        String reject = validate(payload);
        if (reject != null) {
            send(session, ChatMessagePayload.error(payload.getMsgId(), ChatErrorCode.INVALID_PARAM, reject));
            return;
        }

        // ② 会话越权校验：sessionId 必须由「两个用户ID」拼成且包含发送者，防止伪造他人会话
        if (!isMemberOfSession(payload.getSessionId(), userId)) {
            log.warn("越权发送被拦截 userId={}, sessionId={}", userId, payload.getSessionId());
            send(session, ChatMessagePayload.error(payload.getMsgId(), ChatErrorCode.FORBIDDEN));
            return;
        }

        // ③ 限流：单用户维度，挡住刷子与死循环客户端
        if (!rateLimiter.tryAcquire(ChatConstant.REDIS_KEY_RATE_LIMIT + userId, permitsPerSecond, capacity)) {
            log.warn("触发发送限流 userId={}", userId);
            send(session, ChatMessagePayload.error(payload.getMsgId(), ChatErrorCode.RATE_LIMITED));
            return;
        }

        // ④ 服务端权威字段覆盖：发送者只认握手时的身份，内容类型非法则降级为文本
        payload.setSenderId(userId);
        payload.setCreateTime(System.currentTimeMillis());
        payload.setMsgType(MessageTypeEnum.of(payload.getMsgType()).getCode());

        // ⑤ 投递 MQ（顺序发送，保证同会话有序）
        try {
            chatProducer.sendIn(payload);
        } catch (Exception e) {
            // MQ 挂了：明确告诉客户端「没发成功」，让前端显示红点重试。
            // 最忌讳的是默默吞掉 —— 用户以为发出去了，对方一直没收到，投诉就来了。
            log.error("投递 MQ 失败 userId={}, sessionId={}", userId, payload.getSessionId(), e);
            send(session, ChatMessagePayload.error(payload.getMsgId(), ChatErrorCode.MQ_UNAVAILABLE));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get(AuthHandshakeInterceptor.ATTR_USER_ID);
        if (userId == null) {
            return;
        }
        // unregister 返回 false 表示「该用户在本机已无任何连接」→ 可以标记离线
        boolean stillOnline = sessionManager.unregister(userId, session);
        if (!stillOnline) {
            stringRedisTemplate.delete(ChatConstant.REDIS_KEY_ONLINE + userId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WS 传输异常 sessionId={}", session.getId(), exception);
    }

    // ==================== 私有方法 ====================

    private String validate(ChatMessagePayload p) {
        if (p.getMsgId() == null || p.getMsgId().isBlank() || p.getMsgId().length() > 64) {
            return "msgId 缺失或过长";
        }
        if (p.getSessionId() == null || p.getSessionId().isBlank()) {
            return "sessionId 不能为空";
        }
        if (p.getReceiverId() == null || p.getReceiverId() <= 0) {
            return "receiverId 非法";
        }
        if (p.getContent() == null || p.getContent().isBlank()) {
            return "消息内容不能为空";
        }
        if (p.getContent().length() > ChatConstant.MAX_CONTENT_LENGTH) {
            return "消息内容超过 " + ChatConstant.MAX_CONTENT_LENGTH + " 字";
        }
        return null;
    }

    /**
     * 校验发送者确实属于该会话（sessionId = minUid_maxUid）。
     *
     * <p>这是<b>同步</b>的第一道防线，成本为零；
     * 真正的最终防线在落库消费者里查库确认（防「用户 A 和用户 B 的会话被 C 猜到 ID 后乱发」）。</p>
     */
    private boolean isMemberOfSession(String sessionId, Long userId) {
        if (sessionId == null) {
            return false;
        }
        return Arrays.stream(sessionId.split(ChatConstant.SESSION_ID_SEP))
                .anyMatch(part -> part.equals(String.valueOf(userId)));
    }

    private void send(WebSocketSession session, ChatMessagePayload payload) {
        synchronized (session) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
                }
            } catch (Exception e) {
                log.warn("WS 回写失败 sessionId={}", session.getId(), e);
            }
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.NOT_ACCEPTABLE);
        } catch (Exception ignored) {
            // 关闭失败无需处理
        }
    }

    private String currentInstanceId(WebSocketSession session) {
        // 简化实现：本地端口 + session 标识。生产建议用 hostname:port 或注册中心实例ID
        String local = session.getLocalAddress() == null ? "unknown" : String.valueOf(session.getLocalAddress().getPort());
        return local;
    }

    @Override
    public boolean supportsPartialMessages() {
        // 关闭分片消息：IM 单条消息很小，分片只会带来复杂度和内存风险
        return false;
    }
}
