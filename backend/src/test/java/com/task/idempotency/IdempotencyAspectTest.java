package com.task.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IdempotencyAspect 单元测试
 *
 * 覆盖场景：
 *   1) 首次请求：预留 Key → 执行业务 → 缓存结果
 *   2) 重复请求：返回缓存结果（不再执行业务）
 *   3) 业务异常：释放 Key 允许重试
 *   4) 缺少 X-Idempotency-Key 请求头：抛 400
 *
 * @author Mavis
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyAspectTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private ProceedingJoinPoint pjp;

    @Mock
    private MethodSignature signature;

    @InjectMocks
    private IdempotencyAspect aspect;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Mock HTTP 请求上下文
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Idempotency-Key")).thenReturn("test-uuid-12345");
        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        when(attributes.getRequest()).thenReturn(request);
        RequestContextHolder.setRequestAttributes(attributes);
    }

    @Test
    @DisplayName("首次请求：预留 Key + 执行业务 + 缓存结果")
    void firstRequest_shouldReserveAndCache() throws Throwable {
        // Given
        Idempotent annotation = mockAnnotation("CREATE_TASK", 86400L);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(sampleMethod());
        when(pjp.proceed()).thenReturn(Result.success("OK"));

        when(idempotencyService.extractKey(any())).thenReturn("test-uuid-12345");
        when(idempotencyService.tryReserve(eq("test-uuid-12345"), eq("CREATE_TASK"), eq(86400L)))
                .thenReturn(true);

        // When
        Object result = aspect.around(pjp, annotation);

        // Then
        assertThat(result).isNotNull();
        verify(idempotencyService).saveResult(eq("test-uuid-12345"), any(), eq(86400L));
        verify(idempotencyService, never()).release(any());
    }

    @Test
    @DisplayName("重复请求：返回缓存结果（不再执行业务）")
    void duplicateRequest_shouldReturnCached() throws Throwable {
        // Given
        Idempotent annotation = mockAnnotation("CREATE_TASK", 86400L);
        String cachedJson = objectMapper.writeValueAsString(Result.success("cached"));

        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(sampleMethod());
        when(idempotencyService.extractKey(any())).thenReturn("test-uuid-12345");
        when(idempotencyService.tryReserve(anyString(), anyString(), anyLong())).thenReturn(false);
        when(idempotencyService.getCachedResult("test-uuid-12345"))
                .thenReturn(Optional.of(cachedJson));

        // When
        Object result = aspect.around(pjp, annotation);

        // Then
        assertThat(result).isNotNull();
        // 关键：业务方法不应该被执行
        verify(pjp, never()).proceed();
    }

    @Test
    @DisplayName("业务异常：释放 Key 允许重试")
    void businessException_shouldReleaseKey() throws Throwable {
        // Given
        Idempotent annotation = mockAnnotation("CREATE_TASK", 86400L);
        when(pjp.getSignature()).thenReturn(signature);
        when(idempotencyService.extractKey(any())).thenReturn("test-uuid-12345");
        when(idempotencyService.tryReserve(anyString(), anyString(), anyLong())).thenReturn(true);
        when(pjp.proceed()).thenThrow(new RuntimeException("业务异常"));

        // When + Then
        assertThatThrownBy(() -> aspect.around(pjp, annotation))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("业务异常");

        verify(idempotencyService).release("test-uuid-12345");
        verify(idempotencyService, never()).saveResult(anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("缺少 X-Idempotency-Key 请求头：抛 400")
    void missingKeyHeader_shouldThrow400() throws Throwable {
        // Given
        Idempotent annotation = mockAnnotation("CREATE_TASK", 86400L);
        when(idempotencyService.extractKey(any()))
                .thenThrow(com.task.common.BusinessException.badRequest("缺少幂等性 Key"));

        // When + Then
        assertThatThrownBy(() -> aspect.around(pjp, annotation))
                .isInstanceOf(com.task.common.BusinessException.class);

        verify(pjp, never()).proceed();
    }

    // ============================================
    // 工具方法
    // ============================================

    private Idempotent mockAnnotation(String opType, long ttl) {
        Idempotent annotation = mock(Idempotent.class);
        when(annotation.operationType()).thenReturn(opType);
        when(annotation.ttlSeconds()).thenReturn(ttl);
        return annotation;
    }

    private Method sampleMethod() throws NoSuchMethodException {
        return SampleController.class.getMethod("createTask");
    }

    /** 测试用 Controller 桩 */
    @SuppressWarnings("unused")
    static class SampleController {
        @Idempotent(operationType = "CREATE_TASK")
        public Result<String> createTask() {
            return Result.success("OK");
        }
    }
}
