package com.exdemo.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exdemo.admin.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理员账号 Mapper。
 * <p>当前查询均可由 BaseMapper + LambdaWrapper 覆盖；后续出现复杂统计再补 @Select 或 XML。</p>
 */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {
}
