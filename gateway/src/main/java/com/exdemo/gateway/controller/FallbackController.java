package com.exdemo.gateway.controller;

import com.exdemo.common.result.R;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 熔断降级兜底接口
 * <p>当后端服务不可用时，网关返回友好的降级提示，而不是报 500 错误</p>
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/user")
    public R<Void> userFallback() {
        return R.fail(503, "用户服务暂时不可用，请稍后重试");
    }

    @RequestMapping("/fallback/product")
    public R<Void> productFallback() {
        return R.fail(503, "商品服务暂时不可用，请稍后重试");
    }

    @RequestMapping("/fallback/order")
    public R<Void> orderFallback() {
        return R.fail(503, "订单服务暂时不可用，请稍后重试");
    }

    @RequestMapping("/fallback/account")
    public R<Void> accountFallback() {
        return R.fail(503, "账户服务暂时不可用，请稍后重试");
    }
}
