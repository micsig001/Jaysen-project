package com.task.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * P1.7：基于自定义 header 的简易 CSRF 防护。
 *
 * <p>设计动机：
 * <ul>
 *   <li>Spring Security 默认的 {@code CookieCsrfTokenRepository} 方案会
 *       强依赖 cookie + form 提交，破坏现有 axios 拦截器 + Bearer Token
 *       的架构，且与 {@code setAllowCredentials(true)} CORS 配置叠加
 *       后会要求 Origin/Referer 严格匹配，否则 POST 全挂。</li>
 *   <li>本方案更轻：所有受保护 {@code /api/**} 路由（公开白名单除外）要求
 *       请求头 {@code X-Requested-With: XMLHttpRequest}，浏览器原生
 *       {@code <form>} / {@code <a>} 跨站请求无法添加自定义请求头，
 *       因此天然阻挡 CSRF 攻击。</li>
 * </ul>
 *
 * <p>放行策略：
 * <ul>
 *   <li>CORS 预检 {@code OPTIONS} 请求（无 header 但带 Origin/Access-Control-Request-Method）</li>
 *   <li>公开白名单：{@code /api/auth/**}（含企微 OAuth 回调 GET）、
 *       {@code /api/files/**}（文件下载，浏览器直接 link/Img 访问）、
 *       {@code /actuator/**}（K8s 探针）、{@code /swagger-ui/**}、
 *       {@code /v3/api-docs/**}</li>
 *   <li>{@code /api/auth/wework/callback} 是企微服务端 GET 重定向，必须放行</li>
 * </ul>
 *
 * <p>校验失败：直接返回 403 + JSON 错误体（与 {@code @RestControllerAdvice}
 * 的 Result 风格一致），不再走 Spring MVC 异常处理。
 *
 * @author Mavis
 */
@Slf4j
public class CsrfHeaderFilter extends OncePerRequestFilter {

    /** 前端 axios 实例默认注入的 CSRF 标识头。 */
    public static final String CSRF_HEADER = "X-Requested-With";

    /** 前端约定的固定值。 */
    public static final String CSRF_HEADER_VALUE = "XMLHttpRequest";

    /**
     * 无需 CSRF 校验的路径前缀。
     * 注意：必须在 SecurityConfig 的 permitAll() 列表内才有效。
     */
    private static final Set<String> WHITELIST_PREFIXES = Set.of(
            "/api/auth/",
            "/api/files/",
            "/actuator/",
            "/swagger-ui",
            "/swagger-ui/",
            "/v3/api-docs"
    );

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // 1) CORS 预检：浏览器自动发起，不带自定义 header，放行
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2) 白名单前缀：放行（这些路由在 SecurityConfig 也会 permitAll）
        String path = request.getRequestURI();
        for (String prefix : WHITELIST_PREFIXES) {
            if (path.startsWith(prefix)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        // 3) 校验自定义 header
        String headerValue = request.getHeader(CSRF_HEADER);
        if (CSRF_HEADER_VALUE.equals(headerValue)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 4) 缺失或值不匹配 → 403
        log.warn("[CSRF] 拒绝请求: path={}, method={}, missingHeader={}",
                path, request.getMethod(), CSRF_HEADER);
        writeForbidden(response);
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // 与 Result 风格一致（code=403 表示"禁止访问/无权限"）
        String body = "{\"code\":403,\"message\":\"CSRF check failed: missing or invalid X-Requested-With header\"}";
        response.getWriter().write(body);
    }
}
