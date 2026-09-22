package com.exdemo.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API 网关启动类
 * <p>网关是所有请求的"大门"，负责：</p>
 * <ul>
 *   <li>路由转发：把 /api/user/** 的请求转发到 user-service</li>
 *   <li>负载均衡：自动在多个服务实例之间分发请求</li>
 *   <li>跨域处理：统一解决前后端分离的 CORS 问题</li>
 *   <li>熔断降级：下游服务挂了时返回优雅的降级响应</li>
 * </ul>
 */
@EnableDiscoveryClient
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
