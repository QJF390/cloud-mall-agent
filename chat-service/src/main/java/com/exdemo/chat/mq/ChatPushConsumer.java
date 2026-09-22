package com.exdemo.chat.mq;

import com.exdemo.chat.constant.ChatConstant;
import com.exdemo.chat.dto.ChatMessagePayload;
import com.exdemo.chat.websocket.LocalSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = ChatConstant.TOPIC_CHAT_PUSH,
        consumerGroup = ChatConstant.GROUP_CHAT_PUSH,
        selectorExpression = ChatConstant.TAG_CHAT_PUSH,
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.BROADCASTING)
public class ChatPushConsumer implements RocketMQListener<ChatMessagePayload> {

    private final LocalSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(ChatMessagePayload payload) {
        try {
            // ① 接收者：chat 帧
            payload.setType("chat");
            int hit = sessionManager.sendToUser(payload.getReceiverId(),
                    objectMapper.writeValueAsString(payload));

            // ② 发送者：ack 帧（含完整内容，多端同步时可直接当新消息渲染）
            ChatMessagePayload ackFrame = new ChatMessagePayload();
            BeanUtils.copyProperties(payload, ackFrame);
            ackFrame.setType("ack");
            sessionManager.sendToUser(payload.getSenderId(),
                    objectMapper.writeValueAsString(ackFrame));

            if (hit == 0) {
                // 接收者不在线或不在本实例：什么都不用做。
                // 消息已落库 + 会话未读已 +1，对方下次打开会话列表/拉历史自然能看到。
                log.debug("接收者不在线（或不在本实例），走离线消息 userId={}", payload.getReceiverId());
            }
        } catch (Exception e) {
            log.error("推送处理异常 msgId={}", payload.getMsgId(), e);
        }
    }
}
