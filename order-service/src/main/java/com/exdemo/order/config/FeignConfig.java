package com.exdemo.order.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

    /** Sa-Token 默认的请求头名称 */
    private static final String TOKEN_HEADER = "satoken";

    @Bean
    public RequestInterceptor feignTokenInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                // 从当前线程的 HTTP 请求上下文里取原始请求
                ServletRequestAttributes attributes =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes == null) {
                    return;
                }

                HttpServletRequest request = attributes.getRequest();
                String token = request.getHeader(TOKEN_HEADER);

                // 如果请求头里有 satoken，就放到 Feign 请求里一起发往下游
                if (token != null && !token.isEmpty()) {
                    template.header(TOKEN_HEADER, token);
                }
            }
        };
    }
}
