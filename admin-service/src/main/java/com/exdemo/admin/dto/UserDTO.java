package com.exdemo.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserDTO {

    private Long id;
    private String username;
    private String phone;
    private String email;
    private String avatar;
    private String bio;
    private Integer creditScore;

    /** 状态：1-正常 0-已禁用 */
    private Integer status;

    private LocalDateTime createTime;
}
