package com.task.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.auth.JwtAuthenticationFilter;
import com.task.auth.JwtTokenProvider;
import com.task.entity.User;
import com.task.mapper.UserMapper;
import com.task.wework.FakeWeWorkApiClient;
import com.task.wework.WeWorkApiClient;
import com.task.wework.WeWorkAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 完整 OAuth 流程端到端测试（real WeWorkAuthService + FakeWeWorkApiClient + Mocked UserMapper）
 *
 * <p>与 {@link AuthControllerMockMvcTest} 的差异：
 * <ul>
 *   <li>本测试用 <b>真实</b> {@link WeWorkAuthService}，串联 {@link FakeWeWorkApiClient} + 真实
 *       {@link JwtTokenProvider} + mocked {@link UserMapper}</li>
 *   <li>{@link AuthControllerMockMvcTest} 用 {@code @MockBean WeWorkAuthService}，只验证 controller 层</li>
 * </ul>
 *
 * <p>覆盖：
 * <ul>
 *   <li>GET /api/auth/wework/callback — 已存在用户（alice）走更新路径，最终 302 重定向到前端</li>
 *   <li>GET /api/auth/wework/callback — 新用户（bob）走 insert 路径，最终 302 重定向到前端</li>
 *   <li>GET /api/auth/wework/callback — 无效 code 走错误重定向到 /login?error=...</li>
 *   <li>POST /api/auth/token — 合法 code → 200 + access_token（端到端）</li>
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
        "jwt.secret=test-jwt-secret-for-oauth-flow-mockmvc-32chars",
        "jwt.expiration=7200000",
        "jwt.refresh-expiration=604800000"
})
@Import(WeWorkAuthServiceOAuthFlowTest.OAuthFlowBeans.class)
@DisplayName("完整 OAuth 流程端到端（real WeWorkAuthService + FakeWeWorkApiClient）")
class WeWorkAuthServiceOAuthFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FakeWeWorkApiClient fakeWeWorkApiClient;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void seedFakeUsers() {
        // alice 已存在（验证 update 路径）
        User existingAlice = new User();
        existingAlice.setId(100L);
        existingAlice.setUserId("alice");
        existingAlice.setName("Alice Wang");
        existingAlice.setRole("EMPLOYEE");
        existingAlice.setStatus(1);
        Mockito.when(userMapper.selectByUserId("alice")).thenReturn(existingAlice);
        // bob 不存在（验证 insert 路径）
        Mockito.when(userMapper.selectByUserId("bob")).thenReturn(null);
        // 插入/更新不抛异常
        Mockito.when(userMapper.insert(Mockito.any(User.class))).thenAnswer(inv -> 1);
        Mockito.when(userMapper.updateById(Mockito.any(User.class))).thenReturn(1);

        // 在 Fake 客户端预置两个用户
        fakeWeWorkApiClient
                .registerCode("code_alice_001", "alice")
                .registerCode("code_bob_new", "bob")
                .seedUser("alice", "Alice Wang", "13900000001", "alice@fake.example")
                .seedUser("bob", "Bob Chen", "13900000002", "bob@fake.example");
    }

    @Test
    @DisplayName("回调 code=valid_<userId> → 走 fake → upsert user → 302 到前端 /login/callback")
    void callback_validCode_existingUserRedirects() throws Exception {
        mockMvc.perform(get("/api/auth/wework/callback")
                        .param("code", "valid_alice")
                        .param("state", "csrf-abc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login/callback?token=*&state=csrf-abc"));
        // alice 走 update 路径
        Mockito.verify(userMapper, Mockito.atLeastOnce()).updateById(Mockito.argThat(
                u -> u != null && "alice".equals(u.getUserId())));
    }

    @Test
    @DisplayName("回调 code=code_bob_new（首次登录）→ 走 fake → 创建 user → 302 前端")
    void callback_newUserCode_createsAndRedirects() throws Exception {
        mockMvc.perform(get("/api/auth/wework/callback")
                        .param("code", "code_bob_new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login/callback?token=*"));
        // bob 走 insert 路径
        Mockito.verify(userMapper, Mockito.atLeastOnce()).insert(Mockito.argThat(
                u -> u != null && "bob".equals(u.getUserId())));
    }

    @Test
    @DisplayName("回调 code=invalid_xxx（无映射）→ 重定向到 /login?error=...")
    void callback_invalidCode_redirectsToLoginError() throws Exception {
        mockMvc.perform(get("/api/auth/wework/callback")
                        .param("code", "invalid_xxx"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login?error=*"));
    }

    @Test
    @DisplayName("POST /token：合法 code → 200 + access_token（端到端）")
    void postToken_endToEnd_returnsJwt() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("code", "valid_alice");

        mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.access_token").exists())
                .andExpect(jsonPath("$.data.token_type").value("Bearer"));
    }

    /**
     * 测试 bean 配置：
     * <ul>
     *   <li>FakeWeWorkApiClient 覆盖默认 WeWorkApiClient（@Primary）</li>
     *   <li>真实的 JwtTokenProvider（用 @TestPropertySource 里的 jwt.secret 初始化）</li>
     *   <li>真实的 WeWorkAuthService 串起所有（构造器注入）</li>
     * </ul>
     */
    @TestConfiguration
    static class OAuthFlowBeans {

        @Bean
        public WeWorkApiClient weWorkApiClient() {
            return new FakeWeWorkApiClient();
        }

        @Bean
        public JwtTokenProvider jwtTokenProvider(
                @org.springframework.beans.factory.annotation.Value("${jwt.secret}") String secret,
                @org.springframework.beans.factory.annotation.Value("${jwt.expiration}") long expiration,
                @org.springframework.beans.factory.annotation.Value("${jwt.refresh-expiration}") long refreshExpiration) {
            return new JwtTokenProvider(secret, expiration, refreshExpiration);
        }

        @Bean
        public WeWorkAuthService weWorkAuthService(WeWorkApiClient weWorkApiClient,
                                                    JwtTokenProvider jwtTokenProvider,
                                                    UserMapper userMapper) {
            return new WeWorkAuthService(weWorkApiClient, jwtTokenProvider, userMapper);
        }
    }
}
