package com.exdemo.chat.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.exdemo.chat.config.KefuProperties;
import com.exdemo.chat.constant.ChatConstant;
import com.exdemo.chat.dto.CreateSessionRequest;
import com.exdemo.chat.entity.ChatMessage;
import com.exdemo.chat.entity.ChatSession;
import com.exdemo.chat.service.ChatMessageService;
import com.exdemo.chat.service.ChatSessionService;
import com.exdemo.chat.vo.SessionVO;
import com.exdemo.common.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final StringRedisTemplate stringRedisTemplate;
    private final KefuProperties kefuProperties;

    /**
     * 平台客服信息（前端「联系客服」入口用）。
     *
     * <p>不要求登录：它只是一个公开的身份标识（ID + 昵称），不含任何隐私；
     * 反过来，如果要求登录，游客在商品页点「联系客服」会先被弹登录，转化直接掉一截。</p>
     */
    @GetMapping("/kefu")
    public R<Map<String, Object>> kefu() {
        Map<String, Object> data = new HashMap<>(2);
        data.put("userId", kefuProperties.getUserId());
        data.put("nickname", kefuProperties.getNickname());
        return R.ok(data);
    }

    @PostMapping("/session/create")
    public R<SessionVO> createSession(@RequestBody CreateSessionRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        if (request == null || request.getSellerId() == null) {
            return R.badRequest("sellerId 不能为空");
        }
        boolean iAmKefu = Objects.equals(kefuProperties.getUserId(), userId);
        if (!iAmKefu && !Objects.equals(kefuProperties.getUserId(), request.getSellerId())) {
            return R.fail(403, "自营模式下只能咨询平台客服");
        }
        ChatSession session = chatSessionService.getOrCreate(userId, request.getSellerId(),
                request.getProductId(), request.getProductName());
        return R.ok(chatSessionService.toVO(session, userId));
    }

    /** 我的会话列表 */
    @GetMapping("/session/list")
    public R<List<SessionVO>> listSessions() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(chatSessionService.listMySessions(userId));
    }

    /**
     * 历史消息（游标分页，倒序返回，前端自行反转为正序展示）。
     *
     * @param cursor 上一页返回的最小消息ID；不传 = 取最新一页
     */
    @GetMapping("/message/list")
    public R<List<ChatMessage>> history(@RequestParam String sessionId,
                                        @RequestParam(required = false) Long cursor,
                                        @RequestParam(defaultValue = "20") int size) {
        Long userId = StpUtil.getLoginIdAsLong();
        // 越权校验：会话ID 是「小ID_大ID」，很容易被猜出来。
        // 不校验的话，改一下 URL 参数就能看别人的聊天记录 —— 这是典型的水平越权漏洞（IDOR）。
        ChatSession session = chatSessionService.getBySessionId(sessionId);
        if (session == null) {
            return R.notFound("会话不存在");
        }
        if (!session.getBuyerId().equals(userId) && !session.getSellerId().equals(userId)) {
            return R.fail(403, "无权查看该会话");
        }
        List<ChatMessage> list = chatMessageService.history(sessionId, cursor, size);
        return R.ok(list);
    }

    /** 已读：清空我在该会话的未读 */
    @PostMapping("/message/read")
    public R<Void> markRead(@RequestParam String sessionId) {
        Long userId = StpUtil.getLoginIdAsLong();
        chatSessionService.markRead(sessionId, userId);
        return R.ok();
    }

    /** 我的未读总数（用于首页角标） */
    @GetMapping("/unread/total")
    public R<Long> unreadTotal() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(chatSessionService.unreadTotal(userId));
    }

    /** 对方是否在线 */
    @GetMapping("/online")
    public R<Boolean> online(@RequestParam Long userId) {
        return R.ok(Boolean.TRUE.equals(
                stringRedisTemplate.hasKey(ChatConstant.REDIS_KEY_ONLINE + userId)));
    }

}
