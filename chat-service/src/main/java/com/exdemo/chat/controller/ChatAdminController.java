package com.exdemo.chat.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.exdemo.chat.config.KefuProperties;
import com.exdemo.chat.dto.ChatMessagePayload;
import com.exdemo.chat.entity.ChatMessage;
import com.exdemo.chat.entity.ChatSession;
import com.exdemo.chat.enums.MessageTypeEnum;
import com.exdemo.chat.service.ChatMessageService;
import com.exdemo.chat.service.ChatProducer;
import com.exdemo.chat.service.ChatSessionService;
import com.exdemo.chat.vo.SessionVO;
import com.exdemo.common.constant.AdminLoginConstant;
import com.exdemo.common.result.R;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/chat/admin")
@RequiredArgsConstructor
public class ChatAdminController {

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final ChatProducer chatProducer;
    private final KefuProperties kefuProperties;

    /** 会话列表（客服视角） */
    @GetMapping("/session/list")
    public R<List<SessionVO>> listSessions() {
        Long adminId = currentAdminId();
        if (adminId == null) {
            return R.fail(403, "仅后台管理员可访问客服工作台");
        }
        return R.ok(chatSessionService.listKefuSessions());
    }

    /** 某会话的历史消息（游标分页，倒序返回） */
    @GetMapping("/message/list")
    public R<List<ChatMessage>> history(@RequestParam String sessionId,
                                        @RequestParam(required = false) Long cursor,
                                        @RequestParam(defaultValue = "20") int size) {
        Long adminId = currentAdminId();
        if (adminId == null) {
            return R.fail(403, "仅后台管理员可访问客服工作台");
        }
        if (!isKefuSession(sessionId)) {
            return R.fail(403, "无权查看该会话");
        }
        return R.ok(chatMessageService.history(sessionId, cursor, size));
    }

    @PostMapping("/reply")
    public R<Void> reply(@RequestBody ReplyRequest request) {
        Long adminId = currentAdminId();
        if (adminId == null) {
            return R.fail(403, "仅后台管理员可操作");
        }
        if (request == null || request.getSessionId() == null || request.getContent() == null
                || request.getContent().isBlank()) {
            return R.badRequest("会话ID和内容不能为空");
        }
        ChatSession session = chatSessionService.getBySessionId(request.getSessionId());
        if (session == null) {
            return R.notFound("会话不存在");
        }
        // 只能回复「客服作为卖方」的会话：防止后台往任意会话里塞消息（越权写入）
        if (!Objects.equals(session.getSellerId(), kefuProperties.getUserId())) {
            return R.fail(403, "只能回复咨询平台客服的会话");
        }

        ChatMessagePayload payload = ChatMessagePayload.chat();
        payload.setMsgId(UUID.randomUUID().toString());
        payload.setSessionId(session.getSessionId());
        payload.setSenderId(kefuProperties.getUserId());
        payload.setReceiverId(session.getBuyerId());
        payload.setMsgType(MessageTypeEnum.TEXT.getCode());
        payload.setContent(request.getContent());
        payload.setCreateTime(System.currentTimeMillis());

        try {
            chatProducer.sendIn(payload);
        } catch (Exception e) {
            // MQ 挂了要让用户知道「没发出去」，绝不能吞掉假装成功
            log.error("客服回复发送失败 adminId={}, sessionId={}", adminId, session.getSessionId(), e);
            return R.fail(500, "消息发送失败，请稍后重试");
        }
        // 审计：谁在哪一刻回复了哪位用户。生产上应落 sys_log / 客服审计表，
        // 出现纠纷（承诺了退款、辱骂用户）时这是唯一的追责依据
        log.info("客服回复成功 adminId={}, sessionId={}, buyerId={}", adminId, session.getSessionId(),
                session.getBuyerId());
        return R.ok();
    }

    /** 标记已读（清掉客服侧的未读） */
    @PostMapping("/message/read")
    public R<Void> markRead(@RequestParam String sessionId) {
        Long adminId = currentAdminId();
        if (adminId == null) {
            return R.fail(403, "仅后台管理员可操作");
        }
        if (!isKefuSession(sessionId)) {
            return R.fail(403, "无权操作该会话");
        }
        chatSessionService.markRead(sessionId, kefuProperties.getUserId());
        return R.ok();
    }

    /** 会话是否属于客服（越权校验统一收口，避免每个方法各写一遍） */
    private boolean isKefuSession(String sessionId) {
        ChatSession session = chatSessionService.getBySessionId(sessionId);
        return session != null && Objects.equals(session.getSellerId(), kefuProperties.getUserId());
    }

    private Long currentAdminId() {
        if (!StpUtil.isLogin()) {
            return null;
        }
        return AdminLoginConstant.parseAdminId(StpUtil.getLoginIdAsString());
    }

    /** 回复请求（参数校验用显式判空而不是注解：省掉一层 validation 依赖，语义也更直白） */
    @Data
    public static class ReplyRequest {
        private String sessionId;
        private String content;
    }
}
