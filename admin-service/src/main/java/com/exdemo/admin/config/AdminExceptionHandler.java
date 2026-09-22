package com.exdemo.admin.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.SaTokenException;
import com.exdemo.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 管理端异常兜底。
 *
 * <p>拦截器抛出的 Sa-Token 异常如果不处理，会被通用 {@code Exception} 处理器捕获成
 * 500「服务器内部错误」，前端就永远看不到「未登录/无权限」这两个关键语义。</p>
 *
 * <p>这里必须声明比 {@code Exception} 更具体的异常类型，
 * Spring 会按"最具体匹配"选择处理器，从而覆盖 common 模块的兜底逻辑。</p>
 */
@Slf4j
@RestControllerAdvice
public class AdminExceptionHandler {

    /** 未登录 / token 失效 */
    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLogin(NotLoginException e) {
        log.warn("管理端未登录访问: {}", e.getMessage());
        return R.unauthorized("登录已失效，请重新登录");
    }

    /** 无权限（角色不足）等 Sa-Token 业务异常 */
    @ExceptionHandler(SaTokenException.class)
    public R<Void> handleSaToken(SaTokenException e) {
        // getCode() 在不同 Sa-Token 版本里可能是 int / Integer，统一用包装类型接收更安全
        Integer code = e.getCode();
        log.warn("管理端鉴权失败: code={}, msg={}", code, e.getMessage());
        return R.fail(code == null ? 403 : code, e.getMessage());
    }

    /** 参数不合法（业务代码主动抛出） */
    @ExceptionHandler(IllegalArgumentException.class)
    public R<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("管理端参数校验失败: {}", e.getMessage());
        return R.badRequest(e.getMessage());
    }

    /**
     * 上传文件超过限制。
     * <p>这个异常由 Spring 在进入 Controller 之前抛出，业务代码根本没机会处理，
     * 不显式接住就会变成 500「服务器内部错误」——运营看到这句话完全不知道该干嘛。</p>
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制: {}", e.getMessage());
        return R.badRequest("图片过大，请压缩后重新上传");
    }

    /** 落盘 IO 异常（磁盘满/无写权限等），属服务端问题，但也要给前端一个可读提示 */
    @ExceptionHandler(IllegalStateException.class)
    public R<Void> handleIllegalState(IllegalStateException e) {
        log.error("管理端服务状态异常: {}", e.getMessage(), e);
        return R.fail(500, e.getMessage() == null ? "服务暂时不可用" : e.getMessage());
    }
}
