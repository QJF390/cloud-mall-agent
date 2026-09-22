package com.exdemo.admin.service.impl;

import com.exdemo.admin.constant.AdminConstant;
import com.exdemo.admin.dto.PageQuery;
import com.exdemo.admin.dto.PageResult;
import com.exdemo.admin.dto.UserDTO;
import com.exdemo.admin.feign.UserFeignClient;
import com.exdemo.admin.service.AdminUserService;
import com.exdemo.admin.util.PageUtil;
import com.exdemo.common.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserFeignClient userFeignClient;

    @Override
    public R<PageResult<UserDTO>> pageUsers(PageQuery query) {
        R<List<UserDTO>> remote = userFeignClient.listUsers();
        if (!isOk(remote)) {
            return R.fail(remote == null ? 500 : remote.getCode(),
                    remote == null ? "用户服务返回异常" : remote.getMessage());
        }

        String keyword = trimToNull(query.getKeyword());
        Integer status = query.getStatus();

        List<UserDTO> filtered = remote.getData().stream()
                .filter(Objects::nonNull)
                .filter(user -> matchKeyword(user, keyword))
                .filter(user -> status == null || status.equals(currentStatus(user)))
                // 新注册的排前面；createTime 为空的兜到最后，避免 NPE
                .sorted(Comparator.comparing(UserDTO::getCreateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        return R.ok(PageUtil.paginate(filtered, query));
    }

    @Override
    public R<UserDTO> getUser(Long id) {
        if (id == null || id <= 0) {
            return R.badRequest("用户ID不能为空");
        }
        R<UserDTO> remote = userFeignClient.getUserById(id);
        if (!isOk(remote)) {
            return R.fail(remote == null ? 500 : remote.getCode(),
                    remote == null ? "用户服务返回异常" : remote.getMessage());
        }
        if (remote.getData() == null) {
            return R.notFound("用户不存在");
        }
        return R.ok(remote.getData());
    }

    @Override
    public R<Void> disableUser(Long id) {
        return updateStatus(id, AdminConstant.STATUS_DISABLED, "禁用");
    }

    @Override
    public R<Void> enableUser(Long id) {
        return updateStatus(id, AdminConstant.STATUS_ENABLED, "启用");
    }

    // ==================== 私有方法 ====================

    private R<Void> updateStatus(Long id, int status, String action) {
        if (id == null || id <= 0) {
            return R.badRequest("用户ID不能为空");
        }
        R<Boolean> remote = userFeignClient.updateStatus(id, status);
        if (!isOk(remote)) {
            return R.fail(remote == null ? 500 : remote.getCode(),
                    remote == null ? "用户服务返回异常" : remote.getMessage());
        }
        if (!Boolean.TRUE.equals(remote.getData())) {
            return R.fail("用户" + action + "失败");
        }
        return R.ok("用户已" + action, null);
    }

    private boolean matchKeyword(UserDTO user, String keyword) {
        if (keyword == null) {
            return true;
        }
        String lower = keyword.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(user.getUsername(), lower)
                || containsIgnoreCase(user.getPhone(), lower)
                || containsIgnoreCase(user.getEmail(), lower);
    }

    private boolean containsIgnoreCase(String source, String lowerKeyword) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    /** 兼容历史数据：状态为空时视为正常 */
    private Integer currentStatus(UserDTO user) {
        return user.getStatus() == null ? AdminConstant.STATUS_ENABLED : user.getStatus();
    }

    private boolean isOk(R<?> remote) {
        return remote != null && remote.getCode() == 200 && remote.getData() != null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
