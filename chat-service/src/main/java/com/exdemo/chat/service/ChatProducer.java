package com.exdemo.chat.service;

import com.exdemo.chat.constant.ChatConstant;
import com.exdemo.chat.dto.ChatMessagePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/**
 * MQ 发送门面。
 *
 * <p>把所有「发到哪个 topic、用什么方式发」收拢到一处。
 * 好处：以后要换 topic、加 tag、加染色标识，只改这一个文件；
 * 业务代码里不会出现裸的 {@code rocketMQTemplate.convertAndSend("xxx")}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public void sendIn(ChatMessagePayload payload) throws Exception {
        String destination = ChatConstant.TOPIC_CHAT_MSG_IN + ":" + ChatConstant.TAG_CHAT_SEND;
        rocketMQTemplate.syncSendOrderly(destination, payload, payload.getSessionId());
    }

    /**
     * 下行：落库完成 → 各实例广播推送。
     *
     * <p>用异步发送 + 回调：推送链路不阻塞落库消费者，回调里只打日志。
     * 这里的消息丢一条最多是「对方晚点收到」（还有离线消息兜底），
     * 不值得为它同步等待，吞吐量优先。</p>
     */
    public void sendPush(ChatMessagePayload payload) {
        String destination = ChatConstant.TOPIC_CHAT_PUSH + ":" + ChatConstant.TAG_CHAT_PUSH;
        rocketMQTemplate.asyncSend(destination, payload, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.debug("推送消息已发送 msgId={}", payload.getMsgId());
            }

            @Override
            public void onException(Throwable e) {
                // 真出问题要报警：这条意味着用户「发出去了但对方没收到」，是最容易被投诉的故障
                log.error("推送消息发送失败 msgId={}, sessionId={}", payload.getMsgId(), payload.getSessionId(), e);
            }
        });
    }
}
