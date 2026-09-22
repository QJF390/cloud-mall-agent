package com.exdemo.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员账号（系统管理）。
 *
 * <p>对应建表脚本：{@code sql/admin-schema.sql} → {@code t_admin_user}</p>
 */
@Data
@TableName("t_admin_user")
public class AdminUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录名，唯一 */
    private String username;

    /**
     * 密码（BCrypt 加密存储，禁止明文/可逆加密）。
     *
     * <p>{@code WRITE_ONLY} = 只允许反序列化（新增/改密时前端传得进来），
     * 但序列化时永远不输出。防止管理员列表接口把密码哈希带到前端。</p>
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /** 昵称 */
    private String nickname;

    /** 角色ID（关联 t_sys_role） */
    private Long roleId;

    /** 状态：1-启用 0-禁用 */
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
