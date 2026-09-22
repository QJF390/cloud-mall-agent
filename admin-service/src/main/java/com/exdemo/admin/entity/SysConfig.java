package com.exdemo.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置（系统管理 / 参数配置）。
 * TODO: 补充配置分组、是否可热更新（配合 @RefreshScope）等。
 */
@Data
@TableName("t_sys_config")
public class SysConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 配置键，唯一（如 order.timeout.minutes） */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 分组：ORDER / USER / PRODUCT / SYSTEM */
    private String configGroup;

    /** 备注说明 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
