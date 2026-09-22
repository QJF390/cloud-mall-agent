package com.exdemo.admin.service.impl;

import com.exdemo.admin.dto.OrderDTO;
import com.exdemo.admin.dto.PageQuery;
import com.exdemo.admin.dto.PageResult;
import com.exdemo.admin.feign.OrderFeignClient;
import com.exdemo.admin.service.AdminOrderService;
import com.exdemo.admin.util.PageUtil;
import com.exdemo.admin.util.TimeUtil;
import com.exdemo.common.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOrderServiceImpl implements AdminOrderService {

    private final OrderFeignClient orderFeignClient;

    @Override
    public R<PageResult<OrderDTO>> pageOrders(PageQuery query) {
        // 状态过滤放在下游（减少不必要的数据传输），关键词/时间在聚合层过滤
        String statusText = trimToNull(query.getStatusText());
        R<List<OrderDTO>> remote = orderFeignClient.listAllOrders(statusText);
        if (!isOk(remote)) {
            return R.fail(remote == null ? 500 : remote.getCode(),
                    remote == null ? "订单服务返回异常" : remote.getMessage());
        }

        String keyword = trimToNull(query.getKeyword());
        LocalDateTime start = TimeUtil.parseStart(query.getStartTime());
        LocalDateTime end = TimeUtil.parseEnd(query.getEndTime());

        List<OrderDTO> filtered = remote.getData().stream()
                .filter(Objects::nonNull)
                .filter(order -> matchKeyword(order, keyword))
                .filter(order -> TimeUtil.inRange(order.getCreateTime(), start, end))
                .sorted(Comparator.comparing(OrderDTO::getCreateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        return R.ok(PageUtil.paginate(filtered, query));
    }

    @Override
    public R<OrderDTO> getOrder(Long id) {
        if (id == null || id <= 0) {
            return R.badRequest("订单ID不能为空");
        }
        R<OrderDTO> remote = orderFeignClient.getOrderById(id);
        if (!isOk(remote)) {
            return R.fail(remote == null ? 500 : remote.getCode(),
                    remote == null ? "订单服务返回异常" : remote.getMessage());
        }
        return R.ok(remote.getData());
    }

    @Override
    public R<Void> ship(Long id) {
        if (id == null || id <= 0) {
            return R.badRequest("订单ID不能为空");
        }
        // 发货无资金动作，但仍是状态机变更：合法性与幂等由 order-service 把关
        R<Boolean> remote = orderFeignClient.adminShip(id);
        return toVoid(remote, "发货");
    }

    @Override
    public R<Void> forceCancel(Long id, String reason) {
        if (id == null || id <= 0) {
            return R.badRequest("订单ID不能为空");
        }
        // 传空串而不是 null：Feign 对 required=false 的 null 参数处理存在版本差异，统一给空值最稳
        R<Boolean> remote = orderFeignClient.adminCancel(id, nullToEmpty(reason));
        return toVoid(remote, "强制关单");
    }

    @Override
    public R<Void> refund(Long id, String reason) {
        if (id == null || id <= 0) {
            return R.badRequest("订单ID不能为空");
        }
        R<Boolean> remote = orderFeignClient.adminRefund(id, nullToEmpty(reason));
        return toVoid(remote, "退款");
    }

    // ==================== 私有方法 ====================

    /** 把下游的 R<Boolean> 统一转成 R<Void>，并保留下游给出的失败原因 */
    private R<Void> toVoid(R<Boolean> remote, String action) {
        if (!isOk(remote)) {
            return R.fail(remote == null ? 500 : remote.getCode(),
                    remote == null ? "订单服务返回异常" : remote.getMessage());
        }
        if (!Boolean.TRUE.equals(remote.getData())) {
            return R.fail(action + "失败");
        }
        return R.ok(action + "成功", null);
    }

    private boolean matchKeyword(OrderDTO order, String keyword) {
        if (keyword == null) {
            return true;
        }
        String lower = keyword.toLowerCase(Locale.ROOT);
        if (containsIgnoreCase(order.getOrderNo(), lower)
                || containsIgnoreCase(order.getReceiverName(), lower)
                || containsIgnoreCase(order.getReceiverPhone(), lower)) {
            return true;
        }
        // 支持按买家ID精确查（前端提示里写了"订单号 / 买家ID"）
        return String.valueOf(order.getBuyerId()).equals(keyword);
    }

    private boolean containsIgnoreCase(String source, String lowerKeyword) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    private boolean isOk(R<?> remote) {
        return remote != null && remote.getCode() == 200 && remote.getData() != null;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
