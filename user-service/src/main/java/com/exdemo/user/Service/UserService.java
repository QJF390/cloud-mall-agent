package com.exdemo.user.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.exdemo.common.result.R;
import com.exdemo.user.dto.UserDto;
import com.exdemo.user.entity.User;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户业务接口
 * TODO: 补充业务方法
 */
@Service
public interface UserService extends IService<User> {
    R<UserDto> register(User user);       // 注册
    R<UserDto> login(String username, String password);  // 登录
    User getById(Long id);             // 查单用户
    List<User> listUsers();            // 查列表

    R<UserDto> currentProfile();

    // ========== 手机验证码登录 ==========
    R<Void> sendSmsCode(String phone);                        // 发送验证码
    R<UserDto> loginByPhone(String phone, String code);       // 验证码登录

    // ========== 管理端 ==========
    /** 修改用户状态（1-正常 0-禁用），供 admin-service 聚合调用 */
    R<Boolean> updateStatus(Long id, Integer status);
}
