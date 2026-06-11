package com.task.wework;

import com.fasterxml.jackson.databind.JsonNode;
import com.task.auth.JwtTokenProvider;
import com.task.common.BusinessException;
import com.task.entity.User;
import com.task.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 企业微信认证服务
 * 处理 OAuth2.0 授权流程、用户信息同步和 JWT Token 签发
 *
 * <p>{@link WeWorkApiClient} 注入的是 Spring 容器中的 Bean，由 {@link WeWorkApiConfig}
 * 根据 profile 决定是 {@link FakeWeWorkApiClient}（dev/test）还是真实实现（prod）。
 * 业务侧无需感知。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeWorkAuthService {

    /**
     * 注入的 WeWorkApiClient：可能是真实实现（prod），也可能是 Fake（dev/test）
     * <p>类型注入 + @Primary 自动选择，业务侧无感
     */
    private final WeWorkApiClient weWorkApiClient;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    /**
     * 通过企微授权码进行登录
     *
     * @param code 企微授权码
     * @return JWT Token
     */
    @Transactional(rollbackFor = Exception.class)
    public String loginByCode(String code) {
        // 1. 通过授权码获取用户 ID
        JsonNode userInfo = weWorkApiClient.getUserInfoByCode(code);
        if (userInfo == null || !userInfo.has("UserId")) {
            throw new BusinessException(401, "授权码无效或已过期");
        }

        String userId = userInfo.get("UserId").asText();
        log.info("企微授权登录，UserID: {}, client={}", userId,
                weWorkApiClient.getClass().getSimpleName());

        // 2. 获取用户详细信息
        JsonNode userDetail = weWorkApiClient.getUserDetail(userId);
        if (userDetail == null) {
            throw new BusinessException(500, "获取用户详细信息失败");
        }

        // 3. 同步用户信息到本地数据库（Upsert）
        User user = syncUserFromWeWork(userDetail);

        // 4. 检查用户状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }

        // 5. 生成 JWT Token
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getUserId(),
                user.getName(),
                user.getRole()
        );

        log.info("用户 {} 登录成功", user.getName());
        return accessToken;
    }

    /**
     * 从企微同步用户信息到本地数据库
     */
    private User syncUserFromWeWork(JsonNode userDetail) {
        String userId = userDetail.get("userid").asText();

        // 查询现有用户
        User existingUser = userMapper.selectByUserId(userId);

        if (existingUser != null) {
            // 更新用户信息
            existingUser.setName(userDetail.has("name") ? userDetail.get("name").asText() : userId);
            existingUser.setMobile(userDetail.has("mobile") ? userDetail.get("mobile").asText() : null);
            existingUser.setEmail(userDetail.has("email") ? userDetail.get("email").asText() : null);
            existingUser.setAvatarUrl(userDetail.has("avatar") ? userDetail.get("avatar").asText() : null);

            // 同步部门信息
            if (userDetail.has("department") && userDetail.get("department").isArray()
                    && userDetail.get("department").size() > 0) {
                existingUser.setDepartmentId(userDetail.get("department").get(0).asLong());
            }

            existingUser.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(existingUser);

            log.debug("更新用户信息: {}", userId);
            return existingUser;
        } else {
            // 创建新用户
            User newUser = new User();
            newUser.setUserId(userId);
            newUser.setName(userDetail.has("name") ? userDetail.get("name").asText() : userId);
            newUser.setMobile(userDetail.has("mobile") ? userDetail.get("mobile").asText() : null);
            newUser.setEmail(userDetail.has("email") ? userDetail.get("email").asText() : null);
            newUser.setAvatarUrl(userDetail.has("avatar") ? userDetail.get("avatar").asText() : null);
            newUser.setStatus(1); // 默认启用

            // 设置角色（默认为员工）
            newUser.setRole("EMPLOYEE");
            newUser.setManualRole(false); // 非手动设置，可被同步覆盖

            // 同步部门信息
            if (userDetail.has("department") && userDetail.get("department").isArray()
                    && userDetail.get("department").size() > 0) {
                newUser.setDepartmentId(userDetail.get("department").get(0).asLong());
            }

            newUser.setCreatedAt(LocalDateTime.now());
            newUser.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(newUser);

            log.info("创建新用户: {}", userId);
            return newUser;
        }
    }

    /**
     * 刷新 Token
     *
     * @param refreshToken 刷新令牌
     * @return 新的 Access Token
     */
    public String refreshToken(String refreshToken) {
        // 修复（P1.11）：jwtTokenProvider.validateToken() 在 token 无效/过期时
        // 抛 IllegalArgumentException（不是返回 null）。原先 `if (... == null)`
        // 是死代码 — 异常直接向上传播，被 controller 顶层 catch 转成 500。
        // 现在显式捕获并转为 401，让 HTTP 语义正确（401 = 未认证/凭证无效）。
        try {
            jwtTokenProvider.validateToken(refreshToken);
        } catch (IllegalArgumentException e) {
            log.warn("刷新令牌无效或已过期: {}", e.getMessage());
            throw new BusinessException(401, "刷新令牌无效或已过期");
        }

        String userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userMapper.selectByUserId(userId);

        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用");
        }

        // 生成新的 Access Token
        return jwtTokenProvider.generateAccessToken(user.getUserId(), user.getName(), user.getRole());
    }
}
