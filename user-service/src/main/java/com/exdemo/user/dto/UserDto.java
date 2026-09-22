package com.exdemo.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserDto {

    /** 用户ID（前端购物车/订单等接口都需要，登录后必须下发） */
    private Long id;

    private String username;

    /** 用于存储token */
    private String token;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public UserDto(Long id, String username, String token,
                   LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.username = username;
        this.token = token;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }
}
