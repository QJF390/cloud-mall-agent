package com.exdemo.admin.service;

import com.exdemo.admin.dto.AdminLoginRequest;
import com.exdemo.common.result.R;

import java.util.Map;

/**
 * 管理端认证服务。
 */
public interface AdminAuthService {

    /** 管理员登录，返回 token 与基本信息 */
    R<Map<String, Object>> login(AdminLoginRequest request);

    /** 退出登录 */
    R<Void> logout();

    /** 当前登录管理员信息 */
    R<Map<String, Object>> currentUser();
}
