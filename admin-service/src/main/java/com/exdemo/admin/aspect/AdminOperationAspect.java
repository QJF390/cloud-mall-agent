package com.exdemo.admin.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.exdemo.admin.annotation.AdminOperation;
import com.exdemo.admin.constant.AdminConstant;
import com.exdemo.admin.entity.SysLog;
import com.exdemo.admin.mapper.SysLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AdminOperationAspect {

    /** 数据库 error_msg 列长度上限，超长要截断，否则写入报错 */
    private static final int ERROR_MSG_MAX_LEN = 255;

    private final SysLogMapper sysLogMapper;

    @Around("@annotation(operation)")
    public Object around(ProceedingJoinPoint joinPoint, AdminOperation operation) throws Throwable {
        long start = System.currentTimeMillis();
        boolean success = true;
        String errorMsg = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            success = false;
            errorMsg = truncate(t.getMessage());
            throw t;
        } finally {
            long cost = System.currentTimeMillis() - start;
            try {
                SysLog sysLog = new SysLog();
                sysLog.setOperator(currentOperator());
                sysLog.setModule(operation.module());
                sysLog.setAction(operation.action());
                sysLog.setRequestUri(currentRequestUri());
                sysLog.setSuccess(success ? 1 : 0);
                sysLog.setCostMs(cost);
                sysLog.setErrorMsg(errorMsg);
                sysLog.setCreateTime(LocalDateTime.now());
                sysLogMapper.insert(sysLog);
            } catch (Exception e) {
                // 审计日志写入失败只告警，不影响业务结果
                log.error("[审计] 操作日志落库失败, module={}, action={}", operation.module(), operation.action(), e);
            }
        }
    }

    private String currentOperator() {
        try {
            if (StpUtil.isLogin()) {
                Object username = StpUtil.getTokenSession().get(AdminConstant.SESSION_USERNAME_KEY);
                if (username != null) {
                    return String.valueOf(username);
                }
                return StpUtil.getLoginIdAsString();
            }
        } catch (Exception ignored) {
            // 取不到就当匿名，日志本身不能反噬业务
        }
        return "ANONYMOUS";
    }

    private String currentRequestUri() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "";
        }
        HttpServletRequest request = attributes.getRequest();
        return request.getMethod() + " " + request.getRequestURI();
    }

    private String truncate(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() <= ERROR_MSG_MAX_LEN ? msg : msg.substring(0, ERROR_MSG_MAX_LEN);
    }
}
