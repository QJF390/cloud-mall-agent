package com.exdemo.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_chat_message")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 客户端生成的消息ID（幂等去重键） */
    private String msgId;

    /** 所属会话 */
    private String sessionId;

    /** 发送者 */
    private Long senderId;

    /** 接收者（冗余，避免推送时回表查会话） */
    private Long receiverId;

    /** 消息类型，见 MessageTypeEnum */
    private Integer msgType;

    /** 消息正文 */
    private String content;

    /** 扩展 JSON */
    private String extra;

    /** 状态：1-已送达 2-已读 */
    private Integer status;

    private LocalDateTime createTime;
}
