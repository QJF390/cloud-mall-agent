package com.exdemo.common.util;

/**
 * 通用校验工具类
 * <p>集中管理所有正则校验规则，各微服务直接调用</p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 *   if (!ValidationUtil.isPhone("13800138000")) {
 *       return R.fail("手机号格式不正确");
 *   }
 * }</pre>
 */
public final class ValidationUtil {

    /** 手机号：1 开头 + 第二位 3-9 + 9 位数字 = 11 位 */
    public static final String PHONE_REGEX = "^1[3-9]\\d{9}$";

    /** 邮箱 */
    public static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";

    /** 用户名：4-20 位，字母开头，字母数字下划线 */
    public static final String USERNAME_REGEX = "^[a-zA-Z][a-zA-Z0-9_]{3,19}$";

    /** 密码：8-20 位，至少含字母和数字 */
    public static final String PASSWORD_REGEX = "^(?=.*[a-zA-Z])(?=.*\\d)[a-zA-Z\\d!@#$%^&*()_+]{8,20}$";

    private ValidationUtil() {
        // 工具类禁止实例化
    }

    /**
     * 校验手机号格式
     */
    public static boolean isPhone(String phone) {
        return phone != null && phone.matches(PHONE_REGEX);
    }

    /**
     * 校验邮箱格式
     */
    public static boolean isEmail(String email) {
        return email != null && email.matches(EMAIL_REGEX);
    }

    /**
     * 校验用户名格式
     */
    public static boolean isUsername(String username) {
        return username != null && username.matches(USERNAME_REGEX);
    }

    /**
     * 校验密码强度
     */
    public static boolean isPassword(String password) {
        return password != null && password.matches(PASSWORD_REGEX);
    }
}
