package com.exdemo.order.dto;

import lombok.Data;

/**
 * 用户 DTO（Feign 远程调用返回）
 * TODO: 补充字段，与 user-service 返回结构一致
 */
@Data
public class UserDTO {

    private Long id;

    private String username;

    private String phone;
}
