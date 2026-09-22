package com.exdemo.chat.dto;

import com.exdemo.chat.enums.ChatErrorCode;
import lombok.Data;

import java.io.Serializable;

@Data
public class ChatMessagePayload implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 帧类型：
     * <ul>
     *   <li>{@code chat} —— 聊天消息（上行=发送，下行=投递）</li>
     *   <li>{@code ack} —— 服务端已落库的回执（客户端据此把「发送中」改成「已送达」）</li>
     *   <li>{@code ping} / {@code pong} —— 心跳</li>
     *   <li>{@code error} —— 错误提示（被限流、参数非法、MQ 不可用等）</li>
     * </ul>
     */
    private String type;

    /** 客户端生成的消息ID（UUID），幂等去重 + 回执匹配的唯一依据 */
    private String msgId;

    /** 会话ID */
    private String sessionId;

    /** 发送者ID（下行帧由服务端填入，上行帧由服务端覆盖，永不信任客户端） */
    private Long senderId;

    /** 接收者ID */
    private Long receiverId;

    /** 消息类型，见 MessageTypeEnum */
    private Integer msgType;

    /** 消息正文 */
    private String content;

    /** 扩展JSON（图片URL / 商品卡片快照） */
    private String extra;

    /** 服务端落库后的消息ID（回执用，前端做历史消息去重） */
    private Long serverMsgId;

    /** 发送时间（毫秒时间戳） */
    private Long createTime;

    /** 错误提示（type=error 时用，给人看） */
    private String message;

    private String errorCode;

    // ==================== 快捷构造 ====================

    public static ChatMessagePayload chat() {
        ChatMessagePayload p = new ChatMessagePayload();
        p.setType("chat");
        return p;
    }

    public static ChatMessagePayload ack(String msgId, Long serverMsgId, Long createTime) {
        ChatMessagePayload p = new ChatMessagePayload();
        p.setType("ack");
        p.setMsgId(msgId);
        p.setServerMsgId(serverMsgId);
        p.setCreateTime(createTime);
        return p;
    }

    /** 场景固定、文案固定的错误（越权 / 限流 / MQ 挂了） */
    public static ChatMessagePayload error(String msgId, ChatErrorCode code) {
        return error(msgId, code, null);
    }

    public static ChatMessagePayload error(String msgId, ChatErrorCode code, String detail) {
        ChatMessagePayload p = new ChatMessagePayload();
        p.setType("error");
        p.setMsgId(msgId);
        p.setErrorCode(code.getCode());
        p.setMessage(detail == null || detail.isBlank() ? code.getMessage() : detail);
        return p;
    }

    public static ChatMessagePayload pong() {
        ChatMessagePayload p = new ChatMessagePayload();
        p.setType("pong");
        p.setCreateTime(System.currentTimeMillis());
        return p;
    }
}
