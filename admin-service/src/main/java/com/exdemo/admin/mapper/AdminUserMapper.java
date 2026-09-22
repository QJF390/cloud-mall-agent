package com.exdemo.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exdemo.admin.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理员账号 Mapper。
 * TODO: 复杂查询写 @Select 或 XML。
 */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {
}
