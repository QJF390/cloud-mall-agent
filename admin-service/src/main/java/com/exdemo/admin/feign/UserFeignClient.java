package com.exdemo.admin.feign;

import com.exdemo.admin.dto.UserDTO;
import com.exdemo.admin.feign.fallback.UserFeignFallback;
import com.exdemo.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "user-service", fallback = UserFeignFallback.class)
public interface UserFeignClient {

    /** 用户详情 */
    @GetMapping("/user/get/{id}")
    R<UserDTO> getUserById(@PathVariable("id") Long id);

    /** 查询全部用户（分页在管理端聚合层做） */
    @GetMapping("/user/getallusers")
    R<List<UserDTO>> listUsers();

    /** 修改用户状态：1-启用 0-禁用（管理端专用接口） */
    @PostMapping("/user/admin/{id}/status")
    R<Boolean> updateStatus(@PathVariable("id") Long id, @RequestParam("status") Integer status);
}
