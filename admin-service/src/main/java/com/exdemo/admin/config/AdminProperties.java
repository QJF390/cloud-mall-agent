package com.exdemo.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {

    /** 未绑定角色时的兜底角色编码 */
    private String defaultRole = "SUPER_ADMIN";

    /** 免登录白名单（Sa-Token 拦截器放行路径） */
    private List<String> excludePaths = new ArrayList<>();

    /** 是否在管理员表为空时初始化一个默认管理员（仅开发/首次部署使用） */
    private boolean initDefaultAccount = true;

    /** 默认管理员登录名 */
    private String defaultUsername = "admin";

    /** 默认管理员初始密码（首次登录后请立即修改） */
    private String defaultPassword = "admin123";
}
