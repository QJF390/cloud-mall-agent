package com.exdemo.admin.controller;

import com.exdemo.admin.annotation.AdminOperation;
import com.exdemo.admin.dto.PageQuery;
import com.exdemo.admin.dto.PageResult;
import com.exdemo.admin.dto.UserDTO;
import com.exdemo.admin.service.AdminUserService;
import com.exdemo.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理接口。
 *
 * <p>网关路由：/admin/** → admin-service；本类映射 /admin/user。</p>
 */
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** 用户列表（分页 + 关键词/时间筛选） */
    @GetMapping("/page")
    public R<PageResult<UserDTO>> page(PageQuery query) {
        return adminUserService.pageUsers(query);
    }

    /** 用户详情 */
    @GetMapping("/{id}")
    public R<UserDTO> detail(@PathVariable Long id) {
        return adminUserService.getUser(id);
    }

    /** 禁用用户 */
    @AdminOperation(module = "USER", action = "禁用用户")
    @PostMapping("/{id}/disable")
    public R<Void> disable(@PathVariable Long id) {
        return adminUserService.disableUser(id);
    }

    /** 启用用户 */
    @AdminOperation(module = "USER", action = "启用用户")
    @PostMapping("/{id}/enable")
    public R<Void> enable(@PathVariable Long id) {
        return adminUserService.enableUser(id);
    }
}
