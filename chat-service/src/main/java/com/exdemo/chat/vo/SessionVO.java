package com.exdemo.chat.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话ID */
    private String sessionId;

    /** 对方用户ID（当前用户视角） */
    private Long peerId;

    /**
     * 对方展示名（当前用户视角）。
     * 客服账号显示为「官方客服」，普通用户显示「用户xxxx」。
     */
    private String peerName;

    /** 关联商品ID（可能为空） */
    private Long productId;

    /** 商品名快照 */
    private String productName;

    /** 最后一条消息摘要 */
    private String lastMessage;

    /** 最后一条消息时间 */
    private LocalDateTime lastMessageTime;

    /** 我的未读数 */
    private Integer unread;

    /** 对方是否在线 */
    private Boolean peerOnline;
}
