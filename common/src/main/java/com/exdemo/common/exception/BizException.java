package com.exdemo.common.exception;

import lombok.Getter;

/**
 * 业务异常
 * <p>用于服务层抛出明确的业务逻辑错误，由全局异常处理器统一捕获</p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 *   if (user == null) {
 *       throw new BizException(404, "用户不存在");
 *   }
 *   if (balance < amount) {
 *       throw new BizException("余额不足");
 *   }
 * }</pre>
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = 500;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
