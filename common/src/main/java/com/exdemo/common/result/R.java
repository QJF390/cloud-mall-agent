package com.exdemo.common.result;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;

/**
 * 统一响应结果封装
 * <p>所有微服务接口统一返回此格式，方便前端和网关统一处理</p>
 *
 * @param <T> 数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码（200=成功，其他=失败） */
    private int code;

    /** 提示消息 */
    private String message;

    /** 响应数据 */
    private T data;

    /** 时间戳 */
    private long timestamp;

    // ==================== 快捷工厂方法 ====================

    public static <T> R<T> ok() {
        return new R<>(200, "操作成功", null, System.currentTimeMillis());
    }

    public static <T> R<T> ok(T data) {
        return new R<>(200, "操作成功", data, System.currentTimeMillis());
    }

    public static <T> R<T> ok(String message, T data) {
        return new R<>(200, message, data, System.currentTimeMillis());
    }

    public static <T> R<T> fail(String message) {
        return new R<>(500, message, null, System.currentTimeMillis());
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null, System.currentTimeMillis());
    }

    // ==================== 常用业务状态码 ====================

    /** 参数错误 */
    public static <T> R<T> badRequest(String message) {
        return new R<>(400, message, null, System.currentTimeMillis());
    }

    /** 未授权 */
    public static <T> R<T> unauthorized(String message) {
        return new R<>(401, message, null, System.currentTimeMillis());
    }

    /** 资源不存在 */
    public static <T> R<T> notFound(String message) {
        return new R<>(404, message, null, System.currentTimeMillis());
    }
}
