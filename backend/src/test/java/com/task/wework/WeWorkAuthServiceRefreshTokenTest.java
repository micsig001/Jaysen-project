package com.task.wework;

import com.task.auth.JwtTokenProvider;
import com.task.common.BusinessException;
import com.task.entity.User;
import com.task.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WeWorkAuthService.refreshToken 单元测试
 *
 * <p>覆盖场景（P1.11）：
 * <ul>
 *   <li>无效 token（空字符串/乱码）→ 抛 BusinessException(401)，而非 500</li>
 *   <li>过期 token（JwtTokenProvider 抛 IllegalArgumentException）→ 401</li>
 *   <li>合法 token + 已存在用户 + 启用状态 → 200 + 返回新 access token</li>
 *   <li>合法 token + 用户不存在 → 404</li>
 *   <li>合法 token + 用户被禁用 → 403</li>
 * </ul>
 *
 * <p>关键回归点：旧实现 {@code if (validateToken(...) == null)} 是死代码 —
 * {@link JwtTokenProvider#validateToken(String)} 永远 throw 而不是 return null。
 * 必须用 try-catch 捕获 {@link IllegalArgumentException} 才能转成 401。
 *
 * @author Mavis
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WeWorkAuthServiceRefreshTokenTest {

    @Mock
    private WeWorkApiClient weWorkApiClient;

    @Mock
    private UserMapper userMapper;

    private JwtTokenProvider jwtTokenProvider;
    private WeWorkAuthService weWorkAuthService;

    private static final String USER_ID = "alice";
    private static final long EXPIRATION = 7_200_000L;          // 2h
    private static final long REFRESH_EXPIRATION = 60_000L;     // 1min (短一些方便"过期"测试)

    @BeforeEach
    void setUp() {
        // 用真实的 JwtTokenProvider（test secret 32+ 字符）
        jwtTokenProvider = new JwtTokenProvider(
                "test-jwt-secret-for-refresh-token-32chars",
                EXPIRATION,
                REFRESH_EXPIRATION);
        weWorkAuthService = new WeWorkAuthService(weWorkApiClient, jwtTokenProvider, userMapper);
    }

    // ============================================
    // P1.11 核心场景：无效/过期 token → 401
    // ============================================

    @Test
    @DisplayName("P1.11：传 null/空 refreshToken → 抛 BusinessException(401)，不抛 500")
    void refreshToken_blankOrNull_returns401() {
        // null
        assertThatThrownBy(() -> weWorkAuthService.refreshToken(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode())
                            .as("无效 token 应返回 401 而非 500/400")
                            .isEqualTo(401);
                });

        // 空字符串
        assertThatThrownBy(() -> weWorkAuthService.refreshToken(""))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(401);
                });

        // 不应继续查 DB
        verify(userMapper, never()).selectByUserId(anyString());
    }

    @Test
    @DisplayName("P1.11：传乱码 refreshToken（IllegalArgumentException）→ 401")
    void refreshToken_garbledToken_returns401() {
        // 给一个非 JWT 格式的字符串 — JwtTokenProvider 会抛 IllegalArgumentException
        String garbled = "this.is.not.a.jwt";

        assertThatThrownBy(() -> weWorkAuthService.refreshToken(garbled))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode())
                            .as("乱码 token（IllegalArgumentException）应转为 401")
                            .isEqualTo(401);
                });

        verify(userMapper, never()).selectByUserId(anyString());
    }

    @Test
    @DisplayName("P1.11：传过期 refreshToken（mock 抛 ExpiredJwtException）→ 401")
    void refreshToken_expiredToken_returns401() {
        // 用 Mockito mock 一个 JwtTokenProvider，让 validateToken 抛 IllegalArgumentException
        // （JwtTokenProvider 内部把 JwtException 包成 IllegalArgumentException）
        JwtTokenProvider mockedProvider = org.mockito.Mockito.mock(JwtTokenProvider.class);
        when(mockedProvider.validateToken("expired-token"))
                .thenThrow(new IllegalArgumentException("JWT expired"));
        WeWorkAuthService service = new WeWorkAuthService(weWorkApiClient, mockedProvider, userMapper);

        assertThatThrownBy(() -> service.refreshToken("expired-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode())
                            .as("过期 token 应返回 401")
                            .isEqualTo(401);
                });

        verify(userMapper, never()).selectByUserId(anyString());
    }

    @Test
    @DisplayName("P1.11：用错误 secret 签发的 token（验签失败）→ 401")
    void refreshToken_wrongSecret_returns401() {
        // 用另一组 secret 签发
        JwtTokenProvider otherProvider = new JwtTokenProvider(
                "different-secret-still-32-chars-long-xxxxx",
                EXPIRATION,
                REFRESH_EXPIRATION);
        String foreignToken = otherProvider.generateRefreshToken(USER_ID);

        assertThatThrownBy(() -> weWorkAuthService.refreshToken(foreignToken))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode())
                            .as("验签失败的 token 应返回 401")
                            .isEqualTo(401);
                });
    }

    // ============================================
    // 正常路径
    // ============================================

    @Test
    @DisplayName("合法 token + 已存在用户 + 启用状态 → 返回新 access token")
    void refreshToken_validToken_returnsNewAccessToken() {
        // 给定
        String refreshToken = jwtTokenProvider.generateRefreshToken(USER_ID);
        User existing = new User();
        existing.setId(1L);
        existing.setUserId(USER_ID);
        existing.setName("Alice");
        existing.setRole("EMPLOYEE");
        existing.setStatus(1);
        when(userMapper.selectByUserId(USER_ID)).thenReturn(existing);

        // 当
        String accessToken = weWorkAuthService.refreshToken(refreshToken);

        // 那么
        assertThat(accessToken).isNotBlank();
        // 新签发的 token 应当能被同一个 provider 解析
        String extractedUserId = jwtTokenProvider.getUserIdFromToken(accessToken);
        assertThat(extractedUserId).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("合法 token + 用户不存在 → 404（不是 401）")
    void refreshToken_validTokenButUserNotFound_returns404() {
        String refreshToken = jwtTokenProvider.generateRefreshToken("ghost-user");
        when(userMapper.selectByUserId("ghost-user")).thenReturn(null);

        assertThatThrownBy(() -> weWorkAuthService.refreshToken(refreshToken))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(404);
                });
    }

    @Test
    @DisplayName("合法 token + 用户被禁用 → 403")
    void refreshToken_validTokenButUserDisabled_returns403() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(USER_ID);
        User disabled = new User();
        disabled.setId(1L);
        disabled.setUserId(USER_ID);
        disabled.setName("Alice");
        disabled.setRole("EMPLOYEE");
        disabled.setStatus(0);  // 禁用
        when(userMapper.selectByUserId(USER_ID)).thenReturn(disabled);

        assertThatThrownBy(() -> weWorkAuthService.refreshToken(refreshToken))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(403);
                });
    }
}
