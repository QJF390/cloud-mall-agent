package com.exdemo.admin.constant;

import com.exdemo.common.constant.AdminLoginConstant;

/**
 * 管理端全局常量。
 *
 * <p>集中放置"跨层共享"的字面量，避免同一字符串在 Controller / Service / 配置类里各写一遍，
 * 改一处漏一处（这类漏改在生产上表现为"登录成功但接口一直 403"）。</p>
 */
public final class AdminConstant {

    /** Sa-Token 默认请求头名称（与网关、C 端保持一致，勿随意修改） */
    public static final String TOKEN_HEADER = "satoken";

    // 值收敛到 common：chat-service 的客服工作台也要用同一个前缀判断管理员身份，
    // 两边各写一份字符串迟早不一致（不一致 = 越权或误判）。
    public static final String ADMIN_LOGIN_PREFIX = AdminLoginConstant.LOGIN_PREFIX;

    /** Sa-Token Token-Session 中存放角色编码的 key */
    public static final String SESSION_ROLE_KEY = "adminRole";

    /** Sa-Token Token-Session 中存放管理员登录名的 key */
    public static final String SESSION_USERNAME_KEY = "adminUsername";

    /** 管理员账号状态：启用 */
    public static final int STATUS_ENABLED = 1;

    /** 管理员账号状态：禁用 */
    public static final int STATUS_DISABLED = 0;

    /** 登录失败次数上限（超过则锁定） */
    public static final int LOGIN_MAX_FAIL = 5;

    /** 登录失败锁定时长（分钟） */
    public static final long LOGIN_LOCK_MINUTES = 15;

    private AdminConstant() {
    }
}
