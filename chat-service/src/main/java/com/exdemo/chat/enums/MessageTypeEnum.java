package com.exdemo.chat.enums;

import lombok.Getter;

@Getter
public enum MessageTypeEnum {

    /** 纯文本 */
    TEXT(1, "文本"),
    /** 图片（URL 存 extra） */
    IMAGE(2, "图片"),
    /** 商品卡片（点击可跳转商品详情，电商 IM 高频场景） */
    PRODUCT(3, "商品卡片"),
    /** 系统通知（如「订单已发货」） */
    SYSTEM(4, "系统通知"),
    ;

    private final int code;
    private final String desc;

    MessageTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按 code 取枚举，非法值统一降级为文本。
     *
     * <p>不要抛异常：一条脏数据不该让整条消息链路失败（消息系统的可用性 &gt; 严格性）。</p>
     */
    public static MessageTypeEnum of(Integer code) {
        if (code == null) {
            return TEXT;
        }
        for (MessageTypeEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        return TEXT;
    }
}
