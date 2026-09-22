package com.exdemo.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "chat.kefu")
public class KefuProperties {

    /**
     * 客服账号的用户ID（t_user 里的一条真实记录，见 sql/kefu-account.sql）。
     * 默认值仅用于本地兜底，生产必须在 Nacos 覆盖。
     */
    private Long userId = 10000L;

    /** 客服展示名（会话列表 / 聊天标题） */
    private String nickname = "官方客服";
}
