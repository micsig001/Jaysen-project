package com.task.controller;

import com.task.auth.JwtTokenProvider;
import com.task.common.Result;
import com.task.entity.User;
import com.task.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 【开发专用】本地绕过企微 OAuth 的用户名登录端点
 *
 * <p><b>仅 dev / test profile 注册</b>：生产环境（prod profile）整个类不会被 Spring 扫描，
 * 即便有人 POST /api/auth/dev-login 也会直接 404，从根本上阻止绕过企微 OAuth 创建任意账号的攻击面。</p>
 *
 * <p>触发 profile 激活条件（任一）：</p>
 * <ul>
 *   <li>{@code -Dspring.profiles.active=dev} 或环境变量 {@code SPRING_PROFILES_ACTIVE=dev}</li>
 *   <li>{@code -Dspring.profiles.active=test}</li>
 * </ul>
 *
 * <p>触发 profile 失活条件：prod / default / 任何不显式声明 dev 或 test 的 profile</p>
 *
 * <p>双保险：除类级 {@code @Profile} 外，{@link com.task.config.SecurityConfig} 也会根据
 * 当前 profile 决定是否把 {@code /api/auth/dev-login} 放行，详见 SecurityConfig 的 dev 分支。</p>
 *
 * @author Mavis
 */
@Slf4j
@Profile({"dev", "test"})
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class DevAuthController {

    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 用用户名直接换取 JWT Token（绕过企微 OAuth）
     *
     * @param request 包含 username 和可选的 role（默认 ADMIN）
     * @return JWT Token
     */
    @PostMapping("/dev-login")
    public Result<Map<String, String>> devLogin(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        if (!StringUtils.hasText(username)) {
            return Result.badRequest("username 不能为空");
        }
        String role = request.getOrDefault("role", "ADMIN").toUpperCase();
        if (!role.matches("ADMIN|MANAGER|EMPLOYEE")) {
            return Result.badRequest("role 必须是 ADMIN / MANAGER / EMPLOYEE");
        }

        // Upsert 用户（已存在则更新，不存在则创建）
        User user = userMapper.selectByUserId(username);
        boolean isNew = (user == null);
        if (isNew) {
            user = new User();
            user.setUserId(username);
            user.setCreatedAt(LocalDateTime.now());
            user.setManualRole(true);
        }
        user.setName(username);
        user.setRole(role);
        user.setStatus(1);
        user.setUpdatedAt(LocalDateTime.now());
        if (isNew) {
            userMapper.insert(user);
        } else {
            userMapper.updateById(user);
        }

        String token = jwtTokenProvider.generateAccessToken(
                user.getUserId(), user.getName(), user.getRole());

        Map<String, String> resp = new HashMap<>();
        resp.put("access_token", token);
        resp.put("token_type", "Bearer");
        resp.put("userId", user.getUserId());
        resp.put("name", user.getName());
        resp.put("role", user.getRole());
        log.info("[Dev-Login] ✅ profile={} user={} role={} (new={})",
                "dev/test", username, role, isNew);
        return Result.success(resp);
    }
}
