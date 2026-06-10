package com.task.controller;

import com.task.auth.JwtAuthenticationFilter;
import com.task.auth.JwtTokenProvider;
import com.task.common.Result;
import com.task.wework.WeWorkAuthService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 * 处理企业微信 OAuth2.0 登录、Token 刷新和退出登录
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final WeWorkAuthService weWorkAuthService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 前端基础 URL（OAuth 回调后重定向目标）
     * 配置：app.frontend-base-url=https://your-domain
     */
    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    /**
     * 通过企微授权码换取 JWT Token
     *
     * @param request 请求体，包含 code 字段
     * @return JWT Token
     */
    @PostMapping("/token")
    public Result<Map<String, String>> getToken(@RequestBody Map<String, String> request) {
        String code = request.get("code");
        if (code == null || code.isEmpty()) {
            return Result.badRequest("授权码不能为空");
        }

        try {
            String accessToken = weWorkAuthService.loginByCode(code);

            Map<String, String> response = new HashMap<>();
            response.put("access_token", accessToken);
            response.put("token_type", "Bearer");

            return Result.success(response);
        } catch (Exception e) {
            log.error("登录失败", e);
            return Result.error(401, e.getMessage());
        }
    }

    /**
     * 企微 OAuth2.0 回调接口
     *
     * 流程：
     *   1) 企微工作台点击应用 → 企微服务器重定向到本接口（带 code）
     *   2) 用 code 换取用户信息 + JWT Token
     *   3) 重定向回前端，URL 带上 token
     *
     * 注意：本接口是 GET（企微 OAuth 规范），需要 SecurityConfig 放行
     *
     * @param code 企微授权码
     * @param state 可选，企微传递的 state 参数（防 CSRF）
     * @param response HttpServletResponse 用于重定向
     */
    @GetMapping("/wework/callback")
    public void weworkCallback(@RequestParam("code") String code,
                                @RequestParam(value = "state", required = false) String state,
                                HttpServletResponse response) throws IOException {
        log.info("[企微 OAuth] 收到回调, code={}, state={}", code, state);

        try {
            // 1) 用 code 换 JWT token
            String accessToken = weWorkAuthService.loginByCode(code);

            // 2) 构造重定向 URL：把 token 拼到前端 callback 页面
            // 前端应该有 /login/callback 路由，解析 token 后存到 localStorage
            String redirectUrl = frontendBaseUrl + "/login/callback"
                    + "?token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
            if (state != null) {
                redirectUrl += "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
            }
            log.info("[企微 OAuth] 登录成功，重定向到前端: {}", redirectUrl);
            response.sendRedirect(redirectUrl);
        } catch (Exception e) {
            log.error("[企微 OAuth] 登录失败", e);
            // 失败重定向到前端登录页 + 错误信息
            String errorUrl = frontendBaseUrl + "/login?error="
                    + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
            response.sendRedirect(errorUrl);
        }
    }

    /**
     * 刷新 Token
     *
     * @param request 请求体，包含 refresh_token 字段
     * @return 新的 Access Token
     */
    @PostMapping("/refresh")
    public Result<Map<String, String>> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refresh_token");
        if (refreshToken == null || refreshToken.isEmpty()) {
            return Result.badRequest("刷新令牌不能为空");
        }

        try {
            String accessToken = weWorkAuthService.refreshToken(refreshToken);

            Map<String, String> response = new HashMap<>();
            response.put("access_token", accessToken);
            response.put("token_type", "Bearer");

            return Result.success(response);
        } catch (Exception e) {
            log.error("刷新 Token 失败", e);
            return Result.error(401, e.getMessage());
        }
    }

    /**
     * 退出登录
     * 将Token加入Redis黑名单使其立即失效
     *
     * @param authorization 请求头中的Authorization
     * @return 成功响应
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            try {
                // 获取Token剩余有效期
                Claims claims = jwtTokenProvider.validateToken(token);
                long expirationTime = claims.getExpiration().getTime();
                long currentTime = System.currentTimeMillis();
                long remainingTime = expirationTime - currentTime;

                if (remainingTime > 0) {
                    // 将Token加入黑名单,有效期为Token剩余时间
                    jwtAuthenticationFilter.blacklistToken(token, remainingTime);
                    log.info("用户退出登录,Token已加入黑名单");
                }
            } catch (Exception e) {
                log.warn("Token验证失败,但仍执行退出操作", e);
            }
        }
        return Result.success(null);
    }
}
