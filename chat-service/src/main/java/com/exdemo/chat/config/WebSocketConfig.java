package com.exdemo.chat.config;

import com.exdemo.chat.constant.ChatConstant;
import com.exdemo.chat.websocket.AuthHandshakeInterceptor;
import com.exdemo.chat.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WebSocket 装配。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;

    @Value("${chat.ws.path}")
    private String wsPath;

    @Value("${chat.ws.heartbeat-interval}")
    private long heartbeatInterval;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, wsPath)
                .addInterceptors(authHandshakeInterceptor)
                // 跨域：生产环境务必改成具体域名白名单。
                // 用 "*" 意味着任意站点都能拿用户 token 建长连接（CSWSH），属于安全漏洞。
                .setAllowedOriginPatterns("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(ChatConstant.MAX_TEXT_BUFFER_SIZE);
        container.setMaxBinaryMessageBufferSize(ChatConstant.MAX_TEXT_BUFFER_SIZE);
        // 空闲超时：正常客户端会按 heartbeat-interval 发 ping，超时即断，
        // 及时回收「手机断网后残留的半死连接」（这类连接是长连接服务最常见的内存泄漏源）
        container.setMaxSessionIdleTimeout(heartbeatInterval * 3);
        return container;
    }
}
