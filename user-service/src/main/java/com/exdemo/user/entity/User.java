package com.exdemo.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体
 * TODO: 补充字段、业务逻辑
 */
@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    private String password;

    private String phone;

    private String email;

    /** 头像图片URL */
    private String avatar;

    /** 个人简介（展示在个人主页/作品集） */
    private String bio;

    /** 信用分（C2C 卖家信誉：交易完成加分，纠纷/差评扣分） */
    private Integer creditScore;

    /** 账号状态：1-正常 0-已禁用（后台封禁用；历史数据为 NULL 时按正常处理） */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
