package com.exdemo.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exdemo.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问层
 * <p>CRUD 全部由 BaseMapper 提供；条件查询在 Service 侧用 lambdaQuery() 表达，
 * 编译期可校验字段名，比手写 SQL 更抗重构。</p>
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
