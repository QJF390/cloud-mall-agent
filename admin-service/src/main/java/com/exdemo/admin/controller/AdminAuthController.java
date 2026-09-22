package com.exdemo.admin.controller;

import com.exdemo.admin.dto.AdminLoginRequest;
import com.exdemo.admin.service.AdminAuthService;
import com.exdemo.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端认证：登录 / 登出 / 当前管理员。
 *
 * <p>网关路由：/admin/** → admin-service；本类映射 /admin/auth。</p>
 */
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    /** 管理员登录 */
    @PostMapping("/login")
    public R<Map<String, Object>> login(@Valid @RequestBody AdminLoginRequest request) {
        return adminAuthService.login(request);
    }

    /** 退出登录 */
    @PostMapping("/logout")
    public R<Void> logout() {
        return adminAuthService.logout();
    }

    /** 当前登录管理员信息 */
    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        return adminAuthService.currentUser();
    }
}
