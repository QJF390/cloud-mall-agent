package com.exdemo.common.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import java.math.BigInteger;

/**
 * Jackson 全局配置
 * <p>解决前端 JavaScript Number 精度丢失问题：
 * JS 安全整数范围只有 53 位（约 16 位十进制），后端雪花 ID（19 位 Long）
 * 直接序列化为数字时，前端 JSON.parse 会截断精度，导致商品/订单/用户 ID
 * 与数据库不一致，出现"商品不存在"等诡异问题。</p>
 * <p>使用 serializerByType 直接注册，不会覆盖 Spring Boot 默认的 JavaTimeModule，
 * 保证 LocalDateTime 等 JDK8 日期时间类型仍能正常序列化。</p>
 */
@AutoConfiguration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            // Long 及 long 基础类型统一输出为字符串
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
            // BigInteger 同样处理（如数据库主键使用 unsigned bigint 场景）
            builder.serializerByType(BigInteger.class, ToStringSerializer.instance);
        };
    }
}
