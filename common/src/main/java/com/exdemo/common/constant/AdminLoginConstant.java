package com.exdemo.common.constant;

public final class AdminLoginConstant {

    /** 管理端 loginId 前缀：loginId 形如 {@code ADMIN:1} */
    public static final String LOGIN_PREFIX = "ADMIN:";

    private AdminLoginConstant() {
    }

    /** 判断某个 loginId 是否属于管理端账号 */
    public static boolean isAdmin(String loginId) {
        return loginId != null && loginId.startsWith(LOGIN_PREFIX);
    }

    /**
     * 从 loginId 解析管理员ID。
     *
     * @return 管理员ID；非管理端 loginId 或格式非法时返回 null（调用方必须判空）
     */
    public static Long parseAdminId(String loginId) {
        if (!isAdmin(loginId)) {
            return null;
        }
        try {
            return Long.valueOf(loginId.substring(LOGIN_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
