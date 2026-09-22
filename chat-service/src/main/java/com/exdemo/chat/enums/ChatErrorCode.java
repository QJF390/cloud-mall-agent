package com.exdemo.chat.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ChatErrorCode {

    /** 参数非法：msgId / sessionId / receiverId / content 不合规则 */
    INVALID_PARAM("INVALID_PARAM", "消息内容不合规，请修改后重发"),

    /** 越权：sessionId 里没有发言者的 userId，疑似伪造会话 */
    FORBIDDEN("FORBIDDEN", "无权在该会话发言"),

    /** 被限流：单用户发送频率超过令牌桶上限 */
    RATE_LIMITED("RATE_LIMITED", "发送过于频繁，请稍后再试"),

    /** MQ 不可用：消息压根没进队列，对方一定收不到 */
    MQ_UNAVAILABLE("MQ_UNAVAILABLE", "服务繁忙，消息未发出，请重试"),

    /** WebSocket 连接不可用（纯前端判定，服务端不下发此码） */
    CONNECTION_UNAVAILABLE("CONNECTION_UNAVAILABLE", "连接不可用，消息未发出");

    /** 机读错误码（前端做分支、监控打点用） */
    private final String code;

    /** 默认人读文案（具体原因比它更准确时，用 detail 覆盖） */
    private final String message;
}
