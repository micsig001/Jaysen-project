package com.task.auth;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * JWT 认证过滤器
 * 从请求头提取并验证JWT Token,将用户信息存入SecurityContext
 *
 * <p>设计说明（P2-1）：
 * <ul>
 *   <li>认证失败时不再直接写 JSON 响应（绕过 {@code AuthenticationEntryPoint}），
 *       改为委托给 {@code HandlerExceptionResolver}，由全局异常处理
 *       ({@code @RestControllerAdvice} + {@code AuthenticationException})
 *       输出标准 {@code Result} JSON 响应体。</li>
 *   <li>容器关闭钩子改用 {@code @PreDestroy}，符合 Spring 生命周期管理规范，
 *       避免覆盖 {@code Filter.destroy()} 与 Spring bean 销毁阶段的耦合问题。</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String TOKEN_HEADER = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";
    private static final String BLACKLIST_PREFIX = "token:blacklist:";

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    /**
     * 注入 Spring MVC 的 HandlerExceptionResolver（典型实现是
     * {@code ExceptionHandlerExceptionResolver}），用于在 Filter 链中将
     * 业务异常/认证异常委托给 {@code @RestControllerAdvice} 统一处理。
     * <p>显式 @Qualifier 避免与 {@code errorAttributes} bean 冲突
     * （{@code errorAttributes} 实现了多个接口，其中之一是
     * {@code HandlerExceptionResolver}）。
     */
    private final HandlerExceptionResolver resolver;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   StringRedisTemplate redisTemplate,
                                   @Qualifier("handlerExceptionResolver")
                                   HandlerExceptionResolver resolver) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisTemplate = redisTemplate;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = resolveToken(request);

            if (token != null) {
                // 检查Token是否在黑名单中
                if (isTokenBlacklisted(token)) {
                    log.warn("Token已在黑名单中: {}", token.substring(0, Math.min(20, token.length())));
                    resolver.resolveException(request, response, null,
                            new org.springframework.security.authentication.BadCredentialsException("Token已失效"));
                    return;
                }

                // 验证Token并获取用户信息
                String userId = jwtTokenProvider.getUserIdFromToken(token);
                String role = jwtTokenProvider.getRoleFromToken(token);

                // 构建认证对象
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                        );

                // 将认证信息存入SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("用户认证成功: userId={}, role={}", userId, role);
            }

            filterChain.doFilter(request, response);

        } catch (IllegalArgumentException e) {
            log.warn("JWT验证失败: {}", e.getMessage());
            resolver.resolveException(request, response, null,
                    new org.springframework.security.authentication.BadCredentialsException("无效的Token", e));
        } catch (org.springframework.security.core.AuthenticationException e) {
            log.warn("JWT认证失败: {}", e.getMessage());
            resolver.resolveException(request, response, null, e);
        } catch (Exception e) {
            log.error("JWT认证处理异常", e);
            resolver.resolveException(request, response, null,
                    new org.springframework.security.authentication.BadCredentialsException("认证失败", e));
        }
    }

    /**
     * 从请求头中提取Token
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(TOKEN_HEADER);
        if (bearerToken != null && bearerToken.startsWith(TOKEN_PREFIX)) {
            return bearerToken.substring(TOKEN_PREFIX.length());
        }
        return null;
    }

    /**
     * 检查Token是否在黑名单中
     */
    private boolean isTokenBlacklisted(String token) {
        String blacklistKey = BLACKLIST_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey));
    }

    /**
     * 将Token加入黑名单
     */
    public void blacklistToken(String token, long expirationMillis) {
        String blacklistKey = BLACKLIST_PREFIX + token;
        redisTemplate.opsForValue().set(blacklistKey, "1", expirationMillis, TimeUnit.MILLISECONDS);
        log.info("Token已加入黑名单,有效期: {}ms", expirationMillis);
    }

    /**
     * Bean 销毁阶段清理 SecurityContext（P2-1：替代 {@code destroy()}，
     * 避免覆盖 {@code GenericFilterBean.destroy()} 的 Spring 容器语义）。
     */
    @PreDestroy
    public void cleanup() {
        SecurityContextHolder.clearContext();
    }
}
