package com.exdemo.gateway.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关统一鉴权。
 *
 * <p>网关是第一道门，负责"登录态"；真正的"管理员角色"校验在 admin-service（第二道门）。
 * 两道门缺一不可：只做网关鉴权 = 任何 C 端用户拿着自己的 token 就能调后台接口。</p>
 */
@Configuration
public class SaTokenConfig {

    /** 管理员登录 ID 前缀（与 admin-service 的 AdminConstant.ADMIN_LOGIN_PREFIX 保持一致） */
    private static final String ADMIN_LOGIN_PREFIX = "ADMIN:";

    @Bean
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
                // 拦哪些：所有路径
                .addInclude("/**")
                // 放哪些：登录注册等公开接口 + 商品列表（未登录可浏览） + 后台登录接口
                // 注意：/product/get/**（商品详情）刻意不放行 —— 未登录点进详情会被网关直接拦下返回 401
                .addExclude("/user/login", "/user/register", "/user/sms/send", "/user/login/phone",
                        "/product/list", "/product/search", "/product/categories", "/fallback/**",

                        "/alipay/notify",
                        // 同步跳转：支付宝把用户浏览器跳回前端，跨域场景下带不上 cookie/satoken，
                        // 同样必须放行。它只做重定向，不做入账，不可信也不影响资金安全。
                        "/alipay/return",
                        // 后台管理系统：登录接口必须放行（否则登录请求被拦 401，永远拿不到 token）；
                        // 其余 /admin/** 仍要求登录，管理员角色的二次校验在 admin-service 内完成。
                        "/admin/auth/login",
                        // 商品图片等静态资源：浏览器 <img src> 不会带 satoken 头，必须公开可读。
                        // 注意这里只是「读」公开，「写」（上传）仍然挂在 /admin/product/upload 下要求管理员登录。
                        "/uploads/**")
                .setAuth(obj -> {
                    // ① 内部管理接口：只允许服务间 Feign 调用（admin-service 直连下游服务，不走网关）。
                    //    外部一律拒绝 —— 否则普通用户能绕过 BFF 直接改商品上下架/改库存/强制关单。
                    SaRouter.match("/user/admin/**", "/product/admin/**", "/order/admin/**")
                            .check(r -> {
                                throw new SaTokenException(403, "禁止直接访问内部管理接口");
                            });

                    // ② 后台 BFF 接口：登录 + 必须是管理员 token（登录 ID 带 ADMIN: 前缀）
                    SaRouter.match("/admin/**").check(r -> {
                        StpUtil.checkLogin();
                        if (!StpUtil.getLoginIdAsString().startsWith(ADMIN_LOGIN_PREFIX)) {
                            throw new SaTokenException(403, "当前账号无后台访问权限");
                        }
                    });

                    // ③ 其余接口：仅校验登录
                    SaRouter.match("/**")
                            .notMatch("/admin/**", "/user/admin/**", "/product/admin/**", "/order/admin/**")
                            .check(r -> StpUtil.checkLogin());
                })
                // 统一错误响应：未登录=401，其余鉴权异常按其自身 code 返回（403 等）
                .setError(e -> {
                    if (e instanceof NotLoginException) {
                        return SaResult.error("请先登录").setCode(401);
                    }
                    if (e instanceof SaTokenException ste) {
                        return SaResult.error(ste.getMessage()).setCode(ste.getCode());
                    }
                    return SaResult.error("鉴权失败，请重新登录").setCode(401);
                });
    }
}
