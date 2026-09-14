package com.base.admin.aspect;

import com.base.admin.annotation.Log;
import com.base.admin.domain.entity.SysOperLog;
import com.base.admin.mapper.SysOperLogMapper;
import com.base.admin.util.SecurityUtils;
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
public class LogAspect {

    private final SysOperLogMapper operLogMapper;

    @Around("@annotation(logAnnotation)")
    public Object around(ProceedingJoinPoint joinPoint, Log logAnnotation) throws Throwable {
        long startTime = System.currentTimeMillis();
        SysOperLog operLog = new SysOperLog();
        operLog.setTitle(logAnnotation.title());
        operLog.setBusinessType(logAnnotation.businessType());
        operLog.setMethod(joinPoint.getSignature().toShortString());
        operLog.setOperName(SecurityUtils.getCurrentUsername());
        operLog.setOperTime(LocalDateTime.now());

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            operLog.setOperUrl(request.getRequestURI());
            operLog.setRequestMethod(request.getMethod());
            operLog.setOperIp(request.getRemoteAddr());
        }

        try {
            Object result = joinPoint.proceed();
            operLog.setStatus(0);
            operLog.setCostTime(System.currentTimeMillis() - startTime);
            operLogMapper.insert(operLog);
            return result;
        } catch (Throwable e) {
            operLog.setStatus(1);
            operLog.setErrorMsg(e.getMessage());
            operLog.setCostTime(System.currentTimeMillis() - startTime);
            operLogMapper.insert(operLog);
            throw e;
        }
    }
}
