package com.exdemo.admin.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exdemo.admin.constant.AdminConstant;
import com.exdemo.admin.dto.PageQuery;
import com.exdemo.admin.dto.PageResult;
import com.exdemo.admin.entity.AdminUser;
import com.exdemo.admin.entity.SysConfig;
import com.exdemo.admin.entity.SysLog;
import com.exdemo.admin.entity.SysRole;
import com.exdemo.admin.mapper.AdminUserMapper;
import com.exdemo.admin.mapper.SysConfigMapper;
import com.exdemo.admin.mapper.SysLogMapper;
import com.exdemo.admin.mapper.SysRoleMapper;
import com.exdemo.admin.service.AdminSystemService;
import com.exdemo.admin.util.PageUtil;
import com.exdemo.admin.util.TimeUtil;
import com.exdemo.common.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSystemServiceImpl implements AdminSystemService {

    /** 密码长度下限（弱口令是后台被拖库的头号原因） */
    private static final int PASSWORD_MIN_LEN = 6;

    /** 密码长度上限（BCrypt 只取前 72 字节，过长无意义还浪费 CPU） */
    private static final int PASSWORD_MAX_LEN = 64;

    /** 登录名长度上限 */
    private static final int USERNAME_MAX_LEN = 50;

    private final AdminUserMapper adminUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysLogMapper sysLogMapper;
    private final SysConfigMapper sysConfigMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    // ==================== 管理员账号 ====================

    @Override
    public R<PageResult<AdminUser>> pageAdmins(PageQuery query) {
        PageQuery safe = query == null ? new PageQuery() : query;
        LambdaQueryWrapper<AdminUser> wrapper = new LambdaQueryWrapper<>();

        String keyword = trimToNull(safe.getKeyword());
        if (keyword != null) {
            // 括号包裹，避免 and 条件和后面的 status 条件被 OR 优先级冲散
            wrapper.and(w -> w.like(AdminUser::getUsername, keyword)
                    .or().like(AdminUser::getNickname, keyword));
        }
        if (safe.getStatus() != null) {
            wrapper.eq(AdminUser::getStatus, safe.getStatus());
        }
        wrapper.orderByDesc(AdminUser::getCreateTime).orderByDesc(AdminUser::getId);

        IPage<AdminUser> page = adminUserMapper.selectPage(
                new Page<>(PageUtil.normalizePageNum(safe.getPageNum()),
                        PageUtil.normalizePageSize(safe.getPageSize())),
                wrapper);
        // password 字段带 @JsonProperty(WRITE_ONLY)，序列化时不会外泄
        return R.ok(PageResult.of(page));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Void> saveAdmin(AdminUser admin) {
        if (admin == null) {
            return R.badRequest("管理员信息不能为空");
        }
        String username = trimToNull(admin.getUsername());
        if (username == null) {
            return R.badRequest("登录名不能为空");
        }
        if (username.length() > USERNAME_MAX_LEN) {
            return R.badRequest("登录名不能超过 " + USERNAME_MAX_LEN + " 个字符");
        }
        admin.setUsername(username);

        return admin.getId() == null ? createAdmin(admin) : updateAdmin(admin);
    }

    private R<Void> createAdmin(AdminUser admin) {
        String password = admin.getPassword();
        if (password == null || password.isBlank()) {
            return R.badRequest("新增管理员必须设置初始密码");
        }
        R<Void> passwordCheck = validatePassword(password);
        if (passwordCheck != null) {
            return passwordCheck;
        }
        if (existsUsername(admin.getUsername(), null)) {
            return R.fail("登录名已存在");
        }

        admin.setPassword(passwordEncoder.encode(password));
        // 兜底：漏传状态时默认启用，避免 status=NULL 导致登录时被判为禁用
        if (admin.getStatus() == null) {
            admin.setStatus(AdminConstant.STATUS_ENABLED);
        }
        admin.setLastLoginTime(null);
        admin.setCreateTime(LocalDateTime.now());
        admin.setUpdateTime(LocalDateTime.now());
        adminUserMapper.insert(admin);
        log.info("[系统管理] 新增管理员 username={}", admin.getUsername());
        return R.ok("管理员创建成功", null);
    }

    private R<Void> updateAdmin(AdminUser admin) {
        AdminUser existing = adminUserMapper.selectById(admin.getId());
        if (existing == null) {
            return R.notFound("管理员不存在");
        }
        if (existsUsername(admin.getUsername(), admin.getId())) {
            return R.fail("登录名已存在");
        }

        admin.setPassword(null);
        // 禁止禁用当前登录账号，否则把自己锁在门外
        if (Integer.valueOf(AdminConstant.STATUS_DISABLED).equals(admin.getStatus())
                && admin.getId().equals(currentAdminId())) {
            return R.badRequest("不能禁用当前登录账号");
        }
        admin.setCreateTime(null);
        admin.setLastLoginTime(null);
        admin.setUpdateTime(LocalDateTime.now());
        adminUserMapper.updateById(admin);
        log.info("[系统管理] 更新管理员 id={}, username={}", admin.getId(), admin.getUsername());
        return R.ok("管理员更新成功", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Void> resetPassword(Long adminId, String newPassword) {
        if (adminId == null || adminId <= 0) {
            return R.badRequest("管理员ID不能为空");
        }
        R<Void> passwordCheck = validatePassword(newPassword);
        if (passwordCheck != null) {
            return passwordCheck;
        }
        AdminUser existing = adminUserMapper.selectById(adminId);
        if (existing == null) {
            return R.notFound("管理员不存在");
        }
        AdminUser update = new AdminUser();
        update.setId(adminId);
        update.setPassword(passwordEncoder.encode(newPassword));
        update.setUpdateTime(LocalDateTime.now());
        adminUserMapper.updateById(update);
        log.info("[系统管理] 重置管理员密码 id={}", adminId);
        return R.ok("密码重置成功", null);
    }

    // ==================== 角色 / 权限 ====================

    @Override
    public R<PageResult<SysRole>> pageRoles(PageQuery query) {
        PageQuery safe = query == null ? new PageQuery() : query;
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();

        String keyword = trimToNull(safe.getKeyword());
        if (keyword != null) {
            wrapper.and(w -> w.like(SysRole::getRoleCode, keyword)
                    .or().like(SysRole::getRoleName, keyword));
        }
        wrapper.orderByAsc(SysRole::getId);

        IPage<SysRole> page = sysRoleMapper.selectPage(
                new Page<>(PageUtil.normalizePageNum(safe.getPageNum()),
                        PageUtil.normalizePageSize(safe.getPageSize())),
                wrapper);
        return R.ok(PageResult.of(page));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Void> saveRole(SysRole role) {
        if (role == null) {
            return R.badRequest("角色信息不能为空");
        }
        String roleCode = trimToNull(role.getRoleCode());
        String roleName = trimToNull(role.getRoleName());
        if (roleCode == null) {
            return R.badRequest("角色编码不能为空");
        }
        if (roleName == null) {
            return R.badRequest("角色名称不能为空");
        }
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);

        LambdaQueryWrapper<SysRole> codeWrapper = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode);
        if (role.getId() != null) {
            codeWrapper.ne(SysRole::getId, role.getId());
        }
        Long dup = sysRoleMapper.selectCount(codeWrapper);
        if (dup != null && dup > 0) {
            return R.fail("角色编码已存在");
        }

        if (role.getId() == null) {
            role.setCreateTime(LocalDateTime.now());
            role.setUpdateTime(LocalDateTime.now());
            sysRoleMapper.insert(role);
            log.info("[系统管理] 新增角色 code={}", roleCode);
            return R.ok("角色创建成功", null);
        }
        if (sysRoleMapper.selectById(role.getId()) == null) {
            return R.notFound("角色不存在");
        }
        role.setCreateTime(null);
        role.setUpdateTime(LocalDateTime.now());
        sysRoleMapper.updateById(role);
        log.info("[系统管理] 更新角色 id={}, code={}", role.getId(), roleCode);
        return R.ok("角色更新成功", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Void> deleteRole(Long roleId) {
        if (roleId == null || roleId <= 0) {
            return R.badRequest("角色ID不能为空");
        }
        if (sysRoleMapper.selectById(roleId) == null) {
            return R.notFound("角色不存在");
        }
        Long refCount = adminUserMapper.selectCount(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getRoleId, roleId));
        if (refCount != null && refCount > 0) {
            return R.fail("该角色下仍有 " + refCount + " 个管理员，请先调整后再删除");
        }
        sysRoleMapper.deleteById(roleId);
        log.info("[系统管理] 删除角色 id={}", roleId);
        return R.ok("角色已删除", null);
    }

    // ==================== 操作日志 ====================

    @Override
    public R<PageResult<SysLog>> pageLogs(PageQuery query) {
        PageQuery safe = query == null ? new PageQuery() : query;
        LambdaQueryWrapper<SysLog> wrapper = new LambdaQueryWrapper<>();

        String keyword = trimToNull(safe.getKeyword());
        if (keyword != null) {
            wrapper.and(w -> w.like(SysLog::getOperator, keyword)
                    .or().like(SysLog::getModule, keyword)
                    .or().like(SysLog::getAction, keyword));
        }
        LocalDateTime start = TimeUtil.parseStart(safe.getStartTime());
        LocalDateTime end = TimeUtil.parseEnd(safe.getEndTime());
        if (start != null) {
            wrapper.ge(SysLog::getCreateTime, start);
        }
        if (end != null) {
            wrapper.le(SysLog::getCreateTime, end);
        }
        wrapper.orderByDesc(SysLog::getCreateTime).orderByDesc(SysLog::getId);

        IPage<SysLog> page = sysLogMapper.selectPage(
                new Page<>(PageUtil.normalizePageNum(safe.getPageNum()),
                        PageUtil.normalizePageSize(safe.getPageSize())),
                wrapper);
        return R.ok(PageResult.of(page));
    }

    // ==================== 系统配置 ====================

    @Override
    public R<List<SysConfig>> listConfigs(String group) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        String groupFilter = trimToNull(group);
        if (groupFilter != null) {
            wrapper.eq(SysConfig::getConfigGroup, groupFilter);
        }
        wrapper.orderByAsc(SysConfig::getConfigGroup).orderByAsc(SysConfig::getConfigKey);
        return R.ok(sysConfigMapper.selectList(wrapper));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Void> updateConfig(String configKey, String configValue) {
        String key = trimToNull(configKey);
        if (key == null) {
            return R.badRequest("配置键不能为空");
        }

        SysConfig existing = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (existing == null) {
            return R.notFound("配置项不存在：" + key);
        }
        if (configValue != null && configValue.length() > 1000) {
            return R.badRequest("配置值过长（上限 1000 字符）");
        }
        SysConfig update = new SysConfig();
        update.setId(existing.getId());
        update.setConfigValue(configValue == null ? "" : configValue.trim());
        update.setUpdateTime(LocalDateTime.now());
        sysConfigMapper.updateById(update);
        log.info("[系统管理] 修改配置 key={}", key);
        return R.ok("配置已保存", null);
    }

    // ==================== 私有方法 ====================

    private R<Void> validatePassword(String password) {
        if (password == null || password.isBlank()) {
            return R.badRequest("密码不能为空");
        }
        if (password.length() < PASSWORD_MIN_LEN) {
            return R.badRequest("密码长度不能少于 " + PASSWORD_MIN_LEN + " 位");
        }
        if (password.length() > PASSWORD_MAX_LEN) {
            return R.badRequest("密码长度不能超过 " + PASSWORD_MAX_LEN + " 位");
        }
        return null;
    }

    /** 登录名是否已被占用（excludeId 用于编辑时排除自身） */
    private boolean existsUsername(String username, Long excludeId) {
        LambdaQueryWrapper<AdminUser> wrapper = new LambdaQueryWrapper<AdminUser>()
                .eq(AdminUser::getUsername, username);
        if (excludeId != null) {
            wrapper.ne(AdminUser::getId, excludeId);
        }
        Long count = adminUserMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    /** 当前登录管理员的本地主键（从 "ADMIN:{id}" 还原） */
    private Long currentAdminId() {
        try {
            if (!StpUtil.isLogin()) {
                return null;
            }
            String loginId = StpUtil.getLoginIdAsString();
            if (loginId == null || !loginId.startsWith(AdminConstant.ADMIN_LOGIN_PREFIX)) {
                return null;
            }
            return Long.valueOf(loginId.substring(AdminConstant.ADMIN_LOGIN_PREFIX.length()));
        } catch (Exception e) {
            log.warn("[系统管理] 解析当前管理员ID失败", e);
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
