package com.exdemo.account.controller;

import com.alipay.api.internal.util.AlipaySignature;
import com.exdemo.account.config.AlipayProperties;
import com.exdemo.account.service.RechargePayService;
import com.exdemo.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/alipay")
@RequiredArgsConstructor
public class AlipayCallbackController {

    private final RechargePayService rechargePayService;
    private final AlipayProperties props;

    /**
     * 异步通知：支付结果的<b>唯一可信来源</b>
     *
     * <p>返回给支付宝的响应体必须是纯字符串 {@code success} 或 {@code fail}，
     * 不能是 JSON —— 支付宝只认这两个词，返回别的会判定为通知失败并持续重发。</p>
     */
    @PostMapping("/notify")
    public String notify(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) ->
                params.put(key, values != null && values.length > 0 ? values[0] : ""));

        log.info("[alipay] 收到异步通知 outTradeNo={} tradeStatus={}",
                params.get("out_trade_no"), params.get("trade_status"));

        boolean signVerified;
        try {
            signVerified = AlipaySignature.rsaCheckV1(
                    params, props.getAlipayPublicKey(), props.getCharset(), props.getSignType());
        } catch (Exception e) {
            log.error("[alipay] 验签异常，拒绝处理 outTradeNo={}", params.get("out_trade_no"), e);
            return "fail";
        }
        if (!signVerified) {
            log.error("[alipay] 验签失败，疑似伪造请求 outTradeNo={}", params.get("out_trade_no"));
            return "fail";
        }

        // ── ② 校验 app_id：确认这笔是发给"我的应用"的，不是别人的应用打到我的回调 ──
        if (!props.getAppId().equals(params.get("app_id"))) {
            log.error("[alipay] app_id 不匹配，拒绝处理 appId={}", params.get("app_id"));
            return "fail";
        }

        String tradeStatus = params.get("trade_status");

        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            log.info("[alipay] 非支付成功状态，忽略 tradeStatus={}", tradeStatus);
            return "success";
        }

        // ── ④ 入账（幂等在 RechargePayService 内部保证） ──
        String rechargeNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        try {
            BigDecimal paidAmount = new BigDecimal(params.get("total_amount"));
            boolean ok = rechargePayService.confirmPaid(rechargeNo, tradeNo, paidAmount);

            return ok ? "success" : "fail";
        } catch (Exception e) {
            log.error("[alipay] 入账处理异常，返回 fail 等待重试 rechargeNo={}", rechargeNo, e);
            return "fail";
        }
    }

    @GetMapping("/return")
    public void returnUrl(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String rechargeNo = request.getParameter("out_trade_no");
        log.info("[alipay] 同步跳转回来 rechargeNo={}", rechargeNo);

        // 只做重定向，不做任何金额/状态判断
        String target = props.getFrontendUrl() + "?rechargeNo=" + rechargeNo;
        response.sendRedirect(target);
    }
}
