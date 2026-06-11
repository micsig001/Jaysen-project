package com.task.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * P1.7：CsrfHeaderFilter 单元测试（不启动 Spring Context，验证 filter 自身逻辑）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>带 X-Requested-With=XMLHttpRequest → 放行（filterChain.doFilter 被调用）</li>
 *   <li>缺 header → 403 + JSON 错误体（filterChain 不再调用）</li>
 *   <li>header 值错误 → 403</li>
 *   <li>OPTIONS 预检 → 放行</li>
 *   <li>白名单路径（/api/auth/**, /api/files/**, /actuator/**）→ 放行</li>
 * </ul>
 */
@DisplayName("CsrfHeaderFilter 单元测试 (P1.7)")
class CsrfHeaderFilterTest {

    private CsrfHeaderFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new CsrfHeaderFilter();
        chain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("带 X-Requested-With=XMLHttpRequest → 放行")
    void validHeader_passes() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users/u1");
        req.addHeader(CsrfHeaderFilter.CSRF_HEADER, CsrfHeaderFilter.CSRF_HEADER_VALUE);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verify(chain, times(1)).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("缺 X-Requested-With header → 403 + JSON")
    void missingHeader_returns403() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/tasks");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(resp.getContentAsString()).contains("CSRF check failed");
        assertThat(resp.getContentAsString()).contains(CsrfHeaderFilter.CSRF_HEADER);
    }

    @Test
    @DisplayName("X-Requested-With 值错误 → 403")
    void wrongHeaderValue_returns403() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/tasks");
        req.addHeader(CsrfHeaderFilter.CSRF_HEADER, "fetch");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(resp.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("OPTIONS 预检请求 → 放行（无 header）")
    void optionsPreflight_passes() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("OPTIONS", "/api/tasks");
        req.addHeader("Origin", "http://localhost:5173");
        req.addHeader("Access-Control-Request-Method", "POST");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    @DisplayName("白名单 /api/auth/** → 放行（OAuth 回调无 header）")
    void authPath_whitelisted() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/auth/wework/callback");
        req.setQueryString("code=abc&state=xyz");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    @DisplayName("白名单 /api/auth/token → 放行（企微 code 换 JWT）")
    void authToken_whitelisted() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/token");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    @DisplayName("白名单 /api/files/** → 放行（外部 link 访问文件）")
    void filesPath_whitelisted() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/files/abc123");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    @DisplayName("白名单 /actuator/health → 放行（K8s 探针）")
    void actuatorPath_whitelisted() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    @DisplayName("非白名单的 /api/** 缺 header → 403（保护未覆盖路径）")
    void nonWhitelistedApiPath_missingHeader_returns403() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("PUT", "/api/tasks/123");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(resp.getStatus()).isEqualTo(403);
    }
}
