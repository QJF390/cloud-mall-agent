package com.exdemo.chat.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

    private final StringRedisTemplate stringRedisTemplate;

    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>() {{
        setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/token_bucket.lua")));
        setResultType(Long.class);
    }};

    /**
     * 尝试获取令牌。
     *
     * @param key        限流维度（本项目按 userId）
     * @param permits    每秒生成的令牌数
     * @param capacity   桶容量（允许的瞬时突发量）
     * @return true = 放行；false = 被限流
     */
    public boolean tryAcquire(String key, int permits, int capacity) {
        try {
            Long result = stringRedisTemplate.execute(script,
                    java.util.Collections.singletonList(key),
                    String.valueOf(permits),
                    String.valueOf(capacity),
                    String.valueOf(System.currentTimeMillis()),
                    "1");
            return result != null && result == 1L;
        } catch (Exception e) {
            // 限流器挂了不能拖垮主流程（Redis 抖一下就全站发不出消息 = 灾难放大）。
            // 这里选择「放行」，同时在监控上报警，由网关层/其它手段兜底。
            log.error("限流器异常，降级放行 key={}", key, e);
            return true;
        }
    }
}
