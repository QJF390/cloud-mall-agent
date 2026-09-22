package com.exdemo.admin.service;

import com.exdemo.admin.dto.PageQuery;
import com.exdemo.admin.dto.PageResult;
import com.exdemo.admin.dto.UserDTO;
import com.exdemo.common.result.R;

public interface AdminUserService {

    /** 分页查询用户 */
    R<PageResult<UserDTO>> pageUsers(PageQuery query);

    /** 用户详情 */
    R<UserDTO> getUser(Long id);

    /** 禁用用户 */
    R<Void> disableUser(Long id);

    /** 启用用户 */
    R<Void> enableUser(Long id);
}
