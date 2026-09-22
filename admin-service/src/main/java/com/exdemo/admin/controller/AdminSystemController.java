package com.exdemo.admin.controller;

import com.exdemo.admin.annotation.AdminOperation;
import com.exdemo.admin.dto.PageQuery;
import com.exdemo.admin.dto.PageResult;
import com.exdemo.admin.entity.AdminUser;
import com.exdemo.admin.entity.SysConfig;
import com.exdemo.admin.entity.SysLog;
import com.exdemo.admin.entity.SysRole;
import com.exdemo.admin.service.AdminSystemService;
import com.exdemo.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统管理接口（管理员 / 角色 / 日志 / 配置）。
 *
 * <p>网关路由：/admin/** → admin-service；本类映射 /admin/system。</p>
 *
 * <p>写操作统一打 {@code @AdminOperation} 落审计日志——后台改号、改角色、改配置
 * 都是"出了事要查是谁干的"的高危动作，没有日志就只能背锅。</p>
 */
@RestController
@RequestMapping("/admin/system")
@RequiredArgsConstructor
public class AdminSystemController {

    private final AdminSystemService adminSystemService;

    // ==================== 管理员账号 ====================

    @GetMapping("/admin/page")
    public R<PageResult<AdminUser>> pageAdmins(PageQuery query) {
        return adminSystemService.pageAdmins(query);
    }

    @AdminOperation(module = "SYSTEM", action = "保存管理员账号")
    @PostMapping("/admin/save")
    public R<Void> saveAdmin(@RequestBody AdminUser admin) {
        return adminSystemService.saveAdmin(admin);
    }

    @AdminOperation(module = "SYSTEM", action = "重置管理员密码")
    @PostMapping("/admin/{id}/reset-password")
    public R<Void> resetPassword(@PathVariable Long id, @RequestParam String newPassword) {
        return adminSystemService.resetPassword(id, newPassword);
    }

    // ==================== 角色 / 权限 ====================

    @GetMapping("/role/page")
    public R<PageResult<SysRole>> pageRoles(PageQuery query) {
        return adminSystemService.pageRoles(query);
    }

    @AdminOperation(module = "SYSTEM", action = "保存角色")
    @PostMapping("/role/save")
    public R<Void> saveRole(@RequestBody SysRole role) {
        return adminSystemService.saveRole(role);
    }

    @AdminOperation(module = "SYSTEM", action = "删除角色")
    @DeleteMapping("/role/{id}")
    public R<Void> deleteRole(@PathVariable Long id) {
        return adminSystemService.deleteRole(id);
    }

    // ==================== 操作日志 ====================

    @GetMapping("/log/page")
    public R<PageResult<SysLog>> pageLogs(PageQuery query) {
        return adminSystemService.pageLogs(query);
    }

    // ==================== 系统配置 ====================

    @GetMapping("/config/list")
    public R<List<SysConfig>> listConfigs(@RequestParam(required = false) String group) {
        return adminSystemService.listConfigs(group);
    }

    @AdminOperation(module = "SYSTEM", action = "修改系统配置")
    @PostMapping("/config/update")
    public R<Void> updateConfig(@RequestParam String configKey, @RequestParam String configValue) {
        return adminSystemService.updateConfig(configKey, configValue);
    }
}
