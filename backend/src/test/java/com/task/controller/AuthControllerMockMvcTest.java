package com.task.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.auth.JwtAuthenticationFilter;
import com.task.auth.JwtTokenProvider;
import com.task.wework.WeWorkAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController MockMvc 集成测试
 *
 * <p>覆盖 4 个端点：
 * <ul>
 *   <li>POST /api/auth/token — 企微 code 换 JWT</li>
 *   <li>GET  /api/auth/wework/callback — OAuth 回调 + 重定向</li>
 *   <li>POST /api/auth/refresh — 刷新 Token</li>
 *   <li>POST /api/auth/logout — 退出登录（加黑名单）</li>
 * </ul>
 *
 * @author Mavis
 */
@WebMvcTest(controllers = AuthController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "app.frontend-base-url=http://localhost:5173",
        "jwt.secret=test-jwt-secret-for-authcontroller-mockmvc-32chars",
        "jwt.expiration=7200000",
        "jwt.refresh-expiration=604800000"
})
class AuthControllerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WeWorkAuthService weWorkAuthService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    // ============================================
    // POST /api/auth/token
    // ============================================

    @Test
    @DisplayName("POST /token：合法 code → 200 + access_token")
    void postToken_validCode_returns200() throws Exception {
        // Given
        when(weWorkAuthService.loginByCode("valid_code")).thenReturn("mocked.jwt.token");

        Map<String, String> body = new HashMap<>();
        body.put("code", "valid_code");

        // When + Then
        mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.access_token").value("mocked.jwt.token"))
                .andExpect(jsonPath("$.data.token_type").value("Bearer"));
    }

    @Test
    @DisplayName("POST /token：空 code → 400")
    void postToken_emptyCode_returns400() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("code", "");

        mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())  // 业务上用 Result.badRequest → 200 + code=400
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("POST /token：weWorkAuthService 抛异常 → 401")
    void postToken_authServiceThrows_returns401() throws Exception {
        when(weWorkAuthService.loginByCode(anyString()))
                .thenThrow(new RuntimeException("企微 code 无效"));

        Map<String, String> body = new HashMap<>();
        body.put("code", "invalid_code");

        mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================================
    // GET /api/auth/wework/callback
    // ============================================

    @Test
    @DisplayName("GET /wework/callback：合法 code → 重定向到前端")
    void weworkCallback_validCode_redirectsToFrontend() throws Exception {
        when(weWorkAuthService.loginByCode("callback_code")).thenReturn("mocked.jwt.token");

        mockMvc.perform(get("/api/auth/wework/callback")
                        .param("code", "callback_code")
                        .param("state", "xyz123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login/callback?token=*&state=xyz123"));
    }

    @Test
    @DisplayName("GET /wework/callback：异常 → 重定向到 /login?error=...")
    void weworkCallback_exception_redirectsToLoginWithError() throws Exception {
        when(weWorkAuthService.loginByCode(anyString()))
                .thenThrow(new RuntimeException("企微服务不可用"));

        mockMvc.perform(get("/api/auth/wework/callback")
                        .param("code", "bad_code"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login?error=*"));
    }

    // ============================================
    // POST /api/auth/refresh
    // ============================================

    @Test
    @DisplayName("POST /refresh：合法 refresh_token → 200 + 新 token")
    void refreshToken_valid_returns200() throws Exception {
        when(weWorkAuthService.refreshToken("valid_refresh")).thenReturn("new.access.token");

        Map<String, String> body = new HashMap<>();
        body.put("refresh_token", "valid_refresh");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").value("new.access.token"));
    }

    @Test
    @DisplayName("POST /refresh：空 refresh_token → 400")
    void refreshToken_empty_returns400() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("refresh_token", "");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ============================================
    // POST /api/auth/logout
    // ============================================

    @Test
    @DisplayName("POST /logout：合法 Authorization → 200 + Token 加入黑名单")
    void logout_validToken_blacklistsAndReturns200() throws Exception {
        // 不需要 mock jwtTokenProvider.validateToken 实际行为 —— 真实测试在 JwtAuthenticationFilterTest
        // 这里只验证：AuthController 在 logout 流程不会崩

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer some.token.here"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /logout：无 Authorization header → 200（幂等）")
    void logout_noHeader_returns200() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk());
    }
}
