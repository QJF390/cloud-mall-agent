package com.exdemo.admin.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exdemo.admin.config.AdminProperties;
import com.exdemo.admin.constant.AdminConstant;
import com.exdemo.admin.dto.AdminLoginRequest;
import com.exdemo.admin.entity.AdminUser;
import com.exdemo.admin.entity.SysRole;
import com.exdemo.admin.mapper.AdminUserMapper;
import com.exdemo.admin.mapper.SysRoleMapper;
import com.exdemo.admin.service.AdminAuthService;
import com.exdemo.common.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 管理端认证服务实现。
 *
 * <p>登录链路：查库 → 校验状态 → BCrypt 校验密码 → Sa-Token 登录 → 写入角色到 Token-Session。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final String FAIL_KEY_PREFIX = "admin:login:fail:";

    private final AdminUserMapper adminUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;
    private final AdminProperties adminProperties;

    @Override
    public R<Map<String, Object>> login(AdminLoginRequest request) {
        String username = request.getUsername().trim();
        String password = request.getPassword();

        // ====== 1. 防暴力破解：先看失败次数，超限直接拒绝（不查库、不跑 BCrypt，省资源） ======
        String failKey = FAIL_KEY_PREFIX + username;
        if (currentFailCount(failKey) >= AdminConstant.LOGIN_MAX_FAIL) {
            Long ttl = stringRedisTemplate.getExpire(failKey, TimeUnit.MINUTES);
            long remain = (ttl == null || ttl <= 0) ? AdminConstant.LOGIN_LOCK_MINUTES : ttl;
            return R.fail("账号已锁定，请 " + remain + " 分钟后再试");
        }

        // ====== 2. 查管理员 ======
        AdminUser admin = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, username));

        // 统一返回「用户名或密码错误」，不要提示"用户不存在"，避免被枚举账号
        if (admin == null) {
            recordFail(failKey);
            return R.fail("用户名或密码错误");
        }
        if (!Integer.valueOf(AdminConstant.STATUS_ENABLED).equals(admin.getStatus())) {
            return R.fail("账号已被禁用，请联系超级管理员");
        }

        // ====== 3. BCrypt 校验密码（自带盐，禁止明文/摘要比较） ======
        if (!passwordEncoder.matches(password, admin.getPassword())) {
            int remain = recordFail(failKey);
            return R.fail("用户名或密码错误，还剩 " + remain + " 次机会");
        }

        // ====== 4. 登录成功：清失败计数、下发 token ======
        stringRedisTemplate.delete(failKey);

        // 登录 ID 加 ADMIN: 前缀，与 C 端用户会话空间隔离（详见 AdminConstant）
        StpUtil.login(AdminConstant.ADMIN_LOGIN_PREFIX + admin.getId());
        String token = StpUtil.getTokenValue();

        String roleCode = resolveRoleCode(admin);
        // 用 Token-Session（按 token 隔离）而不是 Session（按 loginId 隔离）
        StpUtil.getTokenSession().set(AdminConstant.SESSION_ROLE_KEY, roleCode);
        StpUtil.getTokenSession().set(AdminConstant.SESSION_USERNAME_KEY, admin.getUsername());

        // 记录最后登录时间（MyBatis-Plus 默认只更新非 null 字段，这里只写两列）
        AdminUser update = new AdminUser();
        update.setId(admin.getId());
        update.setLastLoginTime(LocalDateTime.now());
        adminUserMapper.updateById(update);

        log.info("[管理端登录成功] username={}, role={}", admin.getUsername(), roleCode);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("id", admin.getId());
        data.put("username", admin.getUsername());
        data.put("nickname", admin.getNickname());
        data.put("roleCode", roleCode);
        return R.ok("登录成功", data);
    }

    @Override
    public R<Void> logout() {
        if (StpUtil.isLogin()) {
            StpUtil.logout();
        }
        return R.ok();
    }

    @Override
    public R<Map<String, Object>> currentUser() {
        // 拦截器保证到这里一定是"已登录的管理员"，但仍做一次防御性判断
        Object role = StpUtil.getTokenSession().get(AdminConstant.SESSION_ROLE_KEY);
        if (role == null) {
            return R.fail(403, "当前账号不是管理员");
        }

        Long adminId = parseAdminId(StpUtil.getLoginIdAsString());
        AdminUser admin = adminId == null ? null : adminUserMapper.selectById(adminId);
        if (admin == null) {
            return R.fail(401, "管理员账号不存在或已被删除");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", admin.getId());
        data.put("username", admin.getUsername());
        data.put("nickname", admin.getNickname());
        data.put("roleId", admin.getRoleId());
        data.put("roleCode", role);
        return R.ok(data);
    }

    // ==================== 私有方法 ====================

    /** 解析角色编码：优先用管理员绑定的角色，未绑定则用配置的兜底角色 */
    private String resolveRoleCode(AdminUser admin) {
        if (admin.getRoleId() != null) {
            SysRole role = sysRoleMapper.selectById(admin.getRoleId());
            if (role != null && role.getRoleCode() != null && !role.getRoleCode().isBlank()) {
                return role.getRoleCode();
            }
        }
        return adminProperties.getDefaultRole();
    }

    /** 从 "ADMIN:{id}" 中还原管理员主键 */
    private Long parseAdminId(String loginId) {
        if (loginId == null || !loginId.startsWith(AdminConstant.ADMIN_LOGIN_PREFIX)) {
            return null;
        }
        try {
            return Long.valueOf(loginId.substring(AdminConstant.ADMIN_LOGIN_PREFIX.length()));
        } catch (NumberFormatException e) {
            log.warn("[管理端] 非法登录 ID: {}", loginId);
            return null;
        }
    }

    private int currentFailCount(String failKey) {
        String value = stringRedisTemplate.opsForValue().get(failKey);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 累加失败次数，返回剩余可尝试次数；首次失败时设置过期时间 */
    private int recordFail(String failKey) {
        Long count = stringRedisTemplate.opsForValue().increment(failKey);
        long current = (count == null) ? 1 : count;
        if (current == 1) {
            stringRedisTemplate.expire(failKey, AdminConstant.LOGIN_LOCK_MINUTES, TimeUnit.MINUTES);
        }
        return (int) Math.max(0, AdminConstant.LOGIN_MAX_FAIL - current);
    }
}
