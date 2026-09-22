package com.exdemo.account.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 支付宝客户端装配
 *
 * <p>{@link AlipayClient} 是线程安全的，全局单例即可，不要每次请求 new 一个。</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AlipayConfig {

    /** 自检探针串，内容随意，只要两端一致 */
    private static final String PROBE = "exdemo-alipay-key-self-check";

    private final AlipayProperties props;

    @Bean
    public AlipayClient alipayClient() {
        // 参数顺序：网关, appId, 应用私钥, 返回格式, 编码, 支付宝公钥, 签名算法
        return new DefaultAlipayClient(
                props.getGatewayUrl(),
                props.getAppId(),
                props.getMerchantPrivateKey(),
                "json",
                props.getCharset(),
                props.getAlipayPublicKey(),
                props.getSignType()
        );
    }

    @Bean
    public ApplicationRunner alipayKeySelfCheck() {
        return args -> {
            try {
                // 用 MIME 解码器：容忍 Nacos 配置里粘贴时带上换行
                PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(
                        new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(props.getMerchantPrivateKey())));
                PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(
                        new X509EncodedKeySpec(Base64.getMimeDecoder().decode(props.getAlipayPublicKey())));

                Signature signer = Signature.getInstance("SHA256withRSA");
                signer.initSign(privateKey);
                signer.update(PROBE.getBytes(StandardCharsets.UTF_8));
                byte[] signature = signer.sign();

                Signature verifier = Signature.getInstance("SHA256withRSA");
                verifier.initVerify(publicKey);
                verifier.update(PROBE.getBytes(StandardCharsets.UTF_8));

                if (verifier.verify(signature)) {
                    log.error("""
                            [alipay] 🔴 密钥配置错误：alipay-public-key 与 merchant-private-key 是【同一对】密钥。
                                     即「支付宝公钥」位置被误填成了你自己的应用公钥。
                                     后果：DefaultAlipayClient 每次 execute() 都会抛 CHECK_ALIPAY_PUBLIC_KEY_ERROR —
                                     查单 ❌  定时对账 ❌  回调验签 ❌ 全部失效；
                                     而 pageExecute() 不验签，支付链接照常生成，用户能付款 ——
                                     表现出来就是「钱付了，充值单永远是 UNPAID，余额不动」。
                                     修复：支付宝开放平台 → 沙箱控制台 → 你的应用 → 接口加签方式，
                                     复制其中的【支付宝公钥】替换 alipay-public-key，然后重启本服务。
                            """);
                } else {
                    log.info("[alipay] 密钥自检通过：支付宝公钥与应用私钥不是同一对，配置形态正常"
                            + "（注意：这只能证明没把应用公钥填到公钥位，并不代表它一定就是支付宝公钥）");
                }
            } catch (Exception e) {
                log.error("[alipay] 🔴 密钥自检无法执行：merchant-private-key / alipay-public-key "
                        + "格式异常（应为不带 PEM 头的单行 Base64），请检查配置", e);
            }
        };
    }
}
