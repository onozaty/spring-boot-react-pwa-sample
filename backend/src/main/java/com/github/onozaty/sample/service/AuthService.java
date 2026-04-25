package com.github.onozaty.sample.service;

import com.github.onozaty.sample.config.JwtProperties;
import com.github.onozaty.sample.domain.RefreshToken;
import com.github.onozaty.sample.domain.Session;
import com.github.onozaty.sample.mapper.RefreshTokenMapper;
import com.github.onozaty.sample.mapper.SessionMapper;
import com.github.onozaty.sample.mapper.UserCredentialMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

  private final UserCredentialMapper credentialMapper;
  private final SessionMapper sessionMapper;
  private final RefreshTokenMapper refreshTokenMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtProperties jwtProperties;

  public AuthService(
      UserCredentialMapper credentialMapper,
      SessionMapper sessionMapper,
      RefreshTokenMapper refreshTokenMapper,
      PasswordEncoder passwordEncoder,
      JwtProperties jwtProperties) {
    this.credentialMapper = credentialMapper;
    this.sessionMapper = sessionMapper;
    this.refreshTokenMapper = refreshTokenMapper;
    this.passwordEncoder = passwordEncoder;
    this.jwtProperties = jwtProperties;
  }

  public void changePassword(
      Long userId, String currentSessionId, String currentPassword, String newPassword) {
    String currentHash =
        credentialMapper
            .findPasswordHashByUserId(userId)
            .orElseThrow(
                () -> new AuthenticationCredentialsNotFoundException("Credential not found"));

    if (!passwordEncoder.matches(currentPassword, currentHash)) {
      throw new InvalidCurrentPasswordException();
    }

    credentialMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
    // 現在のセッションは維持し、それ以外のセッションを失効させる。
    // refresh_tokens は CASCADE で連動して削除される。
    sessionMapper.deleteByUserIdExcept(userId, currentSessionId);
  }

  @Transactional(readOnly = true)
  public Optional<Session> findSession(String sessionId) {
    return sessionMapper.findById(sessionId);
  }

  public String createSession(Long userId) {
    String sessionId = UUID.randomUUID().toString();
    sessionMapper.insert(sessionId, userId);
    return sessionId;
  }

  public String issueRefreshToken(String sessionId) {
    String plainToken = UUID.randomUUID().toString();
    String tokenHash = sha256(plainToken);

    OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(jwtProperties.refreshExpirationDays());
    refreshTokenMapper.insert(sessionId, tokenHash, expiresAt);

    return plainToken;
  }

  public String validateAndRotateRefreshToken(String plainToken) {
    String tokenHash = sha256(plainToken);

    RefreshToken refreshToken =
        refreshTokenMapper
            .findByTokenHash(tokenHash)
            .orElseThrow(() -> new InvalidRefreshTokenException());

    if (refreshToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
      refreshTokenMapper.deleteByTokenHash(tokenHash);
      throw new InvalidRefreshTokenException();
    }

    // 並行リクエストで同じ RT を 2 つのトランザクションが拾った場合、
    // 後発の DELETE は 0 行になる。戻り値で確認することで二重ローテーションを防ぐ。
    int deleted = refreshTokenMapper.deleteByTokenHash(tokenHash);
    if (deleted == 0) {
      throw new InvalidRefreshTokenException();
    }

    sessionMapper.touch(refreshToken.getSessionId());
    return refreshToken.getSessionId();
  }

  public void revokeSession(String sessionId) {
    sessionMapper.deleteById(sessionId);
  }

  public int cleanupExpiredSessions() {
    return sessionMapper.deleteExpired();
  }

  private static String sha256(String input) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
