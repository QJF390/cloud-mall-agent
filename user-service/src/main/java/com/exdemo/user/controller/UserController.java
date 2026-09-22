package com.exdemo.user.controller;

import com.exdemo.common.result.R;
import com.exdemo.user.Service.UserService;
import com.exdemo.user.dto.UserDto;
import com.exdemo.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    @Autowired
    private final UserService userService;
    @PostMapping("/register")
    public R<UserDto> register(@RequestBody User user) {
        return userService.register(user);
    }
    @PostMapping("/login")
    public R<UserDto> login(@RequestBody User user) {
        return userService.login(user.getUsername(), user.getPassword());
    }

    @GetMapping("/profile")
    public R<UserDto> profile() {
        return userService.currentProfile();
    }

    @GetMapping("/get/{id}")
    public R<User> getUser(@PathVariable Long id) {
        return R.ok(userService.getById(id));
    }
    @GetMapping("/getallusers")
    public R<List<User>> getAllUsers() {
        return R.ok(userService.listUsers());
    }

    // ========== 管理端（仅由 admin-service 通过 Feign 调用） ==========

    /** 修改用户状态：1-正常 0-禁用 */
    @PostMapping("/admin/{id}/status")
    public R<Boolean> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return userService.updateStatus(id, status);
    }

    // ========== 手机验证码登录 ==========

    /**
     * 发送短信验证码
     */
    @PostMapping("/sms/send")
    public R<Void> sendSmsCode(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        return userService.sendSmsCode(phone);
    }

    /**
     * 验证码登录
     */
    @PostMapping("/login/phone")
    public R<UserDto> loginByPhone(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String code = body.get("code");
        return userService.loginByPhone(phone, code);
    }
}
