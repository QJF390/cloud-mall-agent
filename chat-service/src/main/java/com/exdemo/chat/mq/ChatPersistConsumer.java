package com.exdemo.chat.mq;

import com.exdemo.chat.constant.ChatConstant;
import com.exdemo.chat.dto.ChatMessagePayload;
import com.exdemo.chat.entity.ChatMessage;
import com.exdemo.chat.service.ChatMessageService;
import com.exdemo.chat.service.ChatProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = ChatConstant.TOPIC_CHAT_MSG_IN,
        consumerGroup = ChatConstant.GROUP_CHAT_PERSIST,
        selectorExpression = ChatConstant.TAG_CHAT_SEND,
        consumeMode = ConsumeMode.ORDERLY,
        messageModel = MessageModel.CLUSTERING,
        maxReconsumeTimes = 3)
public class ChatPersistConsumer implements RocketMQListener<ChatMessagePayload> {

    private final ChatMessageService chatMessageService;
    private final ChatProducer chatProducer;

    @Override
    public void onMessage(ChatMessagePayload payload) {
        try {
            ChatMessage saved = chatMessageService.persist(payload);
            if (saved == null) {
                // 重复消息：已被幂等拦下，不重复推送（否则对方会收到两条一样的）
                return;
            }
            payload.setServerMsgId(saved.getId());
            payload.setSenderId(saved.getSenderId());
            payload.setReceiverId(saved.getReceiverId());
            payload.setCreateTime(saved.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            // 落库成功后才广播推送：保证「能推给对方」的消息一定是「已经持久化」的消息
            chatProducer.sendPush(payload);
        } catch (DataAccessException e) {
            // DB 抖动：抛出去让 MQ 重试（这类异常重试大概率能成功，不能丢消息）
            log.error("消息落库失败，等待 MQ 重试 msgId={}", payload.getMsgId(), e);
            throw e;
        } catch (Exception e) {

            log.error("消息处理异常已丢弃 msgId={}, payload={}", payload.getMsgId(), payload, e);
        }
    }
}
