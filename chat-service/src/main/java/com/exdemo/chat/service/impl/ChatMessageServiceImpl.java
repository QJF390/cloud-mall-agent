package com.exdemo.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exdemo.chat.constant.ChatConstant;
import com.exdemo.chat.dto.ChatMessagePayload;
import com.exdemo.chat.entity.ChatMessage;
import com.exdemo.chat.entity.ChatSession;
import com.exdemo.chat.mapper.ChatMessageMapper;
import com.exdemo.chat.mapper.ChatSessionMapper;
import com.exdemo.chat.service.ChatMessageService;
import com.exdemo.chat.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * 消息业务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatSessionService chatSessionService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 落库：这是整条链路里唯一碰数据库的地方。
     *
     * <p><b>三重幂等保障：</b></p>
     * <ol>
     *   <li>Redis SETNX（5 分钟）—— 拦住 99% 的重复：网络重传、用户狂点、MQ 重试；</li>
     *   <li>DB 唯一键 uk_msg_id —— Redis 宕机/过期后的最后防线；</li>
     *   <li>命中重复时直接返回 null，不推送、不报错（重复不是错误，是必须容忍的常态）。</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessage persist(ChatMessagePayload payload) {
        // ① Redis 幂等
        String dedupKey = ChatConstant.REDIS_KEY_MSG_DEDUP + payload.getMsgId();
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(dedupKey, "1", Duration.ofMinutes(5));
        if (!Boolean.TRUE.equals(first)) {
            log.info("重复消息丢弃 msgId={}", payload.getMsgId());
            return null;
        }

        // ② 会话校验（最终防线）：确认发送者确实是会话成员，防伪造 sessionId 乱发
        ChatSession session = chatSessionService.getBySessionId(payload.getSessionId());
        if (session == null) {
            // 会话不存在：可能前端跳过了「创建会话」直接发。兜底建一个，保证消息不丢。
            // （生产更严格的做法是拒绝，这里选择可用性优先）
            session = chatSessionService.getOrCreate(payload.getSenderId(), payload.getReceiverId(), null, null);
        }
        if (!Objects.equals(session.getBuyerId(), payload.getSenderId())
                && !Objects.equals(session.getSellerId(), payload.getSenderId())) {
            log.warn("非会话成员的消息被丢弃 sessionId={}, senderId={}", payload.getSessionId(), payload.getSenderId());
            return null;
        }

        // ③ 落库（唯一键兜底幂等）
        LocalDateTime createTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(payload.getCreateTime() == null
                        ? System.currentTimeMillis() : payload.getCreateTime()),
                ZoneId.systemDefault());

        ChatMessage message = new ChatMessage();
        message.setMsgId(payload.getMsgId());
        message.setSessionId(payload.getSessionId());
        message.setSenderId(payload.getSenderId());
        message.setReceiverId(payload.getReceiverId());
        message.setMsgType(payload.getMsgType());
        message.setContent(payload.getContent());
        message.setExtra(payload.getExtra());
        message.setStatus(1);
        message.setCreateTime(createTime);
        try {
            chatMessageMapper.insert(message);
        } catch (DuplicateKeyException e) {
            log.info("消息重复落库，已忽略 msgId={}", payload.getMsgId());
            return null;
        }

        // ④ 更新会话摘要与未读数（未读加给「接收者」那一侧）
        LambdaUpdateWrapper<ChatSession> uw = Wrappers.lambdaUpdate();
        uw.eq(ChatSession::getSessionId, payload.getSessionId());
        uw.set(ChatSession::getLastMessage, abbreviate(payload.getContent(), 200));
        uw.set(ChatSession::getLastMessageTime, createTime);
        uw.set(ChatSession::getUpdateTime, LocalDateTime.now());
        // 用 setSql 做自增，避免「读-改-写」的并发覆盖（两个消费者同时 +1 只生效一次）
        if (Objects.equals(session.getBuyerId(), payload.getReceiverId())) {
            uw.setSql("buyer_unread = buyer_unread + 1");
        } else if (Objects.equals(session.getSellerId(), payload.getReceiverId())) {
            uw.setSql("seller_unread = seller_unread + 1");
        }
        chatSessionMapper.update(null, uw);

        return message;
    }

    /** 截断摘要（不引三方工具库，减少依赖） */
    private String abbreviate(String text, int max) {
        if (text == null || text.isBlank()) {
            return "[图片]";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    /**
     * 游标分页。
     *
     * <p>用 {@code id < cursor ORDER BY id DESC LIMIT n} 而不是 {@code LIMIT offset, n}：
     * offset 越大数据库扫描越多行，翻到第 1 万页时要扫 20 万行，
     * 在长连接服务里这种慢 SQL 会直接拖垮连接池。游标分页永远是常数级开销。</p>
     */
    @Override
    public List<ChatMessage> history(String sessionId, Long cursor, int size) {
        int limit = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<ChatMessage> qw = Wrappers.lambdaQuery();
        qw.eq(ChatMessage::getSessionId, sessionId);
        if (cursor != null && cursor > 0) {
            qw.lt(ChatMessage::getId, cursor);
        }
        qw.orderByDesc(ChatMessage::getId).last("LIMIT " + limit);
        return chatMessageMapper.selectList(qw);
    }
}
