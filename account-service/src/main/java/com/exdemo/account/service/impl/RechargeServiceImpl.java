package com.exdemo.account.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.exdemo.account.config.AlipayProperties;
import com.exdemo.account.constant.RechargeStatus;
import com.exdemo.account.dto.RechargeCreateVO;
import com.exdemo.account.dto.RechargeStatusVO;
import com.exdemo.account.entity.RechargeOrder;
import com.exdemo.account.mapper.RechargeOrderMapper;
import com.exdemo.account.service.RechargePayService;
import com.exdemo.account.service.RechargeService;
import com.exdemo.common.result.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 充值业务实现
 *
 * <p>三条入账路径，最终都收敛到 {@link RechargePayService#confirmPaid}：</p>
 * <pre>
 *   ① 支付宝异步通知 (notify)   ← 主路径
 *   ② 前端轮询时主动查单        ← 本地开发/notify 不通时的救急
 *   ③ 定时对账补偿              ← 兜底
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeServiceImpl extends ServiceImpl<RechargeOrderMapper, RechargeOrder>
        implements RechargeService {

    /** 电脑网站支付的产品码（手机网站支付是 QUICK_WAP_WAY，别混） */
    private static final String PRODUCT_CODE_PC = "FAST_INSTANT_TRADE_PAY";

    /** 支付宝交易状态 */
    private static final String TRADE_SUCCESS = "TRADE_SUCCESS";
    private static final String TRADE_FINISHED = "TRADE_FINISHED";
    private static final String TRADE_CLOSED = "TRADE_CLOSED";

    /** 单笔充值上限，避免误输入巨额 */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("50000.00");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AlipayClient alipayClient;
    private final AlipayProperties props;
    private final RechargePayService rechargePayService;

    // ==================== 发起充值 ====================

    @Override
    public R<RechargeCreateVO> createRecharge(Long userId, BigDecimal amount) {
        if (userId == null || amount == null) {
            return R.badRequest("用户ID或金额不能为空");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return R.badRequest("充值金额必须大于0");
        }

        BigDecimal realAmount = amount.setScale(2, RoundingMode.DOWN);
        if (realAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return R.badRequest("充值金额过小");
        }
        if (realAmount.compareTo(MAX_AMOUNT) > 0) {
            return R.badRequest("单笔充值金额不能超过 " + MAX_AMOUNT);
        }

        String rechargeNo = generateRechargeNo();
        RechargeOrder order = new RechargeOrder();
        order.setRechargeNo(rechargeNo);
        order.setUserId(userId);
        order.setAmount(realAmount);
        order.setStatus(RechargeStatus.UNPAID);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        try {
            baseMapper.insert(order);
        } catch (Exception e) {
            log.error("[recharge] 充值单落库失败 rechargeNo={}", rechargeNo, e);
            return R.fail("创建充值单失败，请稍后重试");
        }

        try {
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            request.setNotifyUrl(props.getNotifyUrl());
            request.setReturnUrl(props.getReturnUrl());

            Map<String, Object> bizContent = new LinkedHashMap<>();
            bizContent.put("out_trade_no", rechargeNo);
            bizContent.put("total_amount", realAmount.toPlainString());
            bizContent.put("subject", "账户充值");
            bizContent.put("product_code", PRODUCT_CODE_PC);
            request.setBizContent(OBJECT_MAPPER.writeValueAsString(bizContent));

            // GET 方式：直接返回一个可跳转的 URL（POST 方式返回的是自动提交的表单 HTML）
            AlipayTradePagePayResponse response = alipayClient.pageExecute(request, "GET");
            if (!response.isSuccess()) {
                log.error("[recharge] 支付宝下单失败 rechargeNo={} subCode={} subMsg={}",
                        rechargeNo, response.getSubCode(), response.getSubMsg());

                closeQuietly(rechargeNo);
                return R.fail("支付宝下单失败：" + response.getSubMsg());
            }

            log.info("[recharge] 发起充值 rechargeNo={} userId={} amount={}",
                    rechargeNo, userId, realAmount);
            return R.ok(new RechargeCreateVO(rechargeNo, response.getBody(), realAmount));

        } catch (Exception e) {
            log.error("[recharge] 发起支付异常 rechargeNo={}", rechargeNo, e);
            closeQuietly(rechargeNo);
            return R.fail("发起支付失败：" + e.getMessage());
        }
    }

    private void closeQuietly(String rechargeNo) {
        try {
            baseMapper.markClosed(rechargeNo);
        } catch (Exception e) {
            log.warn("[recharge] 关闭充值单失败 rechargeNo={}", rechargeNo, e);
        }
    }

    private String generateRechargeNo() {
        return "RC" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    // ==================== 查询状态（含主动查单） ====================

    @Override
    public R<RechargeStatusVO> queryStatus(String rechargeNo, Long userId) {
        if (rechargeNo == null || rechargeNo.isBlank()) {
            return R.badRequest("充值单号不能为空");
        }

        RechargeOrder order = getByRechargeNo(rechargeNo);
        if (order == null) {
            return R.notFound("充值单不存在");
        }

        if (userId != null && !userId.equals(order.getUserId())) {
            return R.badRequest("无权查看该充值单");
        }

        // 仍是待支付 → 主动向支付宝查一次，能查到就立即入账
        if (RechargeStatus.UNPAID.equals(order.getStatus())) {
            syncFromAlipay(rechargeNo, order.getAmount());
            order = getByRechargeNo(rechargeNo);
        }

        return R.ok(toVO(order));
    }

    /**
     * 向支付宝查单并同步本地状态
     *
     * @return 是否已支付
     */
    private boolean syncFromAlipay(String rechargeNo, BigDecimal expectAmount) {
        try {
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            Map<String, Object> bizContent = new LinkedHashMap<>();
            bizContent.put("out_trade_no", rechargeNo);
            request.setBizContent(OBJECT_MAPPER.writeValueAsString(bizContent));

            AlipayTradeQueryResponse response = alipayClient.execute(request);

            if (!response.isSuccess()) {
                // 交易不存在是正常情况（用户还没付款），不要打成 error 污染日志
                if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) {
                    log.debug("[recharge] 支付宝侧暂无该交易 rechargeNo={}", rechargeNo);
                } else {
                    log.warn("[recharge] 查单失败 rechargeNo={} subCode={} subMsg={}",
                            rechargeNo, response.getSubCode(), response.getSubMsg());
                }
                return false;
            }

            String tradeStatus = response.getTradeStatus();
            if (TRADE_SUCCESS.equals(tradeStatus) || TRADE_FINISHED.equals(tradeStatus)) {
                BigDecimal paid = response.getTotalAmount() != null
                        ? new BigDecimal(response.getTotalAmount())
                        : expectAmount;

                return rechargePayService.confirmPaid(rechargeNo, response.getTradeNo(), paid);
            }
            if (TRADE_CLOSED.equals(tradeStatus)) {
                baseMapper.markClosed(rechargeNo);
                log.info("[recharge] 支付宝侧交易已关闭 rechargeNo={}", rechargeNo);
            }
            return false;

        } catch (AlipayApiException e) {
            log.error("[recharge] 查单异常 rechargeNo={}", rechargeNo, e);
            return false;
        } catch (Exception e) {
            log.error("[recharge] 查单处理异常 rechargeNo={}", rechargeNo, e);
            return false;
        }
    }

    // ==================== 定时对账补偿 ====================

    @Override
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void compensateUnpaidOrders() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(2);
        LocalDateTime after = LocalDateTime.now().minusHours(24);

        List<RechargeOrder> pending = lambdaQuery()
                .eq(RechargeOrder::getStatus, RechargeStatus.UNPAID)
                .lt(RechargeOrder::getCreateTime, before)
                .gt(RechargeOrder::getCreateTime, after)
                .last("LIMIT 50")
                .list();

        if (pending.isEmpty()) {
            return;
        }

        log.info("[recharge] 对账补偿开始，待处理 {} 笔", pending.size());
        for (RechargeOrder order : pending) {
            try {
                syncFromAlipay(order.getRechargeNo(), order.getAmount());
            } catch (Exception e) {

                log.error("[recharge] 补偿单笔异常 rechargeNo={}", order.getRechargeNo(), e);
            }
        }
    }

    // ==================== 私有工具 ====================

    private RechargeOrder getByRechargeNo(String rechargeNo) {
        return baseMapper.selectOne(
                new LambdaQueryWrapper<RechargeOrder>().eq(RechargeOrder::getRechargeNo, rechargeNo));
    }

    private RechargeStatusVO toVO(RechargeOrder order) {
        return new RechargeStatusVO(
                order.getRechargeNo(),
                order.getUserId(),
                order.getAmount(),
                order.getStatus(),
                order.getPayTime()
        );
    }
}
