package com.exdemo.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志（系统管理 / 审计）。
 * TODO: 结合 AOP 切面自动落库（记录操作人、接口、耗时、结果）。
 */
@Data
@TableName("t_sys_log")
public class SysLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 操作人（管理员用户名） */
    private String operator;

    /** 操作模块：USER / PRODUCT / ORDER / SYSTEM */
    private String module;

    /** 操作描述 */
    private String action;

    /** 请求方法 + 路径 */
    private String requestUri;

    /** 是否成功：1-成功 0-失败 */
    private Integer success;

    /** 耗时（毫秒） */
    private Long costMs;

    /** 错误信息（失败时） */
    private String errorMsg;

    private LocalDateTime createTime;
}
