package com.exdemo.admin.service;

import com.exdemo.admin.dto.PageQuery;
import com.exdemo.admin.dto.PageResult;
import com.exdemo.admin.entity.AdminUser;
import com.exdemo.admin.entity.SysConfig;
import com.exdemo.admin.entity.SysLog;
import com.exdemo.admin.entity.SysRole;
import com.exdemo.common.result.R;

import java.util.List;

/**
 * 系统管理服务（本服务独有：管理员 / 角色 / 日志 / 配置）。
 */
public interface AdminSystemService {

    // ==================== 管理员账号 ====================

    R<PageResult<AdminUser>> pageAdmins(PageQuery query);

    R<Void> saveAdmin(AdminUser admin);

    R<Void> resetPassword(Long adminId, String newPassword);

    // ==================== 角色 / 权限 ====================

    R<PageResult<SysRole>> pageRoles(PageQuery query);

    R<Void> saveRole(SysRole role);

    R<Void> deleteRole(Long roleId);

    // ==================== 操作日志 ====================

    R<PageResult<SysLog>> pageLogs(PageQuery query);

    // ==================== 系统配置 ====================

    R<List<SysConfig>> listConfigs(String group);

    R<Void> updateConfig(String configKey, String configValue);
}
