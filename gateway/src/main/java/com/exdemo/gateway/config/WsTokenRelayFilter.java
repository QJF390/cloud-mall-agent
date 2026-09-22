package com.exdemo.gateway.config;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class WsTokenRelayFilter implements WebFilter, Ordered {

    private static final String TOKEN_NAME = "satoken";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        // 只在「header 里没有 token」且「query 里有」时才搬，避免覆盖已经带 header 的客户端
        if (request.getHeaders().getFirst(TOKEN_NAME) != null) {
            return chain.filter(exchange);
        }
        String token = fromQuery(request.getURI().getQuery());
        if (token == null) {
            return chain.filter(exchange);
        }
        ServerHttpRequest mutated = request.mutate().header(TOKEN_NAME, token).build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private String fromQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String kv : query.split("&")) {
            String[] arr = kv.split("=", 2);
            if (arr.length == 2 && TOKEN_NAME.equals(arr[0]) && !arr[1].isBlank()) {
                return arr[1];
            }
        }
        return null;
    }
}
