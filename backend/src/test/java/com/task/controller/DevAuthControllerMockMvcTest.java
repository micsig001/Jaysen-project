package com.task.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.auth.JwtAuthenticationFilter;
import com.task.auth.JwtTokenProvider;
import com.task.entity.User;
import com.task.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
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
 * <p>覆盖 4 个场景：</p>
 * <ul>
 *   <li>正常用户名登录（已存在用户）→ 200 + token</li>
 *   <li>正常用户名登录（不存在用户）→ 200 + 新建用户</li>
 *   <li>role 非法值 → 400</li>
 *   <li>username 为空 → 400</li>
 * </ul>
 *
 * <p>所有测试在 {@code @ActiveProfiles("dev")} 下运行，验证 {@code @Profile("dev")} 注解生效。</p>
 *
 * @author Mavis
 */
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
class DevAuthControllerMockMvcTest {

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
        body.put("role", "ROOT"); // 非法值

        mockMvc.perform(post("/api/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        // 校验失败不应触碰 DB 或签 token
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
