package com.exdemo.order.feign.fallback;

import com.exdemo.common.result.R;
import com.exdemo.order.dto.UserDTO;
import com.exdemo.order.feign.UserFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户服务熔断降级
 */
@Slf4j
@Component
public class UserFeignFallback implements UserFeignClient {

    @Override
    public R<UserDTO> getUserById(Long id) {
        log.error("【熔断降级】查询用户失败, userId={}", id);
        return R.fail("用户服务不可用，请稍后重试");
    }
}
