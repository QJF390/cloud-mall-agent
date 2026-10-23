package com.exdemo.order.dto;

import lombok.Data;

/**
 * 用户 DTO（Feign 远程调用返回）
 * <p>刻意只保留下游需要的字段：DTO 字段越多，上游加字段时反序列化耦合越重。</p>
 */
@Data
public class UserDTO {

    private Long id;

    private String username;

    private String phone;
}
