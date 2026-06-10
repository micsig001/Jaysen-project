package com.task.idempotency;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Optional;

/**
 * 幂等性切面
 *
 * 拦截标了 @Idempotent 注解的方法：
 *   1) 从请求头取 X-Idempotency-Key
 *   2) Redis SETNX 预留 Key
 *      - 已存在：返回缓存结果
 *      - 预留成功：执行业务，缓存结果
 *   3) 业务异常时释放 Key（允许重试）
 *
 * 使用示例：
 *   @PostMapping("/api/tasks")
 *   @Idempotent(operationType = "CREATE_TASK")
 *   public Result<Task> createTask(@RequestBody Task task) { ... }
 *
 * @author Mavis
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        // 1) 获取当前 HTTP 请求
        HttpServletRequest request = currentRequest();

        // 2) 提取并校验 X-Idempotency-Key
        String key = idempotencyService.extractKey(request);

        // 3) 尝试预留 Key
        boolean reserved = idempotencyService.tryReserve(
                key, idempotent.operationType(), idempotent.ttlSeconds());
        if (!reserved) {
            // 已存在：返回缓存结果
            Optional<String> cached = idempotencyService.getCachedResult(key);
            if (cached.isPresent()) {
                log.info("[幂等] 重复请求，返回缓存结果: Key={}, Operation={}",
                        key, idempotent.operationType());
                return deserializeResult(cached.get(), pjp);
            }
            // 没有缓存：可能是"预留了但业务还没完成"或"曾经失败释放了"
            // 修复（M8）：返回 429 让客户端知道需要重试，而不是 500
            log.warn("[幂等] Key 已被预留但无缓存: Key={}", key);
            throw com.task.common.BusinessException.status(429, "请求正在处理中，请稍后重试");
        }

        // 4) 预留成功：执行业务
        try {
            Object result = pjp.proceed();
            // 5) 业务成功：缓存结果
            idempotencyService.saveResult(key, result, idempotent.ttlSeconds());
            return result;
        } catch (Throwable e) {
            // 6) 业务异常：释放 Key，允许重试
            log.warn("[幂等] 业务异常，释放 Key 允许重试: Key={}, Error={}", key, e.getMessage());
            idempotencyService.release(key);
            throw e;
        }
    }

    /**
     * 反序列化缓存的 JSON 结果为目标方法返回类型
     * 修复（B4）：使用 method.getGenericReturnType() 保留泛型信息
     * 避免 Result<Task> 反序列化为 Result<LinkedHashMap> 的问题
     */
    private Object deserializeResult(String json, ProceedingJoinPoint pjp) {
        try {
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            Method method = signature.getMethod();
            Type genericReturnType = method.getGenericReturnType();

            // 用 Jackson TypeFactory 构造类型，保留泛型
            com.fasterxml.jackson.databind.JavaType javaType =
                    objectMapper.getTypeFactory().constructType(genericReturnType);
            return objectMapper.readValue(json, javaType);
        } catch (Exception e) {
            log.error("[幂等] 反序列化缓存结果失败", e);
            throw new RuntimeException("幂等性结果反序列化失败", e);
        }
    }

    /**
     * 获取当前线程的 HTTP 请求
     */
    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new IllegalStateException("幂等性切面必须在 HTTP 请求上下文中执行");
        }
        return attributes.getRequest();
    }
}
