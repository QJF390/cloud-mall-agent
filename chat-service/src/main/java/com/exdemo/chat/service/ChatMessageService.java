package com.exdemo.chat.service;

import com.exdemo.chat.dto.ChatMessagePayload;
import com.exdemo.chat.entity.ChatMessage;

import java.util.List;

/**
 * 消息业务。
 */
public interface ChatMessageService {

    /**
     * 落库（幂等）。
     *
     * @return 落库后的消息；返回 null 表示「重复消息，已丢弃」
     */
    ChatMessage persist(ChatMessagePayload payload);

    /**
     * 游标分页查历史消息。
     *
     * @param sessionId 会话ID
     * @param cursor    上一页最小消息ID（首次传 null 表示取最新的）
     * @param size      每页条数
     */
    List<ChatMessage> history(String sessionId, Long cursor, int size);
}
