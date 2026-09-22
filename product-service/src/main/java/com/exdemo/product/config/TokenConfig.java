package com.exdemo.product.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class TokenConfig implements WebMvcConfigurer {
//    注册拦截器当访问拦截器的路径的时候进行验证
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
    // 注册 Sa-Token 拦截器：访问被拦截路径时自动校验登录态
    registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
            .addPathPatterns("/product/**")   // 商品模块所有接口都要登录
            // 放行名单必须与网关 gateway/SaTokenConfig 的 addExclude 保持一致：
            // 网关放行了、下游却拦截，请求会在下游抛 NotLoginException 变成 500（而不是预期的数据），
            // 这类「两层口径不一致」的坑在线上极难排查——网关日志是 200，业务日志才是报错。
            // 商品详情 /product/get/** 刻意不放行：未登录点进详情会被拦下返回 401。
            .excludePathPatterns(
                    "/product/list",          // 商品列表（未登录可浏览）
                    "/product/listByIds",     // Feign 内部批量查询（服务间调用无 token）
                    "/product/search",        // 商品搜索（未登录可搜，AI 助手依赖此接口）
                    "/product/categories"     // 分类字典（前端/AI 都要用）
            );
    }

}
