package com.exdemo.admin.config;

import com.exdemo.admin.constant.AdminConstant;
import com.exdemo.admin.entity.AdminUser;
import com.exdemo.admin.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminDataInitializer implements ApplicationRunner {

    private final AdminUserMapper adminUserMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (!adminProperties.isInitDefaultAccount()) {
            return;
        }
        try {
            Long count = adminUserMapper.selectCount(null);
            if (count != null && count > 0) {
                return;
            }
            // 🔴 安全红线：未显式提供初始密码时不建号。
            // 否则 bootstrapping 出一个「空密码 / 弱密码」的超级管理员，等于把后台直接开放给扫描器。
            String rawPassword = adminProperties.getDefaultPassword();
            if (rawPassword == null || rawPassword.isBlank()) {
                log.error("[初始化] 未设置默认管理员初始密码，已跳过自动建号。"
                        + "请先设置环境变量 ADMIN_INIT_PASSWORD（建议 12 位以上）后重启，或手工向 admin_user 表插入账号。");
                return;
            }

            AdminUser admin = new AdminUser();
            admin.setUsername(adminProperties.getDefaultUsername());
            admin.setPassword(passwordEncoder.encode(rawPassword));
            admin.setNickname("超级管理员");
            admin.setStatus(AdminConstant.STATUS_ENABLED);
            admin.setCreateTime(LocalDateTime.now());
            admin.setUpdateTime(LocalDateTime.now());
            adminUserMapper.insert(admin);

            // 只在日志里留账号名，密码一律不落日志——日志会被采集、会被共享，凭证不能进日志
            log.warn("[初始化] 已创建默认管理员账号 username={}，初始密码来自环境变量 ADMIN_INIT_PASSWORD，请登录后立即修改！",
                    adminProperties.getDefaultUsername());
        } catch (Exception e) {
            // 常见原因：还没执行建表脚本。给出可操作的提示，而不是让人对着堆栈猜
            log.error("[初始化] 默认管理员创建失败，请确认已执行 sql/admin-schema.sql 建表脚本", e);
        }
    }
}
