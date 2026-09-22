package com.exdemo.admin.feign.fallback;

import com.exdemo.admin.dto.UserDTO;
import com.exdemo.admin.feign.UserFeignClient;
import com.exdemo.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class UserFeignFallback implements UserFeignClient {

    private static final String UNAVAILABLE = "用户服务暂时不可用，请稍后重试";

    @Override
    public R<UserDTO> getUserById(Long id) {
        log.warn("[fallback] user-service 不可用, getUserById id={}", id);
        return R.fail(503, UNAVAILABLE);
    }

    @Override
    public R<List<UserDTO>> listUsers() {
        log.warn("[fallback] user-service 不可用, listUsers");
        return R.fail(503, UNAVAILABLE);
    }

    @Override
    public R<Boolean> updateStatus(Long id, Integer status) {
        log.warn("[fallback] user-service 不可用, updateStatus id={}, status={}", id, status);
        return R.fail(503, UNAVAILABLE);
    }
}
