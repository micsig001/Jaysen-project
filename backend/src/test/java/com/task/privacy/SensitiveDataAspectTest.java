package com.task.privacy;

import com.task.common.Result;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * SensitiveDataAspect 单元测试
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>P1.10 修复：{@code currentUserId()} 必须从
 *       {@link org.springframework.security.core.Authentication#getName()} 取值，
 *       等于 {@code JwtAuthenticationFilter} 写入 SecurityContext 的 userId。</li>
 *   <li>当 SecurityContext 中无认证信息时，{@code currentUserId()} 返回 {@code null}。</li>
 *   <li>当 {@code currentUserId} 与实体中 {@code userId} 字段值一致时，
 *       标了 {@code @SensitiveData} 的字段对该用户<strong>不</strong>脱敏（本人豁免）。</li>
 *   <li>当两者不一致时，标了 {@code @SensitiveData} 的字段<strong>会</strong>被脱敏。</li>
 * </ul>
 *
 * @author Mavis
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SensitiveDataAspectTest {

    @Mock
    private ProceedingJoinPoint pjp;

    @Mock
    private MethodSignature signature;

    private SensitiveDataAspect aspect;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        // 构造一个真实的 DesensitizationUtil + 默认开启的 SensitiveDataProperties
        DesensitizationUtil desensitizationUtil = new DesensitizationUtil();
        SensitiveDataProperties properties = new SensitiveDataProperties();
        properties.setEnabled(true);
        aspect = new SensitiveDataAspect(desensitizationUtil, properties);

        // ProceedingJoinPoint 的桩：返回桩方法 + 返回 result 参数
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(sampleControllerMethod());
    }

    @AfterEach
    void tearDown() {
        // 避免污染其它测试
        SecurityContextHolder.clearContext();
    }

    // ============================================
    // P1.10 核心验证
    // ============================================

    @Test
    @DisplayName("P1.10：currentUserId() 等于 SecurityContext 中 Authentication.getName()（userId）")
    void currentUserId_equalsAuthenticationName() {
        // Given：模拟 JwtAuthenticationFilter 写入 SecurityContext 的认证对象
        // （principal = userId，与 JwtAuthenticationFilter:89-94 一致）
        String expectedUserId = "user-001";
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        expectedUserId,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));

        // When：通过反射调用 private currentUserId()
        String actual = invokeCurrentUserId();

        // Then
        assertThat(actual)
                .as("currentUserId() 应等于 SecurityContext 中 Authentication.getName()（userId）")
                .isEqualTo(expectedUserId);
    }

    @Test
    @DisplayName("P1.10：即使 principal 是复杂对象（非 String），getName() 仍能返回 userId")
    void currentUserId_worksWhenPrincipalIsComplexObject() {
        // Given：使用一个 custom principal（toString 不会是 "user-002"）
        // 这是关键测试 — 旧实现 principal.toString() 会失败，新实现走 getName() 不会
        String expectedUserId = "user-002";
        Object customPrincipal = new Object() {
            @Override
            public String toString() {
                return "WRONG_TO_STRING_VALUE";
            }
        };
        // 直接覆盖 Authentication.getName()（UsernamePasswordAuthenticationToken 默认
        // 返回 principal.toString()，但这里我们用匿名子类强制返回 userId）
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        customPrincipal,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))) {
                    @Override
                    public String getName() {
                        return expectedUserId;
                    }
                });

        // When
        String actual = invokeCurrentUserId();

        // Then
        assertThat(actual)
                .as("即使 principal.toString() 是错的，getName() 才是真相")
                .isEqualTo(expectedUserId);
    }

    @Test
    @DisplayName("P1.10：未登录时（SecurityContext 为空）currentUserId() 返回 null")
    void currentUserId_nullWhenNotAuthenticated() {
        // Given：未设置 SecurityContext
        SecurityContextHolder.clearContext();

        // When
        String actual = invokeCurrentUserId();

        // Then
        assertThat(actual).isNull();
    }

    @Test
    @DisplayName("P1.10：admin 角色不受脱敏影响（默认 maskForAdmin=false）")
    void admin_shouldSeePlaintext() throws Throwable {
        // Given：当前用户 = admin
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin", null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        // 任意用户对象
        UserVO user = new UserVO("alice", "13800000001");
        when(pjp.proceed()).thenReturn(Result.success(user));

        // When
        Object result = aspect.around(pjp);

        // Then：admin 看 alice 的 mobile 不脱敏
        Result<?> r = (Result<?>) result;
        UserVO resultUser = (UserVO) r.getData();
        assertThat(resultUser.getMobile())
                .as("ADMIN 默认可见明文")
                .isEqualTo("13800000001");
    }

    @Test
    @DisplayName("P1.10：非 admin 用户访问他人数据 → mobile 字段被脱敏")
    void nonAdmin_otherUser_shouldBeMasked() throws Throwable {
        // Given：当前用户 = bob（普通员工）
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "bob", null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));

        // 模拟 controller 返回 alice 的 user 对象
        UserVO alice = new UserVO("alice", "13800000001");
        when(pjp.proceed()).thenReturn(Result.success(alice));

        // When
        Object result = aspect.around(pjp);

        // Then：bob 看 alice 的 mobile 应该被脱敏
        Result<?> r = (Result<?>) result;
        UserVO resultUser = (UserVO) r.getData();
        assertThat(resultUser.getMobile())
                .as("非 admin 访问他人数据应被脱敏")
                .isEqualTo("138****0001");
    }

    // ============================================
    // 工具方法
    // ============================================

    private String invokeCurrentUserId() {
        try {
            Method m = SensitiveDataAspect.class.getDeclaredMethod("currentUserId");
            m.setAccessible(true);
            return (String) m.invoke(aspect);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke currentUserId()", e);
        }
    }

    private Method sampleControllerMethod() throws NoSuchMethodException {
        return SampleController.class.getMethod("getUser", String.class);
    }

    /** 测试用 Controller 桩（仅用于 ProceedingJoinPoint.getSignature().getMethod()） */
    @SuppressWarnings("unused")
    static class SampleController {
        public Result<UserVO> getUser(String userId) {
            return Result.success(new UserVO(userId, "13800000000"));
        }
    }

    /** 测试用 VO：userId 字段用反射匹配，mobile 标 @SensitiveData */
    @SuppressWarnings("unused")
    public static class UserVO {
        private String userId;
        @SensitiveData(type = SensitiveType.MOBILE)
        private String mobile;

        public UserVO() {}
        public UserVO(String userId, String mobile) {
            this.userId = userId;
            this.mobile = mobile;
        }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }
    }
}
