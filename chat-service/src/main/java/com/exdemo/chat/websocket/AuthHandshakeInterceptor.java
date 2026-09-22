package com.exdemo.chat.websocket;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    /** 握手成功后，userId 存在 session 属性里的 key；后续所有处理从这里取，绝不信任客户端传的 */
    public static final String ATTR_USER_ID = "UID";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                  WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = pickToken(request);
        if (token == null || token.isBlank()) {
            log.warn("WS 握手失败：缺少 token，remote={}", request.getRemoteAddress());
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            // getLoginIdByToken：不依赖请求上下文的「按 token 查登录态」，握手场景专用
            Object loginId = StpUtil.getLoginIdByToken(token);
            long userId = Long.parseLong(String.valueOf(loginId));
            attributes.put(ATTR_USER_ID, userId);
            return true;
        } catch (Exception e) {
            log.warn("WS 握手失败：token 无效或已过期");
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无额外资源需要清理
    }

    /**
     * 取 token：优先 header（satoken），其次 query 参数（satoken / token）。
     * 两种都支持，是为了同时兼容「前端 SDK 带 header」和「浏览器原生 WS 只能拼 URL」。
     */
    private String pickToken(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst("satoken");
        if (token != null && !token.isBlank()) {
            return token;
        }
        String query = request.getURI().getQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String kv : query.split("&")) {
            String[] arr = kv.split("=", 2);
            if (arr.length == 2 && ("satoken".equals(arr[0]) || "token".equals(arr[0]))) {
                return arr[1];
            }
        }
        return null;
    }
}
