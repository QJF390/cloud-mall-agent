package com.exdemo.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exdemo.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问层
 * TODO: 补充自定义 SQL 方法
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
