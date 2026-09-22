package com.exdemo.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天会话（买家 ↔ 卖家，可绑定商品）。
 */
@Data
@TableName("t_chat_session")
public class ChatSession implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会话ID：{minUid}_{maxUid}，确定性生成保证幂等 */
    private String sessionId;

    /** 买家（发起方）ID */
    private Long buyerId;

    /** 卖家 ID */
    private Long sellerId;

    /** 关联商品ID */
    private Long productId;

    /** 商品名快照 */
    private String productName;

    /** 最后一条消息摘要 */
    private String lastMessage;

    /** 最后一条消息时间 */
    private LocalDateTime lastMessageTime;

    /** 买家未读数 */
    private Integer buyerUnread;

    /** 卖家未读数 */
    private Integer sellerUnread;

    /** 状态：1-正常 0-已删除 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
