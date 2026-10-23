package com.exdemo.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置（系统管理 / 参数配置）。
 * <p>按 configGroup 分组管理；需要热更新的参数配合 Nacos 配置的 @RefreshScope 使用。</p>
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
