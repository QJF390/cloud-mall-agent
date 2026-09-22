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
            AdminUser admin = new AdminUser();
            admin.setUsername(adminProperties.getDefaultUsername());

            admin.setPassword(passwordEncoder.encode(adminProperties.getDefaultPassword()));
            admin.setNickname("超级管理员");
            admin.setStatus(AdminConstant.STATUS_ENABLED);
            admin.setCreateTime(LocalDateTime.now());
            admin.setUpdateTime(LocalDateTime.now());
            adminUserMapper.insert(admin);

            log.warn("[初始化] 已创建默认管理员账号 username={}，初始密码={}，请登录后立即修改！",
                    adminProperties.getDefaultUsername(), adminProperties.getDefaultPassword());
        } catch (Exception e) {
            // 常见原因：还没执行建表脚本。给出可操作的提示，而不是让人对着堆栈猜
            log.error("[初始化] 默认管理员创建失败，请确认已执行 sql/admin-schema.sql 建表脚本", e);
        }
    }
}
