package com.exdemo.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统角色（系统管理 / RBAC）。
 * <p>permissions 以 JSON 数组存储菜单/按钮权限点，如 ["admin:user:list"]。</p>
 */
@Data
@TableName("t_sys_role")
public class SysRole {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 角色编码，如 SUPER_ADMIN / OPERATOR */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 权限点（JSON 数组，如 ["admin:user:list","admin:product:edit"]） */
    private String permissions;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
