package com.task.controller;

import com.task.auth.JwtTokenProvider;
import com.task.common.BusinessException;
import com.task.common.Result;
import com.task.entity.User;
import com.task.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
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
 * <p><b>三重保险</b>确保生产环境无法调用：</p>
 * <ol>
 *   <li>类级 {@code @Profile({"dev","test"})}：prod / default 启动时整个类不被 Spring 注册，路由直接 404</li>
 *   <li>运行时 Environment 二次校验：万一因 Spring profile 配置错误导致类被注册，
 *       方法内部检查 active profiles，非 dev/test 时直接抛异常 500</li>
 *   <li>生产构建排除：建议在 Dockerfile.prod 用 Maven filter 移除此类</li>
 * </ol>
 *
 * <p>触发生效 profile（任一）：</p>
 * <ul>
 *   <li>{@code -Dspring.profiles.active=dev} 或环境变量 {@code SPRING_PROFILES_ACTIVE=dev}</li>
 *   <li>{@code -Dspring.profiles.active=test}</li>
 * </ul>
 *
 * <p>触发失活 profile：prod / default / 任何不显式声明 dev 或 test 的 profile</p>
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
    private final Environment environment;

    /**
     * 用用户名直接换取 JWT Token（绕过企微 OAuth）
     *
     * @param request 包含 username 和可选的 role（默认 ADMIN）
     * @return JWT Token
     */
    @PostMapping("/dev-login")
    public Result<Map<String, String>> devLogin(@RequestBody Map<String, String> request) {
        // N1 运行时二次校验：即便 @Profile 注解因配置错误失效，这里也兜底拦截
        if (!isDevLikeProfile()) {
            log.error("[Dev-Login] ❌ 拒绝：当前 active profiles 非 dev/test，" +
                    "activeProfiles={}", String.join(",", environment.getActiveProfiles()));
            throw new BusinessException(403,
                    "dev-login 端点仅在 dev/test profile 下可用");
        }

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
                String.join(",", environment.getActiveProfiles()),
                username, role, isNew);
        return Result.success(resp);
    }

    /**
     * 判定当前激活的 profile 是否属于 dev/test 系列
     */
    private boolean isDevLikeProfile() {
        for (String p : environment.getActiveProfiles()) {
            String lp = p.toLowerCase();
            if (lp.contains("dev") || lp.contains("test")) {
                return true;
            }
        }
        return false;
    }
}
