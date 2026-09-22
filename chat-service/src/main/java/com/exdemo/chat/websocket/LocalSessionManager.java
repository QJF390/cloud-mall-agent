package com.exdemo.chat.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
public class LocalSessionManager {

    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    /** 建立连接：登记 userId → session */
    public void register(Long userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("WS 建立连接 userId={}, sessionId={}, 当前该用户连接数={}",
                userId, session.getId(), userSessions.get(userId).size());
    }

    /** 断开连接：移除登记；返回该用户在本机是否还有其它连接（用于判断「真离线」） */
    public boolean unregister(Long userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null) {
            return false;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            userSessions.remove(userId);
            log.info("WS 断开连接 userId={}, 该用户在本机已无连接", userId);
            return false;
        }
        log.info("WS 断开连接 userId={}, 该用户仍有 {} 个连接（多端在线）", userId, sessions.size());
        return true;
    }

    /**
     * 给某用户在本机的所有连接推送文本。
     *
     * @return 实际推送成功的连接数（0 表示对方不在这台机器上 / 已离线）
     */
    public int sendToUser(Long userId, String text) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }
        int success = 0;
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                // 连接已失效但还没触发 afterConnectionClosed，顺手清掉，防止集合无限膨胀
                sessions.remove(session);
                continue;
            }
            synchronized (session) {
                // WebSocketSession 不是线程安全的：心跳线程和业务线程同时发会出现
                // "The remote endpoint was in state [TEXT_PARTIAL_WRITING]" 异常
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new org.springframework.web.socket.TextMessage(text));
                        success++;
                    }
                } catch (Exception e) {
                    log.warn("WS 推送失败 userId={}, sessionId={}", userId, session.getId(), e);
                }
            }
        }
        return success;
    }

    /** 该用户是否在本机有连接 */
    public boolean isOnlineLocally(Long userId) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    /** 本机连接总数（监控用） */
    public int countSessions() {
        return userSessions.values().stream().mapToInt(Set::size).sum();
    }
}
