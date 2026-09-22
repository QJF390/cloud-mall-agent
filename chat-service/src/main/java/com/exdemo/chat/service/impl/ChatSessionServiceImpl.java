package com.exdemo.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exdemo.chat.config.KefuProperties;
import com.exdemo.chat.constant.ChatConstant;
import com.exdemo.chat.entity.ChatSession;
import com.exdemo.chat.mapper.ChatSessionMapper;
import com.exdemo.chat.service.ChatSessionService;
import com.exdemo.chat.vo.SessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 会话业务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final KefuProperties kefuProperties;

    @Override
    public ChatSession getOrCreate(Long buyerId, Long sellerId, Long productId, String productName) {
        if (Objects.equals(buyerId, sellerId)) {
            throw new IllegalArgumentException("不能和自己聊天");
        }
        String sessionId = buildSessionId(buyerId, sellerId);
        ChatSession exist = getBySessionId(sessionId);
        if (exist != null) {
            return exist;
        }

        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setBuyerId(buyerId);
        session.setSellerId(sellerId);
        session.setProductId(productId);
        session.setProductName(productName);
        session.setBuyerUnread(0);
        session.setSellerUnread(0);
        session.setStatus(1);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        try {
            chatSessionMapper.insert(session);
            return session;
        } catch (DuplicateKeyException e) {
            // 并发创建：两个请求同时进来都查不到，都去 insert，必然有一个撞唯一键。
            // 这是正常的并发现象，不是错误 —— 直接回查即可（这就是「先查后插」的标准配套处理）。
            log.info("会话并发创建命中唯一键，回查 sessionId={}", sessionId);
            return getBySessionId(sessionId);
        }
    }

    @Override
    public ChatSession getBySessionId(String sessionId) {
        return chatSessionMapper.selectOne(
                Wrappers.<ChatSession>lambdaQuery().eq(ChatSession::getSessionId, sessionId));
    }

    @Override
    public List<SessionVO> listMySessions(Long userId) {
        boolean iAmKefu = Objects.equals(kefuProperties.getUserId(), userId);
        LambdaQueryWrapper<ChatSession> qw = Wrappers.lambdaQuery();
        qw.eq(ChatSession::getStatus, 1)
                .and(w -> {
                    if (iAmKefu) {
                        w.eq(ChatSession::getBuyerId, userId).or().eq(ChatSession::getSellerId, userId);
                    } else {

                        w.eq(ChatSession::getBuyerId, userId).eq(ChatSession::getSellerId, kefuProperties.getUserId())
                                .or()
                                .eq(ChatSession::getSellerId, userId).eq(ChatSession::getBuyerId, kefuProperties.getUserId());
                    }
                })
                .orderByDesc(ChatSession::getLastMessageTime)
                .last("LIMIT 100");
        List<ChatSession> sessions = chatSessionMapper.selectList(qw);

        return sessions.stream().map(s -> toVO(s, userId)).toList();
    }

    @Override
    public List<SessionVO> listKefuSessions() {
        Long kefuId = kefuProperties.getUserId();
        LambdaQueryWrapper<ChatSession> qw = Wrappers.lambdaQuery();
        // 只认「卖方是客服」的会话：既符合 B2C 语义，也把 C2C 时期的历史脏会话挡在外面
        qw.eq(ChatSession::getSellerId, kefuId)
                .eq(ChatSession::getStatus, 1)
                .orderByDesc(ChatSession::getLastMessageTime)
                // 次级排序：新会话还没发消息时 lastMessageTime 为 NULL，按更新时间兜底，
                // 否则「刚建的会话」会沉到列表最底部，运营以为用户没来咨询
                .orderByDesc(ChatSession::getUpdateTime)
                .last("LIMIT 100");
        return chatSessionMapper.selectList(qw).stream()
                .map(s -> toVO(s, kefuId))
                .toList();
    }

    @Override
    public SessionVO toVO(ChatSession session, Long userId) {
        boolean iAmBuyer = Objects.equals(session.getBuyerId(), userId);
        Long peerId = iAmBuyer ? session.getSellerId() : session.getBuyerId();

        SessionVO vo = new SessionVO();
        vo.setSessionId(session.getSessionId());
        vo.setPeerId(peerId);
        // 对方是平台客服时显示「官方客服」，否则退回「用户ID」兜底。
        // TODO: 后续接 user-service 批量查昵称，届时这里换成真实用户昵称
        vo.setPeerName(resolvePeerName(peerId));
        vo.setProductId(session.getProductId());
        vo.setProductName(session.getProductName());
        vo.setLastMessage(session.getLastMessage());
        vo.setLastMessageTime(session.getLastMessageTime());
        vo.setUnread(iAmBuyer ? session.getBuyerUnread() : session.getSellerUnread());
        vo.setPeerOnline(Boolean.TRUE.equals(
                stringRedisTemplate.hasKey(ChatConstant.REDIS_KEY_ONLINE + peerId)));
        return vo;
    }

    /** 对端展示名：客服用配置名，普通用户先用 ID 兜底 */
    private String resolvePeerName(Long peerId) {
        if (peerId != null && Objects.equals(peerId, kefuProperties.getUserId())) {
            return kefuProperties.getNickname();
        }
        return "用户" + peerId;
    }

    @Override
    public long unreadTotal(Long userId) {
        Long total = chatSessionMapper.sumUnread(userId);
        return total == null ? 0L : total;
    }

    @Override
    public void markRead(String sessionId, Long userId) {
        ChatSession session = getBySessionId(sessionId);
        if (session == null) {
            return;
        }
        LambdaUpdateWrapper<ChatSession> uw = Wrappers.lambdaUpdate();
        uw.eq(ChatSession::getSessionId, sessionId);
        if (Objects.equals(session.getBuyerId(), userId)) {
            uw.set(ChatSession::getBuyerUnread, 0);
        } else if (Objects.equals(session.getSellerId(), userId)) {
            uw.set(ChatSession::getSellerUnread, 0);
        } else {
            // 不是这个会话的人来清未读 —— 直接拒绝，不能静默成功（那是越权）
            throw new IllegalArgumentException("无权操作该会话");
        }
        uw.set(ChatSession::getUpdateTime, LocalDateTime.now());
        chatSessionMapper.update(null, uw);
    }

    public static String buildSessionId(Long uidA, Long uidB) {
        long min = Math.min(uidA, uidB);
        long max = Math.max(uidA, uidB);
        return min + ChatConstant.SESSION_ID_SEP + max;
    }
}
