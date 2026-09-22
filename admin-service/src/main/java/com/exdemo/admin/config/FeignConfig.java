package com.exdemo.admin.config;

import cn.dev33.satoken.context.SaHolder;
import com.exdemo.admin.constant.AdminConstant;
import feign.Logger;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    /** Feign 日志级别（BASIC 只记录方法/URL/状态/耗时，生产勿用 FULL，否则打印整个 body 泄露隐私） */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public RequestInterceptor feignTokenInterceptor() {
        return template -> {
            try {
                String token = SaHolder.getRequest().getHeader(AdminConstant.TOKEN_HEADER);
                if (token != null && !token.isBlank()) {
                    template.header(AdminConstant.TOKEN_HEADER, token);
                }
            } catch (Exception e) {
                // 无请求上下文（如定时任务触发）时静默跳过，不能让透传逻辑影响主流程
            }
        };
    }
}
