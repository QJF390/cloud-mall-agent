package com.exdemo.admin.config;

import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.exdemo.admin.constant.AdminConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AdminSaTokenConfig implements WebMvcConfigurer {

    private final AdminProperties adminProperties;

    /** 密码加密器：BCrypt 自带随机盐，同一明文每次结果不同，禁止用 MD5/SHA 存密码 */
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    // 1. 校验登录态（网关已校验一次，这里再校验是为了防止绕过网关直连服务）
                    StpUtil.checkLogin();

                    // 2. 校验管理员身份：管理员登录时会把角色写进 Token-Session
                    Object role = StpUtil.getTokenSession().get(AdminConstant.SESSION_ROLE_KEY);
                    if (role == null || String.valueOf(role).isBlank()) {
                        // code=403 交给 AdminExceptionHandler 统一转成 R，前端提示"无权限"
                        throw new SaTokenException(403, "当前账号不是管理员，禁止访问后台");
                    }
                }))
                .addPathPatterns("/admin/**")
                .excludePathPatterns(adminProperties.getExcludePaths());
    }
}
