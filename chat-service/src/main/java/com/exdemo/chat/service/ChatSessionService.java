package com.exdemo.chat.service;

import com.exdemo.chat.entity.ChatSession;
import com.exdemo.chat.vo.SessionVO;

import java.util.List;

/**
 * 会话业务。
 */
public interface ChatSessionService {

    /**
     * 获取（或创建）与某卖家的会话。
     *
     * @param buyerId     当前用户（发起方）
     * @param sellerId    对方（商品发布者）
     * @param productId   关联商品（可为空）
     * @param productName 商品名快照（可为空）
     */
    ChatSession getOrCreate(Long buyerId, Long sellerId, Long productId, String productName);

    /** 按 sessionId 查会话 */
    ChatSession getBySessionId(String sessionId);

    /** 我的会话列表（按最后一条消息时间倒序） */
    List<SessionVO> listMySessions(Long userId);

    /**
     * 后台客服视角的会话列表（所有「卖方=平台客服」的会话）。
     *
     * <p>只查客服作为卖方的会话，是从数据层再上一道锁：
     * 历史脏数据（C2C 时期留下 seller_id 指向不存在用户的会话）不会污染工作台。</p>
     */
    List<SessionVO> listKefuSessions();

    SessionVO toVO(ChatSession session, Long userId);

    /** 我的未读总数 */
    long unreadTotal(Long userId);

    /** 清空当前用户在某会话的未读 */
    void markRead(String sessionId, Long userId);
}
