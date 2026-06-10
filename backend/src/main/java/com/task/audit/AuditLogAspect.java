package com.task.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * 审计日志切面
 *
 * 拦截标了 @AuditLog 注解的方法：
 *   1) 执行前：收集入参快照、操作人、IP、UA
 *   2) 执行方法
 *   3) 执行后：收集返回快照
 *   4) 异步写入 audit_log 表
 *
 * @author Mavis
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;

    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        // 1) 构造审计上下文
        AuditLogContext context = new AuditLogContext();
        context.setOperationType(auditLog.operationType());
        context.setResourceType(auditLog.resourceType());
        // 修复（M5）：只快照业务参数，过滤掉 HttpServletRequest 等框架对象
        context.setBeforeSnapshot(filterBusinessArgs(pjp));

        // 2) 提取操作人（来自 SecurityContext principal）
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal != null) {
            context.setOperatorId(principal.toString());
        }

        // 3) 提取 IP 和 UA
        HttpServletRequest request = currentRequest();
        if (request != null) {
            context.setIpAddress(extractIp(request));
            context.setUserAgent(request.getHeader("User-Agent"));
        }

        // 4) 执行方法
        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable e) {
            // 异常时记录异常信息
            context.setAfterSnapshot("ERROR: " + e.getMessage());
            tryResolveResourceId(pjp, auditLog, context);
            auditLogService.recordAsync(context);
            throw e;
        }

        // 5) 记录返回快照
        context.setAfterSnapshot(result);

        // 6) 解析资源 ID
        tryResolveResourceId(pjp, auditLog, context);

        // 7) 异步写入
        auditLogService.recordAsync(context);

        return result;
    }

    /**
     * 解析资源 ID（支持 SpEL 表达式）
     *
     * 修复（B5）：不再默认取第一个参数（可能是 Task 等复杂对象）
     * 改为：
     *   1) 优先用注解指定的 SpEL 表达式
     *   2) SpEL 为空时，找 @PathVariable 标注的数字参数
     *   3) 找不到就置 null（不强行猜）
     */
    private void tryResolveResourceId(ProceedingJoinPoint pjp, AuditLog auditLog, AuditLogContext context) {
        String expression = auditLog.resourceIdParam();
        if (expression == null || expression.isBlank()) {
            // 修复：扫描所有参数，找 @PathVariable 标注的数字参数
            context.setResourceId(findPathVariableId(pjp));
            return;
        }

        // SpEL 解析
        try {
            EvaluationContext ec = new StandardEvaluationContext();
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            String[] paramNames = signature.getParameterNames();
            Object[] args = pjp.getArgs();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    ec.setVariable(paramNames[i], args[i]);
                }
            }
            ec.setVariable("result", context.getAfterSnapshot());

            Expression exp = parser.parseExpression(expression);
            Object value = exp.getValue(ec);
            if (value instanceof Number n) {
                context.setResourceId(n.longValue());
            } else if (value != null) {
                try {
                    context.setResourceId(Long.parseLong(value.toString()));
                } catch (NumberFormatException ignored) {
                    // ignore
                }
            }
        } catch (Exception e) {
            log.warn("[审计] 解析 resourceId 表达式失败: expression={}, error={}",
                    expression, e.getMessage());
        }
    }

    /**
     * 扫描方法参数，找 @PathVariable 标注的数字参数
     */
    private Long findPathVariableId(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = pjp.getArgs();

        for (int i = 0; i < parameters.length && i < args.length; i++) {
            Parameter param = parameters[i];
            if (param.isAnnotationPresent(PathVariable.class)) {
                Object value = args[i];
                if (value instanceof Number n) {
                    return n.longValue();
                }
            }
        }
        return null;
    }

    /**
     * 过滤业务参数（修复 M5）
     * 只保留标了 @PathVariable / @RequestParam / @RequestBody 的参数
     * 过滤掉 HttpServletRequest / BindingResult / Model 等框架对象
     */
    private Object[] filterBusinessArgs(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = pjp.getArgs();

        java.util.List<Object> filtered = new java.util.ArrayList<>();
        for (int i = 0; i < parameters.length && i < args.length; i++) {
            Parameter param = parameters[i];
            if (param.isAnnotationPresent(PathVariable.class)
                    || param.isAnnotationPresent(RequestParam.class)
                    || param.isAnnotationPresent(RequestBody.class)) {
                filtered.add(args[i]);
            }
        }
        return filtered.toArray();
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private String extractIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            // 修复（m7）：X-Forwarded-For 格式 "client, proxy1, proxy2"
            // 取第一个（最左边）才是真实客户端 IP
            int commaIdx = ip.indexOf(',');
            if (commaIdx > 0) {
                ip = ip.substring(0, commaIdx).trim();
            }
        } else {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
