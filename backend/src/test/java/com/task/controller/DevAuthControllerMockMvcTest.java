package com.task.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.auth.JwtAuthenticationFilter;
import com.task.auth.JwtTokenProvider;
import com.task.entity.User;
import com.task.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DevAuthController MockMvc 集成测试
 *
 * <p>覆盖 2 套场景：</p>
 * <ul>
 *   <li>{@link DevProfileTests} — dev profile 下 dev-login 可用，4 个业务用例</li>
 *   <li>{@link ProdProfileTests} — prod profile 下 dev-login 被拦截（核心安全断言）</li>
 * </ul>
 *
 * <p>prod profile 下 @Profile 注解本应阻止整个类注册，但 @WebMvcTest 强制加载指定 controller，
 * 因此用 prod profile 单独验证运行时 Environment 兜底校验是否生效。</p>
 *
 * @author Mavis
 */
class DevAuthControllerMockMvcTest {

    @WebMvcTest(controllers = DevAuthController.class,
            excludeAutoConfiguration = {
                    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
            })
    @AutoConfigureMockMvc(addFilters = false)
    @ActiveProfiles("dev")
    @TestPropertySource(properties = {
            "jwt.secret=test-jwt-secret-for-dev-auth-controller-32chars-min-aaaa",
            "jwt.expiration=7200000",
            "jwt.refresh-expiration=604800000"
    })
    @Nested
    class DevProfileTests {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private UserMapper userMapper;

        @MockBean
        private JwtTokenProvider jwtTokenProvider;

        // 防止 JwtAuthenticationFilter 注入 StringRedisTemplate 失败
        @MockBean
        private JwtAuthenticationFilter jwtAuthenticationFilter;

        private final ObjectMapper objectMapper = new ObjectMapper();

        private User stubUser(String userId, String role) {
            User u = new User();
            u.setId(1L);
            u.setUserId(userId);
            u.setName(userId);
            u.setRole(role);
            u.setStatus(1);
            return u;
        }

        @Test
        @DisplayName("dev-login 正常返回 token + user 信息（已存在用户）")
        void devLogin_existingUser_returnsToken() throws Exception {
            // Given
            when(userMapper.selectByUserId("admin")).thenReturn(stubUser("admin", "ADMIN"));
            when(jwtTokenProvider.generateAccessToken("admin", "admin", "ADMIN"))
                    .thenReturn("test-jwt-token");

            Map<String, String> body = new HashMap<>();
            body.put("username", "admin");
            body.put("role", "ADMIN");

            // When & Then
            mockMvc.perform(post("/api/auth/dev-login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.access_token").value("test-jwt-token"))
                    .andExpect(jsonPath("$.data.userId").value("admin"))
                    .andExpect(jsonPath("$.data.role").value("ADMIN"));

            // 存在用户走 updateById 分支
            verify(userMapper, times(1)).updateById(any(User.class));
            verify(userMapper, never()).insert(any(User.class));
        }

        @Test
        @DisplayName("dev-login 新建用户 → 走 insert 分支")
        void devLogin_newUser_insertsRow() throws Exception {
            // Given
            when(userMapper.selectByUserId("newuser")).thenReturn(null);
            when(jwtTokenProvider.generateAccessToken(anyString(), anyString(), anyString()))
                    .thenReturn("new-jwt-token");

            Map<String, String> body = new HashMap<>();
            body.put("username", "newuser");
            body.put("role", "EMPLOYEE");

            // When & Then
            mockMvc.perform(post("/api/auth/dev-login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.access_token").value("new-jwt-token"))
                    .andExpect(jsonPath("$.data.role").value("EMPLOYEE"));

            verify(userMapper, times(1)).insert(any(User.class));
            verify(userMapper, never()).updateById(any(User.class));
        }

        @Test
        @DisplayName("dev-login role 非法值 → 400（业务码）")
        void devLogin_invalidRole_returns400() throws Exception {
            Map<String, String> body = new HashMap<>();
            body.put("username", "admin");
            body.put("role", "ROOT");

            mockMvc.perform(post("/api/auth/dev-login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));

            verify(userMapper, never()).selectByUserId(anyString());
            verify(jwtTokenProvider, never()).generateAccessToken(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("dev-login username 为空 → 400（业务码）")
        void devLogin_emptyUsername_returns400() throws Exception {
            Map<String, String> body = new HashMap<>();
            body.put("username", "");
            body.put("role", "ADMIN");

            mockMvc.perform(post("/api/auth/dev-login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));

            verify(userMapper, never()).selectByUserId(anyString());
        }
    }

    /**
     * 【关键安全测试】prod profile 下 dev-login 必须被拦截
     *
     * <p>背景：{@code @Profile("dev","test")} 在 @WebMvcTest 强制加载 controller 时不生效，
     * 但 DevAuthController 内部的 Environment 兜底校验仍应触发，
     * 抛 BusinessException → 500 / message 含 "dev-login 端点仅在 dev/test profile 下可用"。</p>
     */
    @WebMvcTest(controllers = DevAuthController.class,
            excludeAutoConfiguration = {
                    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
            })
    @AutoConfigureMockMvc(addFilters = false)
    @ActiveProfiles("prod")
    @TestPropertySource(properties = {
            "jwt.secret=prod-jwt-secret-must-be-strong-32chars-min-aaaaa-bbbb",
            "jwt.expiration=7200000",
            "jwt.refresh-expiration=604800000"
    })
    @Nested
    class ProdProfileTests {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private UserMapper userMapper;

        @MockBean
        private JwtTokenProvider jwtTokenProvider;

        @MockBean
        private JwtAuthenticationFilter jwtAuthenticationFilter;

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Test
        @DisplayName("【安全】prod profile 下 dev-login 必须被拦截（Environment 兜底）")
        void devLogin_prodProfile_blocked() throws Exception {
            Map<String, String> body = new HashMap<>();
            body.put("username", "hacker");
            body.put("role", "ADMIN");

            mockMvc.perform(post("/api/auth/dev-login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().is5xxServerError());

            // 关键断言：拦截时不应触碰 DB 或签 token
            verify(userMapper, never()).selectByUserId(anyString());
            verify(jwtTokenProvider, never()).generateAccessToken(anyString(), anyString(), anyString());
        }
    }
}
