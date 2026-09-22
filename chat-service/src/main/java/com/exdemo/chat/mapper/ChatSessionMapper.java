package com.exdemo.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exdemo.chat.entity.ChatSession;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 会话 Mapper。
 */
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    @Select("""
            SELECT COALESCE(SUM(CASE WHEN buyer_id = #{userId} THEN buyer_unread ELSE seller_unread END), 0)
            FROM t_chat_session
            WHERE (buyer_id = #{userId} OR seller_id = #{userId}) AND status = 1
            """)
    Long sumUnread(@Param("userId") Long userId);
}
