package com.github.onozaty.sample.service;

import static org.assertj.core.api.Assertions.*;

import com.github.onozaty.sample.AppTest;
import com.github.onozaty.sample.domain.UserCreateInput;
import com.github.onozaty.sample.mapper.RefreshTokenMapper;
import com.github.onozaty.sample.mapper.SessionMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@AppTest
class AuthServiceTest {

  @Autowired private AuthService authService;
  @Autowired private UserService userService;
  @Autowired private SessionMapper sessionMapper;
  @Autowired private RefreshTokenMapper refreshTokenMapper;

  private Long userId;

  @BeforeEach
  void setUp() {
    var input = new UserCreateInput();
    input.setName("Test User");
    input.setEmail("testuser@example.com");
    input.setPassword("password123");
    userId = userService.create(input).getId();
  }

  @Test
  void testCreateSession() {
    // Act
    String sessionId = authService.createSession(userId);

    // Assert
    assertThat(sessionId).isNotNull();
    var session = sessionMapper.findById(sessionId).orElseThrow();
    assertThat(session.getUserId()).isEqualTo(userId);
  }

  @Test
  void testIssueRefreshToken() {
    // Arrange
    String sessionId = authService.createSession(userId);

    // Act
    String token = authService.issueRefreshToken(sessionId);

    // Assert — 平文トークンが返る
    assertThat(token).isNotNull().isNotEmpty();
    // DBにはハッシュが保存されている（平文でlookupすると見つからない）
    assertThat(refreshTokenMapper.findByTokenHash(token)).isEmpty();
  }

  @Test
  void testValidateAndRotateRefreshToken() {
    // Arrange
    String sessionId = authService.createSession(userId);
    String token = authService.issueRefreshToken(sessionId);

    // Act
    String returnedSessionId = authService.validateAndRotateRefreshToken(token);

    // Assert
    assertThat(returnedSessionId).isEqualTo(sessionId);
    // ローテーション後は古いトークンが無効化されている
    assertThatThrownBy(() -> authService.validateAndRotateRefreshToken(token))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void testValidateAndRotateRefreshTokenWithInvalidToken() {
    // Act & Assert
    assertThatThrownBy(() -> authService.validateAndRotateRefreshToken("invalid-token"))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void testValidateAndRotateExpiredRefreshToken() {
    // Arrange — 有効期限切れのトークンを直接DBに挿入
    String sessionId = authService.createSession(userId);
    String expiredToken = "expired-token-plain";
    String expiredTokenHash = sha256(expiredToken);
    refreshTokenMapper.insert(sessionId, expiredTokenHash, OffsetDateTime.now().minusSeconds(1));

    // Act & Assert
    assertThatThrownBy(() -> authService.validateAndRotateRefreshToken(expiredToken))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void testRevokeSessionRevokesOnlyThatSession() {
    // Arrange — 同一ユーザーで 2 セッション作成
    String sessionA = authService.createSession(userId);
    String sessionB = authService.createSession(userId);
    String tokenA = authService.issueRefreshToken(sessionA);
    String tokenB = authService.issueRefreshToken(sessionB);

    // Act — A だけ revoke
    authService.revokeSession(sessionA);

    // Assert — A は使えない、B は使える
    assertThatThrownBy(() -> authService.validateAndRotateRefreshToken(tokenA))
        .isInstanceOf(InvalidRefreshTokenException.class);
    assertThat(authService.validateAndRotateRefreshToken(tokenB)).isEqualTo(sessionB);
  }

  @Test
  void testChangePasswordRevokesOtherSessions() {
    // Arrange — 同一ユーザーで 2 セッション (current と other)
    String currentSession = authService.createSession(userId);
    String otherSession = authService.createSession(userId);
    String currentToken = authService.issueRefreshToken(currentSession);
    String otherToken = authService.issueRefreshToken(otherSession);

    // Act
    authService.changePassword(userId, currentSession, "password123", "newpassword");

    // Assert — 現在のセッションは生きている、他は失効
    assertThat(authService.validateAndRotateRefreshToken(currentToken)).isEqualTo(currentSession);
    assertThatThrownBy(() -> authService.validateAndRotateRefreshToken(otherToken))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void testChangePasswordDoesNotAffectOtherUsers() {
    // Arrange
    var otherInput = new UserCreateInput();
    otherInput.setName("Other User");
    otherInput.setEmail("other@example.com");
    otherInput.setPassword("password123");
    Long otherUserId = userService.create(otherInput).getId();

    String currentSession = authService.createSession(userId);
    String otherUserSession = authService.createSession(otherUserId);
    String otherUserToken = authService.issueRefreshToken(otherUserSession);

    // Act
    authService.changePassword(userId, currentSession, "password123", "newpassword");

    // Assert — 他ユーザーのセッションは影響を受けない
    assertThat(authService.validateAndRotateRefreshToken(otherUserToken))
        .isEqualTo(otherUserSession);
  }

  @Test
  void testCleanupExpiredSessionsRemovesIdleSessions() {
    // Arrange — RT が期限切れのセッション 1 つと、有効なセッション 1 つ
    String expiredSession = authService.createSession(userId);
    refreshTokenMapper.insert(
        expiredSession, sha256("expired-rt"), OffsetDateTime.now().minusDays(1));

    String activeSession = authService.createSession(userId);
    authService.issueRefreshToken(activeSession);

    // Act
    int deleted = authService.cleanupExpiredSessions();

    // Assert
    assertThat(deleted).isGreaterThanOrEqualTo(1);
    assertThat(sessionMapper.findById(expiredSession)).isEmpty();
    assertThat(sessionMapper.findById(activeSession)).isPresent();
  }

  private static String sha256(String input) {
    try {
      var digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(hash);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
