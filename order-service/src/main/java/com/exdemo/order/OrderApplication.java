package com.exdemo.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableFeignClients        // 【关键】启用 Feign 客户端
@EnableDiscoveryClient
@EnableScheduling          // 【关键】超时未支付订单自动关单（OrderTimeoutCancelTask）依赖它，缺失时 @Scheduled 静默不执行
@SpringBootApplication
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
