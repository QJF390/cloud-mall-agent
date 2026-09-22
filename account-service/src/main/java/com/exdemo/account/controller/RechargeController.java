package com.exdemo.account.controller;

import com.exdemo.account.dto.RechargeCreateVO;
import com.exdemo.account.dto.RechargeStatusVO;
import com.exdemo.account.service.RechargeService;
import com.exdemo.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/account/recharge")
@RequiredArgsConstructor
public class RechargeController {

    private final RechargeService rechargeService;

    /**
     * 发起充值
     *
     * @return 支付页 URL，前端 window.open(payUrl) 打开
     */
    @PostMapping("/create")
    public R<RechargeCreateVO> create(@RequestParam("userId") Long userId,
                                      @RequestParam("amount") BigDecimal amount) {
        return rechargeService.createRecharge(userId, amount);
    }

    @GetMapping("/status")
    public R<RechargeStatusVO> status(@RequestParam("rechargeNo") String rechargeNo,
                                      @RequestParam("userId") Long userId) {
        return rechargeService.queryStatus(rechargeNo, userId);
    }
}
