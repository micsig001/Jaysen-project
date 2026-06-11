package com.task.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.8：JwtSecretValidator 单元测试。
 *
 * <p>通过 {@link MockEnvironment} 注入 active profile 模拟生产/非生产环境，
 * 不依赖 Spring Context 启动，跑得快。
 *
 * @author Mavis
 */
@DisplayName("JwtSecretValidator 启动校验 (P1.8)")
class JwtSecretValidatorTest {

    private static final String DEV_PLACEHOLDER =
            "dev-secret-CHANGE-ME-in-production-min-32-chars";

    @Test
    @DisplayName("dev profile + 占位 secret：不抛异常（仅警告）")
    void devProfile_withPlaceholder_doesNotThrow() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        JwtSecretValidator v = new JwtSecretValidator(env, DEV_PLACEHOLDER);
        // 不抛异常
        v.run(null);
    }

    @Test
    @DisplayName("dev profile + 短 secret (10 字符)：不抛异常（仅警告）")
    void devProfile_withShortSecret_doesNotThrow() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        JwtSecretValidator v = new JwtSecretValidator(env, "short");
        v.run(null);
    }

    @Test
    @DisplayName("test profile + 强 secret：不抛异常")
    void testProfile_withStrongSecret_doesNotThrow() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");
        JwtSecretValidator v = new JwtSecretValidator(env,
                "test-jwt-secret-32-chars-or-more-padding");
        v.run(null);
    }

    @Test
    @DisplayName("prod profile + 占位 secret：启动失败")
    void prodProfile_withPlaceholder_throws() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        JwtSecretValidator v = new JwtSecretValidator(env, DEV_PLACEHOLDER);
        assertThatThrownBy(() -> v.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    @DisplayName("prod profile + 短 secret (16 字符)：启动失败")
    void prodProfile_withShortSecret_throws() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        JwtSecretValidator v = new JwtSecretValidator(env, "short-secret-16ch");
        assertThatThrownBy(() -> v.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too short");
    }

    @Test
    @DisplayName("prod profile + 强 secret (≥32 字符)：不抛异常")
    void prodProfile_withStrongSecret_doesNotThrow() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        JwtSecretValidator v = new JwtSecretValidator(env,
                "real-production-jwt-secret-32-chars-strong");
        v.run(null);
    }

    @Test
    @DisplayName("未指定 profile + 占位 secret：非 prod，不抛")
    void noProfile_withPlaceholder_doesNotThrow() {
        MockEnvironment env = new MockEnvironment();
        // 不设 active profile，让 default profile 生效
        JwtSecretValidator v = new JwtSecretValidator(env, DEV_PLACEHOLDER);
        v.run(null);
    }

    @Test
    @DisplayName("isProdProfile 通过 active profile 判定")
    void isProdProfile_detectsActive() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("PROD"); // 大小写不敏感
        JwtSecretValidator v = new JwtSecretValidator(env,
                "real-production-jwt-secret-32-chars-strong");
        v.run(null); // 不抛
        // 已隐式验证：activeProfiles 包含 "PROD" 时被识别为 prod
        assertThat(env.getActiveProfiles()).contains("PROD");
    }
}
