package com.exdemo.account.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {

    /** 应用 APPID */
    private String appId;

    /** 应用私钥（PKCS8，RSA2） */
    private String merchantPrivateKey;

    /** 支付宝公钥（注意：不是应用公钥） */
    private String alipayPublicKey;

    /** 异步通知地址（支付宝服务器回调，必须公网可达） */
    private String notifyUrl;

    /** 同步跳转地址（浏览器跳回，不可信） */
    private String returnUrl;

    /** 支付完成后跳回前端的地址 */
    private String frontendUrl;

    /** 签名算法：RSA2 */
    private String signType;

    /** 编码 */
    private String charset;

    /** 网关地址（沙箱与生产不同） */
    private String gatewayUrl;
}
