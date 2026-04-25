package com.github.onozaty.sample.service;

import static org.assertj.core.api.Assertions.*;

import com.github.onozaty.sample.config.CookieProperties;
import com.github.onozaty.sample.config.JwtProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtTokenServiceTest {

  private static final String SECRET = "test-secret-key-minimum-32-bytes-for-hmac-sha256!!";

  private JwtTokenService service(int accessExpirationMinutes, int refreshExpirationDays) {
    return new JwtTokenService(
        new JwtProperties(SECRET, accessExpirationMinutes, refreshExpirationDays),
        new CookieProperties(false));
  }

  @Test
  void testIssueAndDecode() {
    // Arrange
    var svc = service(15, 7);
    String sessionId = UUID.randomUUID().toString();

    // Act
    String token = svc.issueAccessToken(1L, "user@example.com", sessionId);
    var jwt = svc.jwtDecoder().decode(token);

    // Assert
    assertThat(jwt.getSubject()).isEqualTo("user@example.com");
    assertThat(jwt.<Long>getClaim(JwtTokenService.CLAIM_USER_ID)).isEqualTo(1L);
    assertThat(jwt.<String>getClaim(JwtTokenService.CLAIM_SESSION_ID)).isEqualTo(sessionId);
    assertThat(jwt.getExpiresAt()).isNotNull();
    assertThat(jwt.getIssuedAt()).isNotNull();
  }

  @Test
  void testDecodeInvalidToken() {
    // Arrange
    var svc = service(15, 7);

    // Act & Assert
    assertThatThrownBy(() -> svc.jwtDecoder().decode("invalid.token.here"))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void testBuildAccessTokenCookie() {
    // Arrange
    var svc = service(15, 7);
    String token = svc.issueAccessToken(1L, "user@example.com", UUID.randomUUID().toString());

    // Act
    var cookie = svc.buildAccessTokenCookie(token);

    // Assert
    assertThat(cookie.getName()).isEqualTo("ACCESS_TOKEN");
    assertThat(cookie.getValue()).isEqualTo(token);
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.isSecure()).isFalse();
    assertThat(cookie.getSameSite()).isEqualTo("Strict");
    assertThat(cookie.getPath()).isEqualTo("/");
    assertThat(cookie.getMaxAge()).isEqualTo(java.time.Duration.ofMinutes(15));
  }

  @Test
  void testBuildClearAccessTokenCookie() {
    // Arrange
    var svc = service(15, 7);

    // Act
    var cookie = svc.buildClearAccessTokenCookie();

    // Assert
    assertThat(cookie.getName()).isEqualTo("ACCESS_TOKEN");
    assertThat(cookie.getValue()).isEmpty();
    assertThat(cookie.getMaxAge()).isZero();
  }

  @Test
  void testBuildRefreshTokenCookie() {
    // Arrange
    var svc = service(15, 7);

    // Act
    var cookie = svc.buildRefreshTokenCookie("some-refresh-token");

    // Assert
    assertThat(cookie.getName()).isEqualTo("REFRESH_TOKEN");
    assertThat(cookie.getValue()).isEqualTo("some-refresh-token");
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.isSecure()).isFalse();
    assertThat(cookie.getSameSite()).isEqualTo("Strict");
    assertThat(cookie.getPath()).isEqualTo("/api/auth/refresh");
    assertThat(cookie.getMaxAge()).isEqualTo(java.time.Duration.ofDays(7));
  }

  @Test
  void testBuildClearRefreshTokenCookie() {
    // Arrange
    var svc = service(15, 7);

    // Act
    var cookie = svc.buildClearRefreshTokenCookie();

    // Assert
    assertThat(cookie.getName()).isEqualTo("REFRESH_TOKEN");
    assertThat(cookie.getValue()).isEmpty();
    assertThat(cookie.getMaxAge()).isZero();
  }
}
