package com.exdemo.admin.service;

import com.exdemo.admin.dto.OrderDTO;
import com.exdemo.admin.dto.PageQuery;
import com.exdemo.admin.dto.PageResult;
import com.exdemo.common.result.R;

/**
 * 订单管理服务（聚合 order-service）。
 */
public interface AdminOrderService {

    /** 分页查询订单（支持按状态/时间/买家筛选） */
    R<PageResult<OrderDTO>> pageOrders(PageQuery query);

    /** 订单详情 */
    R<OrderDTO> getOrder(Long id);

    /** 后台发货（已付款 → 已发货）：发货后买家侧才出现物流轨迹 */
    R<Void> ship(Long id);

    /** 后台强制关单（如超时未支付、异常订单） */
    R<Void> forceCancel(Long id, String reason);

    /** 后台介入退款 */
    R<Void> refund(Long id, String reason);
}
