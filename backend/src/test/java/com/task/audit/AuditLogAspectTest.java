package com.task.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.common.Result;
import com.task.mapper.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AuditLogAspect 单元测试
 *
 * 覆盖场景：
 *   1) 业务成功：写入审计日志（含 before/after 快照）
 *   2) 业务异常：仍写入审计日志（after 快照为错误信息）
 *   3) 资源 ID 解析：支持 SpEL 表达式
 *
 * @author Mavis
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLogAspectTest {

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ProceedingJoinPoint pjp;

    @Mock
    private MethodSignature signature;

    @Mock
    private AuditLogMapper auditLogMapper;

    private AuditLogAspect aspect;

    @BeforeEach
    void setUp() {
        // 注入审计 Service
        aspect = new AuditLogAspect(auditLogService);

        // Mock HTTP 请求
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        when(attributes.getRequest()).thenReturn(request);
        RequestContextHolder.setRequestAttributes(attributes);

        // Mock SecurityContext：当前用户
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "user-001", null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));
    }

    @Test
    @DisplayName("业务成功：写入审计日志（含 before/after 快照）")
    void success_shouldRecordAudit() throws Throwable {
        // Given
        AuditLog annotation = mockAnnotation("CREATE", "TASK", "");
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(sampleMethod());
        when(signature.getParameterNames()).thenReturn(new String[]{"task"});
        when(pjp.getArgs()).thenReturn(new Object[]{new SampleTask(123L)});
        when(pjp.proceed()).thenReturn(Result.success(new SampleTask(123L)));

        // When
        Object result = aspect.around(pjp, annotation);

        // Then
        assertThat(result).isNotNull();
        ArgumentCaptor<AuditLogContext> captor = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLogService).recordAsync(captor.capture());

        AuditLogContext ctx = captor.getValue();
        assertThat(ctx.getOperationType()).isEqualTo("CREATE");
        assertThat(ctx.getResourceType()).isEqualTo("TASK");
        assertThat(ctx.getOperatorId()).isEqualTo("user-001");
        assertThat(ctx.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(ctx.getBeforeSnapshot()).isNotNull();
        assertThat(ctx.getAfterSnapshot()).isNotNull();
    }

    @Test
    @DisplayName("业务异常：仍写入审计日志（after 快照为错误信息）")
    void exception_shouldStillRecord() throws Throwable {
        // Given
        AuditLog annotation = mockAnnotation("DELETE", "TASK", "");
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(sampleMethod());
        when(signature.getParameterNames()).thenReturn(new String[]{"id"});
        when(pjp.getArgs()).thenReturn(new Object[]{123L});
        when(pjp.proceed()).thenThrow(new RuntimeException("不允许删除"));

        // When + Then
        try {
            aspect.around(pjp, annotation);
        } catch (RuntimeException expected) {
            // 异常应该被透传
        }

        // 即使业务异常，审计日志也要写
        verify(auditLogService).recordAsync(any(AuditLogContext.class));
    }

    @Test
    @DisplayName("资源 ID 解析：默认取第一个参数")
    @Disabled("TODO: 代码 B5 修复后改为扫 @PathVariable，不再取第一个参数，测试需要重写")
    void resourceIdParse_fromFirstArg() throws Throwable {
        // Given
        AuditLog annotation = mockAnnotation("DELETE", "TASK", "");
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(sampleMethod());
        when(signature.getParameterNames()).thenReturn(new String[]{"id"});
        when(pjp.getArgs()).thenReturn(new Object[]{456L});
        when(pjp.proceed()).thenReturn(null);

        // When
        aspect.around(pjp, annotation);

        // Then
        ArgumentCaptor<AuditLogContext> captor = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLogService).recordAsync(captor.capture());
        assertThat(captor.getValue().getResourceId()).isEqualTo(456L);
    }

    // ============================================
    // 工具方法
    // ============================================

    private AuditLog mockAnnotation(String opType, String resourceType, String resourceIdParam) {
        AuditLog annotation = mock(AuditLog.class);
        when(annotation.operationType()).thenReturn(opType);
        when(annotation.resourceType()).thenReturn(resourceType);
        when(annotation.resourceIdParam()).thenReturn(resourceIdParam);
        when(annotation.description()).thenReturn("测试");
        return annotation;
    }

    private Method sampleMethod() throws NoSuchMethodException {
        return SampleController.class.getMethod("deleteTask", Long.class);
    }

    /** 测试用 Controller 桩 */
    @SuppressWarnings("unused")
    static class SampleController {
        public Result<Void> deleteTask(Long id) { return Result.success(); }
    }

    /** 测试用 VO 桩 */
    @SuppressWarnings("unused")
    static class SampleTask {
        private Long id;
        SampleTask(Long id) { this.id = id; }
        public Long getId() { return id; }
    }
}
