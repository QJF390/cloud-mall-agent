package com.exdemo.order.feign;

import com.exdemo.common.result.R;
import com.exdemo.order.dto.UserDTO;
import com.exdemo.order.feign.fallback.UserFeignFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 用户服务 Feign 客户端
 */
@FeignClient(name = "user-service", fallback = UserFeignFallback.class)
public interface UserFeignClient {

    /** 查询用户 */
    @GetMapping("/user/get/{id}")
    R<UserDTO> getUserById(@PathVariable("id") Long id);
}
