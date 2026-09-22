package com.exdemo.user.Service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.exdemo.common.result.R;
import com.exdemo.user.Service.UserService;
import com.exdemo.user.dto.UserDto;
import com.exdemo.user.entity.User;
import com.exdemo.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.exdemo.common.util.ValidationUtil;

/**
 * 用户业务实现
 * TODO: 补充业务逻辑
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    @Autowired
    UserMapper userMapper;
    @Autowired
    StringRedisTemplate   stringRedisTemplate;
    @Autowired
    BCryptPasswordEncoder bCryptPasswordEncoder;

    /**
     * Redis 分布式锁释放脚本：只有锁的 value 匹配时才删除，防止误删别人的锁
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("lua/unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public R<UserDto> register(User user) {
        // ========== 参数校验 ==========
        if (user == null) {
            return R.fail("用户消息不能为空");
        }
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            return R.fail("用户名不能为空");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            return R.fail("密码不能为空");
        }
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            return R.fail("手机号不能为空");
        }
        if (!ValidationUtil.isPhone(user.getPhone())) {
            return R.fail("手机号格式不正确");
        }

        // ========== 分布式锁（按用户名加锁，防止同名并发注册） ==========
        String lockKey = "user:register:lock:" + user.getUsername();
        String lockValue = UUID.randomUUID().toString();

        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, 10, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(locked)) {
            return R.fail("系统繁忙，请稍后再试");
        }

        try {
            // ========== 校验用户名是否已存在（缓存 + DB） ==========
            String usernameCacheKey = "user:info:username:" + user.getUsername();
            if (stringRedisTemplate.hasKey(usernameCacheKey)) {
                return R.fail("该用户名已经注册过了");
            }
            if (lambdaQuery().eq(User::getUsername, user.getUsername()).one() != null) {
                return R.fail("该用户名已经注册过了");
            }

            // ========== 校验手机号是否已存在（缓存 + DB） ==========
            String phoneCacheKey = "user:info:phone:" + user.getPhone();
            if (stringRedisTemplate.hasKey(phoneCacheKey)) {
                return R.fail("该手机号已经注册过了");
            }
            if (lambdaQuery().eq(User::getPhone, user.getPhone()).one() != null) {
                return R.fail("该手机号已经注册过了");
            }

            // ========== 创建用户 ==========
            user.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
            save(user);
            String jsonUser = JSONUtil.toJsonStr(user);

            // ========== 写入缓存：用户名 + 手机号 双索引 ==========
            stringRedisTemplate.opsForValue().set(usernameCacheKey, jsonUser, 30, TimeUnit.MINUTES);
            stringRedisTemplate.opsForValue().set(phoneCacheKey, jsonUser, 30, TimeUnit.MINUTES);

            StpUtil.login(user.getId());
            String token = StpUtil.getTokenValue();
            UserDto userDto = new UserDto(user.getId(), user.getUsername(), token, user.getCreateTime(), user.getUpdateTime());
            return R.ok("注册成功", userDto);

        } finally {
            stringRedisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), lockValue);
        }
    }

    @Override
    public R<UserDto> login(String username, String password) {
        // 参数校验
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return R.fail("用户名或密码不能为空");
        }

        // 1. 先查失败次数（>=5 直接拦截，不查 DB，不跑 BCrypt）
        String failKey = "user:login:fail:" + username;
        String failCountStr = stringRedisTemplate.opsForValue().get(failKey);
        int failCount = failCountStr != null ? Integer.parseInt(failCountStr) : 0;
        if (failCount >= 5) {
            Long ttl = stringRedisTemplate.getExpire(failKey, TimeUnit.MINUTES);
            return R.fail("账户已锁定，请 " + (ttl != null ? ttl : 15) + " 分钟后再试");
        }

        // 2. 获取用户信息（先缓存，再 DB）
        String cacheKey = "user:info:username:" + username;
        String cacheValue = stringRedisTemplate.opsForValue().get(cacheKey);
        User user;

        if (cacheValue != null) {
            user = JSONUtil.toBean(cacheValue, User.class);
        } else {
            user = lambdaQuery().eq(User::getUsername, username).one();
            if (user == null) {
                return R.fail("用户不存在");
            }
            // 写回缓存
            stringRedisTemplate.opsForValue().set(cacheKey,
                    JSONUtil.toJsonStr(user), 30, TimeUnit.MINUTES);
        }

        // 3. 校验密码
        if (!bCryptPasswordEncoder.matches(password, user.getPassword())) {
            // 密码错误：INCR 失败计数，首次时设15分钟过期
            Long newCount = stringRedisTemplate.opsForValue().increment(failKey);
            if (newCount != null && newCount == 1) {
                stringRedisTemplate.expire(failKey, 15, TimeUnit.MINUTES);
            }
            int remain = 5 - newCount.intValue();
            return R.fail("密码错误，还剩 " + Math.max(0, remain) + " 次机会");
        }

        // 4. 登录成功：清除失败计数
        stringRedisTemplate.delete(failKey);

        // 4.5 状态校验：后台封禁的账号密码再对也不放行
        if (Integer.valueOf(0).equals(user.getStatus())) {
            return R.fail("账号已被禁用，请联系管理员");
        }

        // 5. Sa-Token 登录，返回脱敏信息
        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();
        UserDto userDto = new UserDto(user.getId(), user.getUsername(), token, user.getCreateTime(), user.getUpdateTime());
        return R.ok("登录成功", userDto);
    }

    @Override
    public User getById(Long id) {
//        先对传入的 id 进行判空
        if (id==null){
            return null;
        }
        return userMapper.selectById(id);
    }

    @Override
    public R<UserDto> currentProfile() {
        // 身份来源只有一个：当前 token 背后的 loginId。
        // 绝不能写成 currentProfile(Long userId) —— 那等于允许任何登录用户改个参数
        // 就能查到别人资料（典型的水平越权，IDOR）。「我是谁」这个问题，答案只能由服务端给。
        long loginId = StpUtil.getLoginIdAsLong();

        User user = userMapper.selectById(loginId);
        if (user == null) {
            // token 有效但账号没了（后台清库 / 数据修复常见于测试环境）
            return R.fail("账号不存在或已被注销");
        }
        if (Integer.valueOf(0).equals(user.getStatus())) {
            // 封禁也必须在这里挡：token 是在封禁前签发的，还活着且能通过网关校验。
            // 只在 login 时校验 = 已登录的违规账号可以一直用到 token 过期。
            return R.fail("账号已被禁用，请联系管理员");
        }

        // 刻意不下发 token：本接口的职责是「回答我是谁」，不是「签发凭证」。
        // 混在一起就会重蹈 register 的覆辙（把请求里的旧凭证当成新身份发出去）。
        UserDto userDto = new UserDto(user.getId(), user.getUsername(), null,
                user.getCreateTime(), user.getUpdateTime());
        return R.ok(userDto);
    }

    @Override
    public List<User> listUsers() {
        return userMapper.selectList(null);
    }

    /**
     * 实现发送验证码*
     * @param phone
     *
     * **/
    @Override
    public R<Void> sendSmsCode(String phone) {
//        格式校验
        if (!ValidationUtil.isPhone(phone)) {
            return R.fail("手机号格式不正确");
        }
//        先查缓存，未命中再查 DB 并写回缓存（减轻数据库压力）
        String phoneCacheKey = "user:info:phone:" + phone;
        String cachedUser = stringRedisTemplate.opsForValue().get(phoneCacheKey);
        if (cachedUser == null) {
            User existUser = lambdaQuery().eq(User::getPhone, phone).one();
            if (existUser == null) {
                return R.fail("该手机号未注册，请先注册账号");
            }
            stringRedisTemplate.opsForValue().set(phoneCacheKey, JSONUtil.toJsonStr(existUser), 30, TimeUnit.MINUTES);
        }
//        防重复发送
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey("sms:code:" + phone))) {
            return R.fail("验证码已发送，请勿重复获取");
        }
        String codeKey = "sms:code:" + phone;
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(codeKey, code, 5, TimeUnit.MINUTES);
        System.out.println("发送验证码：" + phone + " " + code);
        return R.ok("验证码已发送", null);
    }

    @Override
    public R<UserDto> loginByPhone(String phone, String code) {
//        格式校验
        if (!ValidationUtil.isPhone(phone)) {
            return R.fail("手机号格式不正确");
        }
        // 查失败次数
        String failKey = "sms:fail:" + phone;
        String failCountStr = stringRedisTemplate.opsForValue().get(failKey);
        int failCount = failCountStr != null ? Integer.parseInt(failCountStr) : 0;
        if (failCount >= 5) {
            Long ttl = stringRedisTemplate.getExpire(failKey, TimeUnit.MINUTES);
            return R.fail("验证码错误次数过多，请 " + (ttl != null ? ttl : 5) + " 分钟后再试");
        }
//        校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get("sms:code:" + phone);
        if (cacheCode == null) {
            return R.fail("验证码已过期");
        }
        if (!cacheCode.equals(code)) {
            Long newCount = stringRedisTemplate.opsForValue().increment(failKey);
            if (newCount != null && newCount == 1) {
                stringRedisTemplate.expire(failKey, 5, TimeUnit.MINUTES);
            }
            int remain = 5 - newCount.intValue();
            return R.fail("验证码错误，还剩 " + Math.max(0, remain) + " 次机会");
        }
        // 验证码通过，删掉验证码防止重用
        stringRedisTemplate.delete("sms:code:" + phone);
        stringRedisTemplate.delete(failKey);
//        查缓存获取用户信息，未命中再查 DB
        String phoneCacheKey = "user:info:phone:" + phone;
        String cachedUser = stringRedisTemplate.opsForValue().get(phoneCacheKey);
        User user;
        if (cachedUser != null) {
            user = JSONUtil.toBean(cachedUser, User.class);
        } else {
            user = lambdaQuery().eq(User::getPhone, phone).one();
            if (user == null) {
                return R.fail("该手机号未注册");
            }
            stringRedisTemplate.opsForValue().set(phoneCacheKey, JSONUtil.toJsonStr(user), 30, TimeUnit.MINUTES);
        }
        // 状态校验：后台封禁的账号即使验证码正确也不放行
        if (Integer.valueOf(0).equals(user.getStatus())) {
            return R.fail("账号已被禁用，请联系管理员");
        }
        StpUtil.login(user.getId());
//        登录成功后获取 token
        String token = StpUtil.getTokenValue();
        UserDto userDto = new UserDto(user.getId(), user.getUsername(), token, user.getCreateTime(), user.getUpdateTime());
        return R.ok("登录成功", userDto);
    }

    // ========== 管理端 ==========

    @Override
    public R<Boolean> updateStatus(Long id, Integer status) {
        if (id == null || id <= 0) {
            return R.fail("用户ID不能为空");
        }
        if (status == null || (status != 1 && status != 0)) {
            return R.fail("状态只能是 1（正常）或 0（禁用）");
        }
        User existing = userMapper.selectById(id);
        if (existing == null) {
            return R.fail("用户不存在");
        }
        // 幂等：状态没变直接返回成功，避免无谓的写库与缓存清理
        if (status.equals(existing.getStatus())) {
            return R.ok(status == 1 ? "用户已是正常状态" : "用户已是禁用状态", true);
        }
        boolean updated = update(Wrappers.<User>lambdaUpdate()
                .eq(User::getId, id)
                .set(User::getStatus, status)
                .set(User::getUpdateTime, LocalDateTime.now()));
        if (!updated) {
            return R.fail("用户状态更新失败");
        }
        evictUserCache(existing);
        return R.ok(status == 1 ? "用户已启用" : "用户已禁用", true);
    }

    /** 清理用户缓存（用户名 / 手机号双索引） */
    private void evictUserCache(User user) {
        if (user == null) {
            return;
        }
        if (user.getUsername() != null) {
            stringRedisTemplate.delete("user:info:username:" + user.getUsername());
        }
        if (user.getPhone() != null) {
            stringRedisTemplate.delete("user:info:phone:" + user.getPhone());
        }
    }
}
